package com.bebecup.app.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
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
import javax.net.ssl.HttpsURLConnection

/**
 * GitHub build: updates itself from GitHub Releases, like the other sideloaded
 * apps (Markleaf, Homebase).
 *
 * 1. Check: GET `releases/latest/download/update.json` ([GithubUpdateManifest]).
 *    Automatically when home opens (at most every few hours) and from 설정.
 *    The request carries only the URL and standard headers — no photo, no id.
 * 2. Only when the parent taps "업데이트": download the APK into the app cache,
 *    check its size and SHA-256 against the manifest, then hand it to Android's
 *    own installer ([Intent.ACTION_INSTALL_PACKAGE]), which asks to confirm.
 *    Android itself refuses an APK signed with a different key.
 */
object GithubUpdates {

    private const val TAG = "GithubUpdates"
    private const val TIMEOUT_MILLIS = 15_000
    private const val MAX_MANIFEST_BYTES = 16 * 1024
    private const val UPDATE_DIR = "updates"

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
        lastCheckedAt = System.currentTimeMillis()
        withContext(Dispatchers.IO) { cleanLeftovers(context) }
        if (manifest.versionCode > BuildConfig.VERSION_CODE) {
            offered = manifest
            _status.value = UpdateStatus.Available(manifest.versionName, manifest.versionCode)
        } else {
            offered = null
            _status.value = UpdateStatus.UpToDate
        }
    }

    /** Checking, downloading, or a verified APK waiting for "설치": a new check would only get in the way. */
    private fun busy(): Boolean = _status.value is UpdateStatus.Checking || downloadJob?.isActive == true ||
        (_status.value is UpdateStatus.ReadyToInstall && readyFile?.exists() == true)

    /**
     * "업데이트" / "설치": downloads and verifies the offered APK, then opens the
     * installer. Survives leaving the screen; progress is in [status].
     */
    fun startUpdate(context: Context) {
        val appContext = context.applicationContext
        val current = _status.value
        if (current is UpdateStatus.ReadyToInstall) {
            install(appContext)
            return
        }
        val manifest = offered ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _status.value = UpdateStatus.Downloading(manifest.versionName, null)
            val file = try {
                download(appContext, manifest)
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
                return@launch
            }
            readyFile = file
            _status.value = UpdateStatus.ReadyToInstall(manifest.versionName)
            withContext(Dispatchers.Main) { install(appContext) }
        }
    }

    /**
     * Android asks the parent once to allow "이 출처의 앱 설치" for BebeCup. Until
     * that is on we open that settings page; coming back, "설치" continues.
     */
    private fun install(context: Context) {
        val file = readyFile?.takeIf { it.exists() } ?: run {
            _status.value = offered?.let { UpdateStatus.Available(it.versionName, it.versionCode) } ?: UpdateStatus.Unknown
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "no unknown-sources settings page; trying the installer anyway", e)
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no package installer", e)
            _status.value = UpdateStatus.DownloadFailed(
                offered?.versionName,
                "이 기기에서 설치 화면을 열지 못했어요. GitHub 릴리스 페이지에서 직접 받아 주세요."
            )
        }
    }

    /** Debug builds only: pretend [versionCode] is out (no download possible). Lasts for this process. */
    fun useFakeUpdate(versionCode: Int) {
        if (!BuildConfig.DEBUG) return
        fakeVersionCode = versionCode
        lastCheckedAt = 0L
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
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
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

    /** A previous run's APK is of no use once the check has an answer (installed, or superseded). */
    private fun cleanLeftovers(context: Context) {
        if (downloadJob?.isActive == true || readyFile != null) return
        File(context.cacheDir, UPDATE_DIR).listFiles()?.forEach { it.delete() }
    }
}
