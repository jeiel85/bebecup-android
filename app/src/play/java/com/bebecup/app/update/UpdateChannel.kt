package com.bebecup.app.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bebecup.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** Play build: the home update banner backed by [StoreUpdates]. */
object UpdateChannel {

    /** Shown in 설정 ("… 배포본"). */
    const val LABEL = "Google Play"

    private const val EXTRA_DEBUG_FAKE_UPDATE = "debug_fake_update"

    /** MainActivity.onCreate. Play installs updates itself, so there is nothing to schedule. */
    @Suppress("UNUSED_PARAMETER")
    fun onAppStart(activity: Activity) = Unit

    /** The last answer to 설정 > 업데이트 확인 in this process; home's background checks feed [StoreUpdates.available]. */
    private val manual = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)

    /**
     * Debug builds only: `adb shell am start -n com.bebecup.app/.MainActivity --ei debug_fake_update 99`
     * pretends Play offers versionCode 99, to see the home update banner.
     */
    fun handleDebugLaunch(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getIntExtra(EXTRA_DEBUG_FAKE_UPDATE, 0).takeIf { it > 0 }?.let(StoreUpdates::useFakeUpdate)
    }

    /**
     * Asks Play whenever the caller comes to the foreground, at most every few
     * hours, and returns the banner to show, or null. Never a dialog, because
     * updating is not why the user opened the app.
     */
    @Composable
    fun rememberBanner(): UpdateBannerState? {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        remember(context) { StoreUpdates.restore(context.applicationContext) }
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { StoreUpdates.checkIfStale(context) }
        }
        val available by StoreUpdates.available.collectAsState()
        val dismissed by StoreUpdates.dismissed.collectAsState()
        val versionCode = StoreUpdateDecision.bannerVersionCode(available, dismissed) ?: return null
        return UpdateBannerState(
            versionCode = versionCode,
            onOpen = { StoreUpdates.openStore(context) },
            onDismiss = { StoreUpdates.dismiss(context, versionCode) },
        )
    }

    /** 설정 > 앱 업데이트: what Play said last, by a manual check or home's background check. */
    @Composable
    fun rememberStatus(): UpdateStatus {
        val context = LocalContext.current
        remember(context) { StoreUpdates.restore(context.applicationContext) }
        val checked by manual.collectAsState()
        val available by StoreUpdates.available.collectAsState()
        return when {
            checked == UpdateStatus.Checking -> checked
            available != null -> UpdateStatus.Available(versionName = null, versionCode = available!!)
            else -> checked
        }
    }

    suspend fun checkNow(context: Context) {
        val before = manual.value
        manual.value = UpdateStatus.Checking
        val answer = try {
            StoreUpdates.checkNow(context)
        } catch (e: CancellationException) {
            // 설정 closed (or rotated) mid-check: don't leave the button stuck on "Checking".
            if (manual.value == UpdateStatus.Checking) manual.value = before
            throw e
        }
        manual.value = when {
            answer == null -> UpdateStatus.CheckFailed(
                "Google Play에서 확인하지 못했어요. Play 스토어에서 설치한 앱만 여기서 확인할 수 있어요."
            )
            answer > 0 -> UpdateStatus.Available(versionName = null, versionCode = answer)
            else -> UpdateStatus.UpToDate
        }
    }

    /** 설정 > 앱 업데이트 options: none, Play decides when to update. */
    @Composable
    fun AutoUpdateOptions() = Unit

    /** "Play 스토어에서 업데이트": Play does the installing. */
    fun startUpdate(context: Context) = StoreUpdates.openStore(context)

    /** 설정 caption under the update card. */
    const val SETTINGS_CAPTION = "Google Play로 받은 베베컵이에요. 업데이트는 Play 스토어가 설치해요."
}
