package com.bebecup.app.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bebecup.app.BuildConfig
import com.bebecup.app.ui.ai.ToggleRow

/**
 * GitHub (standalone) build: no store installs updates, so the app checks the
 * latest GitHub Release itself and, when the parent taps, downloads and hands
 * the verified APK to Android's installer ([GithubUpdates]).
 */
object UpdateChannel {

    /** Shown in 설정 ("… 배포본"). */
    const val LABEL = "GitHub"

    private const val EXTRA_DEBUG_FAKE_UPDATE = "debug_fake_update"

    /** MainActivity.onCreate: track whether the app is on screen and keep the 12-hour check scheduled. */
    fun onAppStart(activity: Activity) {
        AppVisibility.register(activity.application)
        UpdateScheduler.sync(activity.applicationContext)
        handleDebugLocalUpdate(activity)
    }

    /**
     * Debug builds only: `adb shell am start -n com.bebecup.app/.MainActivity --ei debug_fake_update 99`
     * pretends versionCode 99 is on GitHub, to see the banner and 설정 card (nothing is downloaded).
     */
    fun handleDebugLaunch(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getIntExtra(EXTRA_DEBUG_FAKE_UPDATE, 0).takeIf { it > 0 }?.let(GithubUpdates::useFakeUpdate)
    }

    /**
     * Debug builds only: install a local APK through the real install path (no GitHub release needed).
     * `adb shell run-as com.bebecup.app sh -c 'mkdir -p cache/updates && cp /data/local/tmp/b.apk cache/updates/debug.apk'`, then
     * `adb shell am start -n com.bebecup.app/.MainActivity --ei debug_local_update 13 --es debug_local_update_name 0.11.1`
     * (add `--ez debug_local_update_background true` and press Home to try the silent install).
     */
    fun handleDebugLocalUpdate(activity: Activity) {
        if (!BuildConfig.DEBUG) return
        val intent = activity.intent
        val versionCode = intent.getIntExtra("debug_local_update", 0).takeIf { it > 0 } ?: return
        GithubUpdates.useLocalDebugApk(
            activity,
            versionCode,
            intent.getStringExtra("debug_local_update_name") ?: "9.9.9",
            intent.getBooleanExtra("debug_local_update_background", false),
        )
    }

    /** Checks when home comes to the foreground (at most every few hours) and returns the banner, or null. */
    @Composable
    fun rememberBanner(): UpdateBannerState? {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { GithubUpdates.checkIfStale(context) }
        }
        val status by GithubUpdates.status.collectAsState()
        val dismissed by GithubUpdates.dismissed.collectAsState()
        if (!status.offersUpdate) return null
        val versionCode = GithubUpdates.offeredVersionCode?.takeIf { it != dismissed } ?: return null
        return UpdateBannerState(
            versionCode = versionCode,
            onOpen = { GithubUpdates.startUpdate(context) },
            onDismiss = { GithubUpdates.dismiss(context, versionCode) },
            title = status.versionName?.let { "새 버전 ${VersionParticles.subject(it)} 나왔어요" } ?: "새 버전이 나왔어요",
            subtitle = when (status) {
                is UpdateStatus.Downloading -> "받는 중이에요"
                is UpdateStatus.ReadyToInstall -> "다 받았어요. 눌러서 설치하세요"
                is UpdateStatus.Installing -> "설치하는 중이에요"
                is UpdateStatus.DownloadFailed -> "받지 못했어요. 눌러서 다시 받기"
                else -> "눌러서 GitHub에서 받아 설치하세요"
            },
        )
    }

    /** 설정 > 앱 업데이트. */
    @Composable
    fun rememberStatus(): UpdateStatus = GithubUpdates.status.collectAsState().value

    suspend fun checkNow(context: Context) = GithubUpdates.checkNow(context)

    /** 설정 > 앱 업데이트: background check and silent install, as in Homebase and 포너디. */
    @Composable
    fun AutoUpdateOptions() {
        val context = LocalContext.current
        val prefs = remember(context) { AutoUpdatePrefs(context) }
        var backgroundCheck by remember { mutableStateOf(prefs.backgroundCheck) }
        var autoInstall by remember { mutableStateOf(prefs.autoInstall) }
        ToggleRow("앱을 열지 않아도 새 버전 확인", backgroundCheck) {
            prefs.backgroundCheck = it
            backgroundCheck = it
            UpdateScheduler.sync(context)
        }
        val silentPossible = Build.VERSION.SDK_INT >= AutoUpdatePolicy.SILENT_INSTALL_MIN_SDK
        if (backgroundCheck && silentPossible) {
            ToggleRow("앱을 쓰지 않을 때 자동으로 설치", autoInstall) {
                prefs.autoInstall = it
                autoInstall = it
            }
        }
        val notificationsOn = remember(backgroundCheck) { UpdateNotifier.canShow(context) }
        val line = when {
            !backgroundCheck -> "앱을 열 때와 '업데이트 확인'을 누를 때만 확인해요."
            silentPossible && autoInstall ->
                "12시간마다 GitHub를 확인하고, 앱을 쓰지 않는 동안 새 버전을 설치한 뒤 알려 드려요. " +
                    "처음 한 번은 Android가 설치를 확인해 달라고 할 수 있어요."
            else -> "12시간마다 GitHub를 확인하고 새 버전이 나오면 한 번 알려 드려요."
        } + if (backgroundCheck && !notificationsOn) " 알림이 꺼져 있어서 홈 화면 배너로만 알려요." else ""
        Text(
            line,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }

    fun startUpdate(context: Context) = GithubUpdates.startUpdate(context)

    /** 설정 caption under the update card. */
    const val SETTINGS_CAPTION =
        "GitHub에서 받은 베베컵이에요. GitHub 릴리스에서 새 버전을 받아 파일이 맞는지 확인한 뒤 설치해요. " +
            "처음 한 번은 '이 출처의 앱 설치'를 허용해야 해요. 사진·기록은 보내지 않아요."
}
