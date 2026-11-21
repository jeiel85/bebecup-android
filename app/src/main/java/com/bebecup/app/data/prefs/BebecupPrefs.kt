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

    /** Parent opted in to reminders (사진이 쌓이면 + 이달의 월드컵). Off until they say yes. */
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

    /** The baby's name as the parent typed it; "" when not given. Stays on this device. */
    var babyName: String
        get() = prefs.getString(KEY_BABY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BABY_NAME, value).apply()

    /** The baby's birthday as "yyyy-MM-dd"; "" when not given. Stays on this device. */
    var babyBirthDate: String
        get() = prefs.getString(KEY_BABY_BIRTH_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BABY_BIRTH_DATE, value).apply()

    /** The parent tapped "다음에" on the home card asking for the birthday. */
    var babyProfilePromptDismissed: Boolean
        get() = prefs.getBoolean(KEY_BABY_PROMPT_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_BABY_PROMPT_DISMISSED, value).apply()

    /**
     * Kept photos (보관함·인화함) saved before the channel split (after v0.9.0) may carry the pick day or
     * the gallery's add day as their date; set once they are re-read from EXIF.
     */
    var keptPhotoDatesFixed: Boolean
        get() = prefs.getBoolean(KEY_KEPT_DATES_FIXED, false)
        set(value) = prefs.edit().putBoolean(KEY_KEPT_DATES_FIXED, value).apply()

    /** Play build: when Play last answered the update check; 0 = never. */
    var updateLastCheckedAtMillis: Long
        get() = prefs.getLong(KEY_UPDATE_LAST_CHECKED, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_LAST_CHECKED, value).apply()

    /** Play build: the newer versionCode Play last offered; 0 = none. */
    var updateOfferedVersionCode: Int
        get() = prefs.getInt(KEY_UPDATE_OFFERED, 0)
        set(value) = prefs.edit().putInt(KEY_UPDATE_OFFERED, value).apply()

    /** Play build: the versionCode whose home banner the parent closed; 0 = none. */
    var updateDismissedVersionCode: Int
        get() = prefs.getInt(KEY_UPDATE_DISMISSED, 0)
        set(value) = prefs.edit().putInt(KEY_UPDATE_DISMISSED, value).apply()

    /** Play build: update checks in a row that got no answer from Play. */
    var updateCheckFailures: Int
        get() = prefs.getInt(KEY_UPDATE_FAILURES, 0)
        set(value) = prefs.edit().putInt(KEY_UPDATE_FAILURES, value).apply()

    /** "yyyy-MM" of the 이달의 월드컵 the parent skipped on the home card; "" = none. */
    var monthlyCupSkippedMonth: String
        get() = prefs.getString(KEY_MONTHLY_SKIPPED, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MONTHLY_SKIPPED, value).apply()

    /** "yyyy-MM" of the last 이달의 월드컵 reminder posted, so each month is announced once. */
    var monthlyCupNotifiedMonth: String
        get() = prefs.getString(KEY_MONTHLY_NOTIFIED, "") ?: ""
        set(value) = prefs.edit().putString(KEY_MONTHLY_NOTIFIED, value).apply()

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
        private const val KEY_BABY_NAME = "baby_name"
        private const val KEY_BABY_BIRTH_DATE = "baby_birth_date"
        private const val KEY_BABY_PROMPT_DISMISSED = "baby_profile_prompt_dismissed"
        private const val KEY_MONTHLY_SKIPPED = "monthly_cup_skipped_month"
        private const val KEY_MONTHLY_NOTIFIED = "monthly_cup_notified_month"
        // v0.8.0 only re-read hand-picked photos under "picked_photo_dates_fixed";
        // a new key so scanned photos get the same one-time fix.
        private const val KEY_KEPT_DATES_FIXED = "kept_photo_dates_fixed"
        private const val KEY_UPDATE_LAST_CHECKED = "update_last_checked_at"
        private const val KEY_UPDATE_OFFERED = "update_offered_version_code"
        private const val KEY_UPDATE_DISMISSED = "update_dismissed_version_code"
        private const val KEY_UPDATE_FAILURES = "update_check_failures"
    }
}
