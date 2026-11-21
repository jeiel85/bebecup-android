package com.bebecup.app.update

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

/** Play build: the home update banner backed by [StoreUpdates]. */
object UpdateChannel {

    private const val EXTRA_DEBUG_FAKE_UPDATE = "debug_fake_update"

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
}
