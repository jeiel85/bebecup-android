package com.bebecup.app.update

/**
 * A home banner saying a newer version is out. Only the Play build produces
 * one ([UpdateChannel] in the `play` source set); the GitHub build never does.
 */
data class UpdateBannerState(
    val versionCode: Int,
    val onOpen: () -> Unit,
    /** Hide the banner for this [versionCode]; a later version shows it again. */
    val onDismiss: () -> Unit,
)
