package com.bebecup.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Small persisted settings and bookkeeping shared by the UI and the nudge
 * worker. Plain SharedPreferences: a handful of scalars, read synchronously.
 */
class BebecupPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 0 = "지난 정리 이후" (since the last saved round); otherwise a day count. */
    var scanRangeDays: Int
        get() = prefs.getInt(KEY_SCAN_RANGE, SCAN_SINCE_LAST_REVIEW)
        set(value) = prefs.edit().putInt(KEY_SCAN_RANGE, value).apply()

    var sleepingModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SLEEPING, false)
        set(value) = prefs.edit().putBoolean(KEY_SLEEPING, value).apply()

    var similarOnly: Boolean
        get() = prefs.getBoolean(KEY_SIMILAR_ONLY, true)
        set(value) = prefs.edit().putBoolean(KEY_SIMILAR_ONLY, value).apply()

    var excludeBlurry: Boolean
        get() = prefs.getBoolean(KEY_EXCLUDE_BLURRY, true)
        set(value) = prefs.edit().putBoolean(KEY_EXCLUDE_BLURRY, value).apply()

    var onboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, value).apply()

    /** Parent opted in to "사진이 쌓이면 알려주기". Off until they say yes. */
    var nudgeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUDGE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NUDGE_ENABLED, value).apply()

    /** End of the scan window of the last round the parent saved; 0 = never. */
    var lastReviewedAtMillis: Long
        get() = prefs.getLong(KEY_LAST_REVIEWED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REVIEWED, value).apply()

    var lastNudgeAtMillis: Long
        get() = prefs.getLong(KEY_LAST_NUDGE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NUDGE, value).apply()

    companion object {
        const val SCAN_SINCE_LAST_REVIEW = 0

        private const val FILE = "bebecup_prefs"
        // Keys predating v0.7 keep their names so existing settings survive.
        private const val KEY_SCAN_RANGE = "ai_scan_range_days_v2"
        private const val KEY_SLEEPING = "ai_sleeping_mode"
        private const val KEY_SIMILAR_ONLY = "ai_similar_only"
        private const val KEY_EXCLUDE_BLURRY = "ai_exclude_blurry"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        private const val KEY_NUDGE_ENABLED = "nudge_enabled"
        private const val KEY_LAST_REVIEWED = "last_reviewed_at"
        private const val KEY_LAST_NUDGE = "last_nudge_at"
    }
}
