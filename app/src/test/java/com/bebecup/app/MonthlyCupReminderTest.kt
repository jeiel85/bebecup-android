package com.bebecup.app

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.domain.profile.CalendarDate
import com.bebecup.app.nudge.NudgeWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The daily reminder worker's 이달의 월드컵 path: once a month, from the app's
 * own records only — so it works without full gallery access ("선택한 사진만").
 * On the day it fires, the worker stops there: at most one reminder a day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyCupReminderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = BebecupPrefs(context)
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        MonthlyCup.clock = { CalendarDate(2026, 11, 3) }
        context.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        // Notifications allowed; READ_MEDIA_IMAGES deliberately not granted.
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        prefs.nudgeEnabled = true
    }

    @After
    fun tearDown() {
        MonthlyCup.clock = MonthlyCup::deviceToday
        Thread { AppDatabase.getDatabase(context).clearAllTables() }.apply { start(); join() }
    }

    private fun seedBest(vararg takenDates: String) = runBlocking {
        val dao = AppDatabase.getDatabase(context).babyPhotoDao()
        val ids = takenDates.mapIndexed { i, date ->
            dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = date)).toInt()
        }
        dao.markBest(ids, 1_000L)
    }

    private fun runWorker(): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<NudgeWorker>(context).build().doWork() }

    @Test
    fun `last month's cup is announced once, without full gallery access`() {
        seedBest("2026-10-01", "2026-10-20")

        assertEquals(ListenableWorker.Result.success(), runWorker())
        val posted = notifications.allNotifications
        assertEquals(1, posted.size)
        assertEquals("monthly_cup", posted.single().channelId)
        assertEquals("2026-10", prefs.monthlyCupNotifiedMonth)
        // The unsorted-photo reminder didn't run that day.
        assertEquals(0L, prefs.lastNudgeAtMillis)

        // The next day: already announced, nothing new.
        MonthlyCup.clock = { CalendarDate(2026, 11, 4) }
        runWorker()
        assertEquals(1, notifications.allNotifications.size)
    }

    @Test
    fun `nothing is announced without two photos from last month`() {
        seedBest("2026-10-01", "2026-09-01", "2026-09-02")
        runWorker()
        assertEquals(0, notifications.allNotifications.size)
        assertEquals("", prefs.monthlyCupNotifiedMonth)
    }

    @Test
    fun `nothing is announced while reminders are off`() {
        seedBest("2026-10-01", "2026-10-20")
        prefs.nudgeEnabled = false
        runWorker()
        assertEquals(0, notifications.allNotifications.size)
    }
}
