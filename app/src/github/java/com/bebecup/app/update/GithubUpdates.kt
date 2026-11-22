package com.bebecup.app.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.bebecup.app.BuildConfig
import com.bebecup.app.data.prefs.BebecupPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection

/**
 * GitHub build: updates itself from GitHub Releases the way the other sideloaded
 * apps do (Homebase, 포너디, Markleaf).
 *
 * 1. Check: GET `releases/latest/download/update.json` ([GithubUpdateManifest]) when home
 *    opens (at most every few hours), from 설정, and every 12 hours in the background
 *    ([UpdateWorker]). The request carries only the URL and standard headers — no photo, no id.
 * 2. Download the APK into the app cache and check its size and SHA-256 against the manifest,
 *    and that it is this app at that versionCode. Only installs signed with the GitHub key
 *    ([BuildConfig.UPDATE_SIGNER_SHA256]) are offered an in-place update; Android refuses others.
 * 3. Install through a [PackageInstaller] session. After the first update BebeCup installed
 *    itself, Android 12+ lets later ones go through without asking, so the background check
 *    installs while the app is not in use and says so afterwards ([AutoUpdatePolicy]).
 *    Otherwise Android shows its confirmation; in the background that becomes a notification.
 */
object GithubUpdates {

    const val ACTION_INSTALL_STATUS = "com.bebecup.app.update.INSTALL_STATUS"
    const val EXTRA_VERSION_CODE = "versionCode"
    const val EXTRA_SILENT = "silent"

    private const val TAG = "GithubUpdates"
    private const val TIMEOUT_MILLIS = 15_000
    private const val MAX_MANIFEST_BYTES = 16 * 1024
    private const val UPDATE_DIR = "updates"

    private const val SIGNER_MISMATCH =
        "이 베베컵은 GitHub 릴리스와 다른 키로 서명돼 있어서 그 위에 업데이트할 수 없어요. " +
            "앱을 지우고 GitHub 릴리스 페이지에서 새 APK를 받아 설치해 주세요(앱 데이터는 지워져요)."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _dismissed = MutableStateFlow(0)

    /** The versionCode whose home banner the parent closed, or 0 (kept across restarts). */
    val dismissed: StateFlow<Int> = _dismissed.asStateFlow()

    @Volatile private var lastCheckedAt = 0L
    @Volatile private var restored = false
    @Volatile private var offered: GithubUpdateManifest? = null

    /** The versionCode on offer while [status] offers an update, else null. */
    val offeredVersionCode: Int? get() = offered?.versionCode
    @Volatile private var downloadJob: Job? = null
    @Volatile private var readyFile: File? = null
    private val downloading = AtomicBoolean(false)

    /** Android's confirmation for a session the parent started, kept so the banner can reopen it. */
    @Volatile private var pendingConfirm: Intent? = null
    @Volatile private var signerMatchesCache: Boolean? = null

    /** Debug builds only ([useFakeUpdate]): pretend this versionCode is out, without network. */
    @Volatile private var fakeVersionCode = 0

    /** Home: re-checks when the last answer is older than [UpdateCheckPolicy.RECHECK_MILLIS]. */
    suspend fun checkIfStale(context: Context) {
        restore(context)
        if (!UpdateCheckPolicy.isStale(lastCheckedAt, System.currentTimeMillis())) return
        check(context, userAsked = false)
    }

    /** 설정 > 업데이트 확인: always checks. */
    suspend fun checkNow(context: Context) = check(context, userAsked = true)

    private fun restore(context: Context) {
        if (restored) return
        restored = true
        _dismissed.value = BebecupPrefs(context.applicationContext).updateDismissedVersionCode
    }

    /** Hide the home banner for [versionCode]; a later version shows it again. 설정 still offers it. */
    fun dismiss(context: Context, versionCode: Int) {
        BebecupPrefs(context.applicationContext).updateDismissedVersionCode = versionCode
        _dismissed.value = versionCode
    }

    private suspend fun check(context: Context, userAsked: Boolean) {
        if (busy()) return
        val before = _status.value
        if (userAsked) _status.value = UpdateStatus.Checking
        val manifest = try {
            withContext(Dispatchers.IO) { fetchManifest() }
        } catch (e: CancellationException) {
            // The screen that asked went away (설정 closed, rotation): don't leave
            // "Checking" behind, or busy() would block every later check.
            if (_status.value == UpdateStatus.Checking) _status.value = before
            throw e
        }
        if (manifest == null) {
            _status.value = if (userAsked) {
                UpdateStatus.CheckFailed("GitHub에서 새 버전을 확인하지 못했어요. 인터넷 연결을 확인해 주세요.")
            } else before
            return
        }
        accept(context, manifest)
    }

    /** Records the answer to a check; true when [manifest] is newer than this install. */
    private suspend fun accept(context: Context, manifest: GithubUpdateManifest): Boolean {
        lastCheckedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) { cleanLeftovers(context) }
        return if (manifest.versionCode > BuildConfig.VERSION_CODE) {
            offered = manifest
            _status.value = UpdateStatus.Available(manifest.versionName, manifest.versionCode)
            true
        } else {
            offered = null
            _status.value = UpdateStatus.UpToDate
            false
        }
    }

    /**
     * [UpdateWorker]: check, then install without asking when [AutoUpdatePolicy] allows it,
     * otherwise post one notification for the version. [isStopped] = WorkManager stopped the work.
     */
    suspend fun backgroundCheck(context: Context, isStopped: () -> Boolean) {
        val appContext = context.applicationContext
        restore(appContext)
        if (busy()) return
        val manifest = withContext(Dispatchers.IO) { fetchManifest() } ?: return
        if (!accept(appContext, manifest)) return
        val prefs = AutoUpdatePrefs(appContext)
        val trySilent = !isStopped() && AutoUpdatePolicy.shouldInstallSilently(
            autoInstall = prefs.autoInstall,
            deviceSdk = Build.VERSION.SDK_INT,
            appVisible = AppVisibility.visible,
            signerMatches = signerMatches(appContext),
            canRequestInstalls = canRequestInstalls(appContext),
            offeredVersionCode = manifest.versionCode,
            needsConfirmVersionCode = prefs.needsConfirmVersionCode,
            failedVersionCode = prefs.failedVersionCode,
            failedAttempts = prefs.failedAttempts,
        )
        val file = if (trySilent) downloadAndVerify(appContext, manifest) ?: run {
            prefs.addSilentFailure(manifest.versionCode)
            null
        } else null
        installInBackground(appContext, manifest, file, isStopped)
    }

    /** Commits [file] without a confirmation if still allowed; otherwise (or with no file) notifies once. */
    private fun installInBackground(
        appContext: Context,
        manifest: GithubUpdateManifest,
        file: File?,
        isStopped: () -> Boolean,
    ) {
        val prefs = AutoUpdatePrefs(appContext)
        if (file != null) {
            when {
                // The parent opened the app, turned 자동 설치 off, or the work was stopped while downloading.
                isStopped() || AppVisibility.visible || !prefs.autoInstall -> Unit
                commitSession(appContext, file, manifest.versionCode, manifest.versionName, silent = true) -> return
                else -> prefs.addSilentFailure(manifest.versionCode)
            }
        }
        if (!isStopped()) notifyIfDue(appContext, manifest.versionCode, manifest.versionName)
    }

    /** Checking, downloading, installing, or a verified APK waiting for "설치": a new check would only get in the way. */
    private fun busy(): Boolean = _status.value is UpdateStatus.Checking || downloading.get() ||
        downloadJob?.isActive == true || _status.value is UpdateStatus.Installing ||
        (_status.value is UpdateStatus.ReadyToInstall && readyFile?.exists() == true)

    /**
     * "업데이트 받기" / "설치": downloads and verifies the offered APK, then installs it.
     * Survives leaving the screen; progress is in [status].
     */
    fun startUpdate(context: Context) {
        val appContext = context.applicationContext
        when (val current = _status.value) {
            is UpdateStatus.Installing -> {
                // Android's confirmation was hidden (or never shown): show it again from here.
                pendingConfirm?.let { confirm ->
                    try {
                        context.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        Log.w(TAG, "installer confirmation is gone", e)
                    }
                }
                return
            }
            is UpdateStatus.ReadyToInstall -> if (readyFile?.exists() == true) {
                installByUser(context)
                return
            }
            else -> Unit
        }
        val manifest = offered ?: return
        if (!signerMatches(appContext)) {
            _status.value = UpdateStatus.DownloadFailed(manifest.versionName, SIGNER_MISMATCH)
            return
        }
        if (downloadJob?.isActive == true || downloading.get()) return
        downloadJob = scope.launch {
            downloadAndVerify(appContext, manifest) ?: return@launch
            withContext(Dispatchers.Main) { installByUser(appContext) }
        }
    }

    /**
     * Downloads [manifest]'s APK and checks it; the verified file, or null with the reason in
     * [status]. One download at a time across the parent's tap and the background check.
     */
    private suspend fun downloadAndVerify(context: Context, manifest: GithubUpdateManifest): File? {
        if (!downloading.compareAndSet(false, true)) return null
        try {
            _status.value = UpdateStatus.Downloading(manifest.versionName, null)
            val file = try {
                download(context, manifest)
            } catch (e: CancellationException) {
                _status.value = UpdateStatus.Available(manifest.versionName, manifest.versionCode)
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "update download failed", e)
                _status.value = UpdateStatus.DownloadFailed(
                    manifest.versionName,
                    if (e is IOException && e.message?.startsWith("verify:") == true) {
                        "받은 파일이 GitHub에 올라간 파일과 달라서 설치하지 않았어요. 다시 시도해 주세요."
                    } else {
                        "새 버전을 받지 못했어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
                    }
                )
                return null
            }
            val problem = withContext(Dispatchers.IO) { archiveProblem(context, file, manifest) }
            if (problem != null) {
                Log.w(TAG, "downloaded APK rejected: $problem")
                file.delete()
                _status.value = UpdateStatus.DownloadFailed(manifest.versionName, problem)
                return null
            }
            readyFile = file
            _status.value = UpdateStatus.ReadyToInstall(manifest.versionName)
            return file
        } finally {
            downloading.set(false)
        }
    }

    /**
     * Android asks the parent once to allow "이 출처의 앱 설치" for BebeCup. Until that is on
     * we open that settings page; coming back, "설치" continues.
     */
    private fun installByUser(context: Context) {
        val appContext = context.applicationContext
        val file = readyFile?.takeIf { it.exists() } ?: run {
            _status.value = offered?.let { UpdateStatus.Available(it.versionName, it.versionCode) } ?: UpdateStatus.Unknown
            return
        }
        val manifest = offered ?: return
        if (!canRequestInstalls(appContext)) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "no unknown-sources settings page; trying the installer anyway", e)
            }
        }
        scope.launch { commitSession(appContext, file, manifest.versionCode, manifest.versionName, silent = false) }
    }

    /**
     * Copies [apk] into an install session and commits it; the outcome arrives in
     * [UpdateInstallReceiver] → [onInstallStatus]. False if the session could not start.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    private fun commitSession(context: Context, apk: File, versionCode: Int, versionName: String, silent: Boolean): Boolean {
        val installer = context.packageManager.packageInstaller
        // A session an earlier attempt left behind (e.g. a confirmation never answered).
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Without a confirmation when Android allows it (BebeCup installed the current version itself).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            // Another installer then has to ask before replacing BebeCup.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) setRequestUpdateOwnership(true)
        }
        var sessionId = -1
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                AutoUpdatePrefs(context).installingVersionCode = versionCode
                _status.value = UpdateStatus.Installing(versionName)
                val result = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, UpdateInstallReceiver::class.java)
                        .setAction(ACTION_INSTALL_STATUS)
                        .putExtra(EXTRA_VERSION_CODE, versionCode)
                        .putExtra(EXTRA_SILENT, silent),
                    // The installer fills in the result extras, so it must be mutable (explicit intent).
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                )
                session.commit(result.intentSender)
            }
            Log.i(TAG, "committed install of $versionCode (silent=$silent)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "install session failed to start", e)
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
            AutoUpdatePrefs(context).installingVersionCode = 0
            _status.value = if (silent) {
                UpdateStatus.ReadyToInstall(versionName)
            } else {
                UpdateStatus.DownloadFailed(versionName, "설치를 시작하지 못했어요. 다시 시도해 주세요.")
            }
            false
        }
    }

    /** [UpdateInstallReceiver]: how a session ended. Success needs nothing — Android replaces the app. */
    internal fun onInstallStatus(
        context: Context,
        versionCode: Int,
        silent: Boolean,
        status: Int,
        sessionId: Int,
        confirm: Intent?,
        message: String?,
    ) {
        val appContext = context.applicationContext
        val prefs = AutoUpdatePrefs(appContext)
        val versionName = offered?.takeIf { it.versionCode == versionCode }?.versionName
        val waiting: UpdateStatus = if (readyFile?.exists() == true) {
            UpdateStatus.ReadyToInstall(versionName)
        } else {
            UpdateStatus.Available(versionName, versionCode)
        }
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                if (!silent && confirm != null) {
                    // The parent tapped "설치": show Android's confirmation now.
                    pendingConfirm = confirm
                    try {
                        appContext.startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: ActivityNotFoundException) {
                        Log.w(TAG, "no installer confirmation screen", e)
                        prefs.installingVersionCode = 0
                        _status.value = UpdateStatus.DownloadFailed(versionName, "이 기기에서 설치 화면을 열지 못했어요.")
                    }
                    return
                }
                // In the background Android wants a confirmation (the first update after installing from
                // a browser, or a device that never allows it): drop the session and tell the parent.
                if (sessionId >= 0) runCatching { appContext.packageManager.packageInstaller.abandonSession(sessionId) }
                prefs.installingVersionCode = 0
                prefs.needsConfirmVersionCode = versionCode
                _status.value = waiting
                notifyIfDue(appContext, versionCode, versionName)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // The parent backed out of Android's confirmation. "설치" tries again.
                pendingConfirm = null
                prefs.installingVersionCode = 0
                _status.value = waiting
            }
            else -> {
                Log.w(TAG, "install of $versionCode failed: $status $message")
                pendingConfirm = null
                prefs.installingVersionCode = 0
                if (silent) {
                    prefs.addSilentFailure(versionCode)
                    _status.value = waiting
                    notifyIfDue(appContext, versionCode, versionName)
                } else {
                    _status.value = UpdateStatus.DownloadFailed(versionName, installStatusText(status))
                }
            }
        }
    }

    /** First start of the new version: clean up, and say what happened if BebeCup installed it. */
    internal fun onReplaced(context: Context) {
        val appContext = context.applicationContext
        val prefs = AutoUpdatePrefs(appContext)
        val installed = prefs.installingVersionCode
        if (installed != 0) prefs.installingVersionCode = 0
        File(appContext.cacheDir, UPDATE_DIR).listFiles()?.forEach { it.delete() }
        if (installed == BuildConfig.VERSION_CODE) {
            UpdateNotifier.showUpdated(appContext, BuildConfig.VERSION_NAME)
        } else {
            // Installed some other way: a leftover "새 버전이 나왔어요" is no longer true.
            UpdateNotifier.cancel(appContext)
        }
    }

    private fun notifyIfDue(context: Context, versionCode: Int, versionName: String?) {
        val prefs = AutoUpdatePrefs(context)
        if (!AutoUpdatePolicy.shouldNotify(prefs.backgroundCheck, versionCode, BuildConfig.VERSION_CODE, prefs.notifiedVersionCode)) return
        // Not remembered when it could not be shown, so allowing notifications later still brings it.
        if (UpdateNotifier.showAvailable(context, versionName)) prefs.notifiedVersionCode = versionCode
    }

    private fun canRequestInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Is this install signed with the GitHub key? Unknown (unreadable) counts as yes: Android has the last word. */
    private fun signerMatches(context: Context): Boolean = signerMatchesCache ?: run {
        val mine = try {
            installedSignerSha256(context)
        } catch (e: Exception) {
            Log.w(TAG, "could not read this app's signer", e)
            emptyList()
        }
        (mine.isEmpty() || BuildConfig.UPDATE_SIGNER_SHA256 in mine).also { signerMatchesCache = it }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("PackageManagerGetSignatures")
    private fun installedSignerSha256(context: Context): List<String> {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return emptyList()
            // A rotated key keeps its history; the release key anywhere in it means updates install.
            (if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory)
                ?.toList().orEmpty()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
        }
        return signatures.map { sha256Hex(it.toByteArray()) }
    }

    /** Null when [apk] is BebeCup at exactly the manifest's versionCode; otherwise what is wrong, for 설정. */
    private fun archiveProblem(context: Context, apk: File, manifest: GithubUpdateManifest): String? {
        val info: PackageInfo = context.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: return "받은 파일이 올바른 APK가 아니라서 설치하지 않았어요."
        if (info.packageName != context.packageName) return "받은 파일이 베베컵이 아니라서 설치하지 않았어요."
        if (PackageInfoCompat.getLongVersionCode(info) != manifest.versionCode.toLong()) {
            return "받은 파일의 버전이 GitHub에 적힌 버전과 달라서 설치하지 않았어요."
        }
        return null
    }

    /** Debug builds only: pretend [versionCode] is out (no download possible). Lasts for this process. */
    fun useFakeUpdate(versionCode: Int) {
        if (!BuildConfig.DEBUG) return
        fakeVersionCode = versionCode
        lastCheckedAt = 0L
    }

    /**
     * Debug builds only, to try installing on an emulator without a GitHub release: treats
     * `cache/updates/debug.apk` (copied in with `adb shell run-as`) as the verified download of
     * [versionCode]. [background]: after 15 s (time to press Home) run the background install
     * path as [UpdateWorker] would; otherwise wait for the parent's "설치".
     */
    fun useLocalDebugApk(context: Context, versionCode: Int, versionName: String, background: Boolean) {
        if (!BuildConfig.DEBUG) return
        val appContext = context.applicationContext
        val file = File(File(appContext.cacheDir, UPDATE_DIR), "debug.apk").takeIf { it.exists() } ?: run {
            Log.w(TAG, "debug update: no cache/updates/debug.apk")
            return
        }
        val manifest = GithubUpdateManifest(versionCode, versionName, file.name, "0".repeat(64), file.length())
        offered = manifest
        archiveProblem(appContext, file, manifest)?.let {
            _status.value = UpdateStatus.DownloadFailed(versionName, it)
            return
        }
        readyFile = file
        _status.value = UpdateStatus.ReadyToInstall(versionName)
        if (!background) return
        scope.launch {
            kotlinx.coroutines.delay(15_000)
            installInBackground(appContext, manifest, file) { false }
        }
    }

    private fun fetchManifest(): GithubUpdateManifest? {
        if (BuildConfig.DEBUG && fakeVersionCode > 0) {
            return GithubUpdateManifest(fakeVersionCode, "9.9.9", "bebecup-v9.9.9.apk", "0".repeat(64), 1)
        }
        var connection: HttpURLConnection? = null
        return try {
            connection = open(GithubUpdateManifest.latestUrl(BuildConfig.UPDATE_REPO))
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.use { input ->
                val buffer = ByteArray(MAX_MANIFEST_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val n = input.read(buffer, total, buffer.size - total)
                    if (n < 0) break
                    total += n
                }
                String(buffer, 0, total, Charsets.UTF_8)
            }
            GithubUpdateManifest.parse(body)
        } catch (e: Exception) {
            Log.i(TAG, "update check failed: $e")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun download(context: Context, manifest: GithubUpdateManifest): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        readyFile = null
        val target = File(dir, manifest.apkName)
        val digest = MessageDigest.getInstance("SHA-256")
        var connection: HttpURLConnection? = null
        try {
            connection = open(manifest.apkUrl(BuildConfig.UPDATE_REPO))
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            var received = 0L
            var lastReported = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        received += n
                        if (received > manifest.sizeBytes) throw IOException("verify: larger than announced")
                        digest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        val percent = (received * 100 / manifest.sizeBytes).toInt()
                        if (percent != lastReported) {
                            lastReported = percent
                            _status.value = UpdateStatus.Downloading(manifest.versionName, percent / 100f)
                        }
                    }
                }
            }
            val sha = hex(digest.digest())
            if (received != manifest.sizeBytes || sha != manifest.sha256) throw IOException("verify: size or SHA-256 mismatch")
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            useCaches = false
            instanceFollowRedirects = true // github.com → release-assets host, https only
        }

    private fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** A previous run's APK is of no use once the check has an answer (installed, or superseded). */
    private fun cleanLeftovers(context: Context) {
        if (downloadJob?.isActive == true || downloading.get() || readyFile != null) return
        File(context.cacheDir, UPDATE_DIR).listFiles()?.forEach { it.delete() }
    }
}
