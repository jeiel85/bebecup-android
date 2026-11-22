package com.bebecup.app.update

/**
 * A home banner saying a newer version is out, from the flavor's
 * [UpdateChannel]: the Play build opens the Play listing, the GitHub build
 * downloads and installs the APK itself.
 */
data class UpdateBannerState(
    val versionCode: Int,
    val onOpen: () -> Unit,
    /** Hide the banner for this [versionCode]; a later version shows it again. */
    val onDismiss: () -> Unit,
    val title: String = "새 버전이 나왔어요",
    val subtitle: String = "눌러서 Play 스토어에서 업데이트하세요",
)
