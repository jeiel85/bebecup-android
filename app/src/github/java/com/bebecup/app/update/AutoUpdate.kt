package com.bebecup.app.update

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When the GitHub build may update itself without asking, and when it should
 * say so with a notification (the same rules as Homebase and 포너디). Pure, so
 * `AutoUpdatePolicyTest` pins them.
 */
object AutoUpdatePolicy {

    /** `PackageInstaller.SessionParams.setRequireUserAction` exists from Android 12. */
    const val SILENT_INSTALL_MIN_SDK = 31

    /** A version whose silent install failed this often is left to the parent (notification + banner). */
    const val MAX_SILENT_ATTEMPTS = 2

    /**
     * Install [offeredVersionCode] in the background without a confirmation only when the parent
     * allows it, Android can do it, the app is not on screen (replacing it would close it under
     * their fingers), the install is signed with the GitHub key, and this version has not already
     * asked for a confirmation or failed too often.
     */
    fun shouldInstallSilently(
        autoInstall: Boolean,
        deviceSdk: Int,
        appVisible: Boolean,
        signerMatches: Boolean,
        canRequestInstalls: Boolean,
        offeredVersionCode: Int,
        needsConfirmVersionCode: Int,
        failedVersionCode: Int,
        failedAttempts: Int,
    ): Boolean =
        autoInstall &&
            deviceSdk >= SILENT_INSTALL_MIN_SDK &&
            !appVisible &&
            signerMatches &&
            canRequestInstalls &&
            offeredVersionCode != needsConfirmVersionCode &&
            !(offeredVersionCode == failedVersionCode && failedAttempts >= MAX_SILENT_ATTEMPTS)

    /** One "새 버전이 나왔어요" notification per version, only for something newer than this install. */
    fun shouldNotify(
        backgroundCheck: Boolean,
        offeredVersionCode: Int,
        installedVersionCode: Int,
        notifiedVersionCode: Int,
    ): Boolean =
        backgroundCheck && offeredVersionCode > installedVersionCode && offeredVersionCode != notifiedVersionCode
}

/** The GitHub updater's own settings and bookkeeping (not backed up anywhere else). */
class AutoUpdatePrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bebecup_github_update", Context.MODE_PRIVATE)

    /** 설정 "새 버전 확인": check GitHub every 12 hours even when the app is closed, and notify. */
    var backgroundCheck: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_CHECK, value).apply()

    /** 설정 "앱을 쓰지 않을 때 자동 설치" (Android 12+). */
    var autoInstall: Boolean
        get() = prefs.getBoolean(KEY_AUTO_INSTALL, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_INSTALL, value).apply()

    var notifiedVersionCode: Int
        get() = prefs.getInt(KEY_NOTIFIED, 0)
        set(value) = prefs.edit().putInt(KEY_NOTIFIED, value).apply()

    /** Android asked to confirm this version's silent install: leave it to the parent. */
    var needsConfirmVersionCode: Int
        get() = prefs.getInt(KEY_NEEDS_CONFIRM, 0)
        set(value) = prefs.edit().putInt(KEY_NEEDS_CONFIRM, value).apply()

    val failedVersionCode: Int get() = prefs.getInt(KEY_FAILED_VERSION, 0)
    val failedAttempts: Int get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun addSilentFailure(versionCode: Int) {
        val attempts = if (failedVersionCode == versionCode) failedAttempts + 1 else 1
        prefs.edit().putInt(KEY_FAILED_VERSION, versionCode).putInt(KEY_FAILED_ATTEMPTS, attempts).apply()
    }

    /** The versionCode handed to Android's installer, so the next process can tell it was replaced. 0 = none. */
    var installingVersionCode: Int
        get() = prefs.getInt(KEY_INSTALLING, 0)
        // commit(), not apply(): Android may kill this process right after the install starts.
        @SuppressLint("ApplySharedPref")
        set(value) {
            prefs.edit().putInt(KEY_INSTALLING, value).commit()
        }

    private companion object {
        const val KEY_BACKGROUND_CHECK = "background_check"
        const val KEY_AUTO_INSTALL = "auto_install"
        const val KEY_NOTIFIED = "notified_version_code"
        const val KEY_NEEDS_CONFIRM = "needs_confirm_version_code"
        const val KEY_FAILED_VERSION = "silent_failed_version_code"
        const val KEY_FAILED_ATTEMPTS = "silent_failed_attempts"
        const val KEY_INSTALLING = "installing_version_code"
    }
}

/**
 * Is any BebeCup screen started (onStart..onStop)? A background install would close the
 * app in front of the parent, so it waits. Zero when a worker wakes the process.
 */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
        private set

    private var started = 0
    private val registered = AtomicBoolean(false)

    /** Called from the first activity's onCreate, before its onStart. */
    fun register(application: Application) {
        if (!registered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                visible = true
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                visible = started > 0
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
