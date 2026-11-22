package com.bebecup.app.update

import java.util.concurrent.TimeUnit

/**
 * What 설정 > 앱 업데이트 knows about a newer version. Each channel flavor's
 * `UpdateChannel` (src/github, src/play) produces it:
 *
 * - GitHub build: reads `update.json` from the latest GitHub Release, downloads
 *   the APK, checks its SHA-256 and hands it to Android's installer.
 * - Play build: asks the Google Play app; Play does the installing.
 */
sealed interface UpdateStatus {
    /** Not asked yet in this process. */
    data object Unknown : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    /** The last check could not get an answer (offline, not from Play, ...). */
    data class CheckFailed(val reason: String) : UpdateStatus
    data class Available(val versionName: String?, val versionCode: Int) : UpdateStatus
    /** GitHub build only. [progress] is 0..1, or null while the size is unknown. */
    data class Downloading(val versionName: String?, val progress: Float?) : UpdateStatus
    /** GitHub build only: downloaded and verified; waiting for "설치" (e.g. after allowing unknown sources). */
    data class ReadyToInstall(val versionName: String?) : UpdateStatus
    data class DownloadFailed(val versionName: String?, val reason: String) : UpdateStatus
}

/** True while there is something newer the home banner should mention. */
val UpdateStatus.offersUpdate: Boolean
    get() = this is UpdateStatus.Available || this is UpdateStatus.Downloading ||
        this is UpdateStatus.ReadyToInstall || this is UpdateStatus.DownloadFailed

/** The version on offer, when this state knows it (the Play build never does). */
val UpdateStatus.versionName: String?
    get() = when (this) {
        is UpdateStatus.Available -> versionName
        is UpdateStatus.Downloading -> versionName
        is UpdateStatus.ReadyToInstall -> versionName
        is UpdateStatus.DownloadFailed -> versionName
        else -> null
    }

/** When the GitHub build re-checks, free of Android calls so tests can pin it. */
object UpdateCheckPolicy {

    /** Coming back to the app re-checks only after this long; "업데이트 확인" in 설정 always checks. */
    val RECHECK_MILLIS: Long = TimeUnit.HOURS.toMillis(6)

    fun isStale(lastCheckedAt: Long, now: Long): Boolean =
        lastCheckedAt <= 0L || lastCheckedAt > now || now - lastCheckedAt >= RECHECK_MILLIS
}
