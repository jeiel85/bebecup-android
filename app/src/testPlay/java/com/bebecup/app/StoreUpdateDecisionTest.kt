package com.bebecup.app

import com.bebecup.app.update.StoreUpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreUpdateDecisionTest {

    @Test
    fun offersOnlyAStrictlyNewerVersionPlayCallsAvailable() {
        assertEquals(8, StoreUpdateDecision.offeredVersionCode(updateAvailable = true, availableVersionCode = 8, installedVersionCode = 7))
        assertEquals(0, StoreUpdateDecision.offeredVersionCode(updateAvailable = true, availableVersionCode = 7, installedVersionCode = 7))
        assertEquals(0, StoreUpdateDecision.offeredVersionCode(updateAvailable = true, availableVersionCode = 6, installedVersionCode = 7))
        // Already installing, or Play says not available: no banner.
        assertEquals(0, StoreUpdateDecision.offeredVersionCode(updateAvailable = false, availableVersionCode = 8, installedVersionCode = 7))
    }

    @Test
    fun rechecksOnlyAfterTheIntervalOrWhenNeverChecked() {
        val now = 1_000_000_000L
        assertTrue(StoreUpdateDecision.isStale(lastCheckedAt = 0L, now = now))
        assertFalse(StoreUpdateDecision.isStale(lastCheckedAt = now - 1_000L, now = now))
        assertTrue(StoreUpdateDecision.isStale(lastCheckedAt = now - StoreUpdateDecision.RECHECK_MILLIS, now = now))
        // Clock moved backwards: ask again rather than waiting hours.
        assertTrue(StoreUpdateDecision.isStale(lastCheckedAt = now + 1_000L, now = now))
    }

    @Test
    fun bannerHidesOnlyTheVersionTheUserClosed() {
        assertEquals(9, StoreUpdateDecision.bannerVersionCode(available = 9, dismissedVersionCode = 0))
        assertEquals(null, StoreUpdateDecision.bannerVersionCode(available = 9, dismissedVersionCode = 9))
        // A newer version than the one closed shows the banner again.
        assertEquals(10, StoreUpdateDecision.bannerVersionCode(available = 10, dismissedVersionCode = 9))
        assertEquals(null, StoreUpdateDecision.bannerVersionCode(available = null, dismissedVersionCode = 0))
        assertEquals(null, StoreUpdateDecision.bannerVersionCode(available = 0, dismissedVersionCode = 0))
    }
}
