package com.bebecup.app.update

import android.content.Intent
import androidx.compose.runtime.Composable

/**
 * GitHub (standalone) build: no store to ask, so no home update banner. New
 * versions are announced on the GitHub Releases page.
 */
object UpdateChannel {

    @Suppress("UNUSED_PARAMETER")
    fun handleDebugLaunch(intent: Intent) = Unit

    /** Never a banner in this build. */
    @Composable
    fun rememberBanner(): UpdateBannerState? = null
}
