package com.bebecup.app

import com.bebecup.app.update.UpdateCheckPolicy
import com.bebecup.app.update.UpdateStatus
import com.bebecup.app.update.offersUpdate
import com.bebecup.app.update.versionName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {

    @Test
    fun rechecksOnlyAfterTheIntervalOrWhenNeverChecked() {
        val now = 1_000_000_000L
        assertTrue(UpdateCheckPolicy.isStale(lastCheckedAt = 0L, now = now))
        assertFalse(UpdateCheckPolicy.isStale(lastCheckedAt = now - 1_000L, now = now))
        assertTrue(UpdateCheckPolicy.isStale(lastCheckedAt = now - UpdateCheckPolicy.RECHECK_MILLIS, now = now))
        // Clock moved backwards: ask again rather than waiting hours.
        assertTrue(UpdateCheckPolicy.isStale(lastCheckedAt = now + 1_000L, now = now))
    }

    @Test
    fun homeBannerShowsOnlyWhileSomethingNewerIsOnOffer() {
        assertFalse(UpdateStatus.Unknown.offersUpdate)
        assertFalse(UpdateStatus.UpToDate.offersUpdate)
        assertFalse(UpdateStatus.CheckFailed("offline").offersUpdate)
        assertTrue(UpdateStatus.Available("0.9.0", 10).offersUpdate)
        assertTrue(UpdateStatus.Downloading("0.9.0", 0.5f).offersUpdate)
        assertTrue(UpdateStatus.ReadyToInstall("0.9.0").offersUpdate)
        assertTrue(UpdateStatus.DownloadFailed("0.9.0", "x").offersUpdate)
    }

    @Test
    fun bannerKeepsTheVersionThroughDownloadAndFailure() {
        assertEquals("0.9.0", UpdateStatus.Available("0.9.0", 10).versionName)
        assertEquals("0.9.0", UpdateStatus.Downloading("0.9.0", null).versionName)
        assertEquals("0.9.0", UpdateStatus.ReadyToInstall("0.9.0").versionName)
        assertEquals("0.9.0", UpdateStatus.DownloadFailed("0.9.0", "x").versionName)
        assertNull(UpdateStatus.UpToDate.versionName)
    }
}
