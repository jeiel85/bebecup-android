package com.bebecup.app.domain.nudge

/**
 * When to nudge a parent about photos they haven't sorted yet. Pure so the
 * thresholds are unit-tested; the worker and the home screen both use it.
 *
 * "확인 안 한 사진" = gallery photos added after the last time the parent saved
 * a curation round. Before the first round we look back [FIRST_RUN_LOOKBACK_DAYS].
 */
object NudgePolicy {

    /** Notify once at least this many unsorted photos have piled up. */
    const val UNSORTED_THRESHOLD = 20

    /** Never notify more often than this, even if photos keep piling up. */
    const val MIN_INTERVAL_DAYS = 3L

    const val FIRST_RUN_LOOKBACK_DAYS = 14L

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /** Start of the "unsorted" window: the last saved round, or a look-back on first run. */
    fun unsortedWindowStart(lastReviewedAtMillis: Long, nowMillis: Long): Long =
        if (lastReviewedAtMillis > 0L) lastReviewedAtMillis
        else nowMillis - FIRST_RUN_LOOKBACK_DAYS * DAY_MILLIS

    fun shouldNudge(
        enabled: Boolean,
        unsortedCount: Int,
        lastNudgeAtMillis: Long,
        nowMillis: Long
    ): Boolean {
        if (!enabled) return false
        if (unsortedCount < UNSORTED_THRESHOLD) return false
        if (lastNudgeAtMillis <= 0L) return true
        return nowMillis - lastNudgeAtMillis >= MIN_INTERVAL_DAYS * DAY_MILLIS
    }

    /** Whole days since the last saved round, or null before the first one. */
    fun daysSinceReview(lastReviewedAtMillis: Long, nowMillis: Long): Long? =
        if (lastReviewedAtMillis <= 0L) null
        else ((nowMillis - lastReviewedAtMillis) / DAY_MILLIS).coerceAtLeast(0L)
}
