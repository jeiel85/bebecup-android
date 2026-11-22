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
    /** GitHub build only: handed to Android's installer; on success Android replaces the app. */
    data class Installing(val versionName: String?) : UpdateStatus
    data class DownloadFailed(val versionName: String?, val reason: String) : UpdateStatus
}

/** True while there is something newer the home banner should mention. */
val UpdateStatus.offersUpdate: Boolean
    get() = this is UpdateStatus.Available || this is UpdateStatus.Downloading ||
        this is UpdateStatus.ReadyToInstall || this is UpdateStatus.Installing ||
        this is UpdateStatus.DownloadFailed

/** The version on offer, when this state knows it (the Play build never does). */
val UpdateStatus.versionName: String?
    get() = when (this) {
        is UpdateStatus.Available -> versionName
        is UpdateStatus.Downloading -> versionName
        is UpdateStatus.ReadyToInstall -> versionName
        is UpdateStatus.Installing -> versionName
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

/**
 * Korean particles after a version number depend on how its last digit is read:
 * 영·일·삼·육·칠·팔 end in a consonant ("0.11.1이", "0.12.2가"), and 로 replaces 으로
 * after a vowel or ㄹ (일·칠·팔): "0.11.1로", "0.11.0으로".
 */
object VersionParticles {
    private fun lastDigit(version: String): Char? = version.lastOrNull()?.takeIf { it.isDigit() }

    /** "0.11.1이" / "0.12.2가". */
    fun subject(version: String): String = version + if (lastDigit(version) in "013678".toList()) "이" else "가"

    /** "0.11.0으로" / "0.11.1로". */
    fun direction(version: String): String = version + if (lastDigit(version) in "036".toList()) "으로" else "로"
}
