package com.bebecup.app

import com.bebecup.app.update.AutoUpdatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdatePolicyTest {

    private fun silent(
        autoInstall: Boolean = true,
        deviceSdk: Int = 34,
        appVisible: Boolean = false,
        signerMatches: Boolean = true,
        canRequestInstalls: Boolean = true,
        offered: Int = 13,
        needsConfirm: Int = 0,
        failedVersion: Int = 0,
        failedAttempts: Int = 0,
    ) = AutoUpdatePolicy.shouldInstallSilently(
        autoInstall, deviceSdk, appVisible, signerMatches, canRequestInstalls, offered, needsConfirm, failedVersion, failedAttempts
    )

    @Test
    fun installsWithoutAskingOnlyWhenEverythingAllowsIt() {
        assertTrue(silent())
        assertFalse("parent turned it off", silent(autoInstall = false))
        assertFalse("Android 11 has no USER_ACTION_NOT_REQUIRED", silent(deviceSdk = 30))
        assertTrue(silent(deviceSdk = 31))
        assertFalse("replacing the app would close it in front of the parent", silent(appVisible = true))
        assertFalse("Android would refuse another key's APK", silent(signerMatches = false))
        assertFalse("'이 출처의 앱 설치' not allowed yet", silent(canRequestInstalls = false))
    }

    @Test
    fun leavesAVersionToTheParentOnceAndroidAskedOrItFailedTwice() {
        assertFalse(silent(offered = 13, needsConfirm = 13))
        assertTrue("a newer version tries again", silent(offered = 14, needsConfirm = 13))
        assertTrue(silent(offered = 13, failedVersion = 13, failedAttempts = 1))
        assertFalse(silent(offered = 13, failedVersion = 13, failedAttempts = AutoUpdatePolicy.MAX_SILENT_ATTEMPTS))
        assertTrue(silent(offered = 14, failedVersion = 13, failedAttempts = 5))
    }

    @Test
    fun notifiesOncePerNewerVersion() {
        assertTrue(AutoUpdatePolicy.shouldNotify(true, offeredVersionCode = 13, installedVersionCode = 12, notifiedVersionCode = 0))
        assertFalse(AutoUpdatePolicy.shouldNotify(true, 13, 12, notifiedVersionCode = 13))
        assertTrue(AutoUpdatePolicy.shouldNotify(true, 14, 12, notifiedVersionCode = 13))
        assertFalse("not newer", AutoUpdatePolicy.shouldNotify(true, 12, 12, 0))
        assertFalse("background check off", AutoUpdatePolicy.shouldNotify(false, 13, 12, 0))
    }
}
