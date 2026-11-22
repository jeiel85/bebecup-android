package com.bebecup.app.update

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bebecup.app.BuildConfig

/**
 * GitHub (standalone) build: no store installs updates, so the app checks the
 * latest GitHub Release itself and, when the parent taps, downloads and hands
 * the verified APK to Android's installer ([GithubUpdates]).
 */
object UpdateChannel {

    /** Shown in 설정 ("… 배포본"). */
    const val LABEL = "GitHub"

    private const val EXTRA_DEBUG_FAKE_UPDATE = "debug_fake_update"

    /**
     * Debug builds only: `adb shell am start -n com.bebecup.app/.MainActivity --ei debug_fake_update 99`
     * pretends versionCode 99 is on GitHub, to see the banner and 설정 card (nothing is downloaded).
     */
    fun handleDebugLaunch(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getIntExtra(EXTRA_DEBUG_FAKE_UPDATE, 0).takeIf { it > 0 }?.let(GithubUpdates::useFakeUpdate)
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
            title = (status as? UpdateStatus.Available)?.versionName?.let { "새 버전 ${it}이 나왔어요" } ?: "새 버전이 나왔어요",
            subtitle = when (status) {
                is UpdateStatus.Downloading -> "받는 중이에요"
                is UpdateStatus.ReadyToInstall -> "다 받았어요. 눌러서 설치하세요"
                is UpdateStatus.DownloadFailed -> "받지 못했어요. 눌러서 다시 받기"
                else -> "눌러서 GitHub에서 받아 설치하세요"
            },
        )
    }

    /** 설정 > 앱 업데이트. */
    @Composable
    fun rememberStatus(): UpdateStatus = GithubUpdates.status.collectAsState().value

    suspend fun checkNow(context: Context) = GithubUpdates.checkNow(context)

    fun startUpdate(context: Context) = GithubUpdates.startUpdate(context)

    /** 설정 caption under the update card. */
    const val SETTINGS_CAPTION =
        "GitHub에서 받은 베베컵이에요. 앱을 열 때 GitHub 릴리스에서 새 버전을 확인하고, 누르면 받아서 " +
            "파일이 맞는지 확인한 뒤 Android 설치 화면을 열어요. 처음 한 번은 '이 출처의 앱 설치'를 허용해야 해요. " +
            "사진·기록은 보내지 않아요."
}
