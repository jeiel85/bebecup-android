package com.bebecup.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.domain.profile.CalendarDate
import com.bebecup.app.nudge.NudgeNotifier
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 이달의 월드컵: home card → that month's photos only → winner kept as the month's photo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyCupFlowTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dao get() = AppDatabase.getDatabase(context).babyPhotoDao()
    private lateinit var viewModel: BabyCupViewModel

    /** Relative to the real today, because the view model uses the device calendar. */
    private val lastMonth = MonthlyCup.previousMonth(MonthlyCup.today())
    private val olderMonth = MonthlyCup.previousMonth(CalendarDate.parse("$lastMonth-01")!!)

    @Before
    fun setUp() {
        context.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = BabyCupViewModel(ApplicationProvider.getApplicationContext())
        viewModel.completeOnboarding()
    }

    @After
    fun tearDown() {
        Thread { AppDatabase.getDatabase(context).clearAllTables() }.apply { start(); join() }
    }

    private fun seedBest(vararg takenDates: String): List<Int> = runBlocking {
        val ids = takenDates.mapIndexed { i, date ->
            dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = date)).toInt()
        }
        dao.markBest(ids, 1_000L)
        ids
    }

    private fun showHome() {
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitForIdle()
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("dashboard_view").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    @Test
    fun `last month's cup is offered, played on that month only, and recorded`() {
        val lastMonthIds = seedBest("$lastMonth-03", "$lastMonth-15", "$lastMonth-28")
        seedBest("$olderMonth-01", "$olderMonth-02")
        showHome()
        scrollTo("monthly_cup_card")
        compose.onNodeWithTag("monthly_cup_open").performClick()
        compose.waitForIdle()

        assertEquals(UiScreen.WorldCupSetup, viewModel.currentScreen)
        assertEquals(lastMonth, viewModel.worldCupMonth)
        // Three photos → a bracket of three, all from last month.
        compose.onNodeWithTag("bracket_size_3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("start_worldcup_button").performClick()
        compose.waitForIdle()

        val seen = mutableSetOf<Int>()
        repeat(5) {
            val match = viewModel.currentMatch ?: return@repeat
            seen += match.left.id
            seen += match.right.id
            compose.runOnIdle { viewModel.vote(match, match.left) }
            compose.waitForIdle()
        }
        assertTrue("only last month's photos compete: $seen", lastMonthIds.containsAll(seen))
        val winner = viewModel.currentScreen as UiScreen.WorldCupWinner
        assertEquals(lastMonth, winner.monthKey)
        compose.onNodeWithTag("winner_title").assertTextContains("${viewModel.monthLabel(lastMonth)}의 사진!")

        compose.waitUntil(timeoutMillis = 10_000) { runBlocking { dao.getMonthlyCupKeys() } == listOf(lastMonth) }

        // Played: the home card is gone.
        compose.runOnIdle { viewModel.navigateHome() }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("monthly_cup_card").fetchSemanticsNodes().size)
    }

    @Test
    fun `skipping hides the card for that month`() {
        seedBest("$lastMonth-01", "$lastMonth-02")
        showHome()
        scrollTo("monthly_cup_card")
        compose.onNodeWithTag("monthly_cup_skip").performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("monthly_cup_card").fetchSemanticsNodes().size)
        assertEquals(lastMonth, BebecupPrefs(context).monthlyCupSkippedMonth)
    }

    @Test
    fun `no card with fewer than two photos from last month`() {
        seedBest("$lastMonth-01", "$olderMonth-01", "$olderMonth-02")
        showHome()
        assertEquals(0, compose.onAllNodesWithTag("monthly_cup_card").fetchSemanticsNodes().size)
    }

    @Test
    fun `the reminder opens that month's cup`() {
        seedBest("$lastMonth-01", "$lastMonth-02")
        viewModel.handleLaunchIntent(Intent().putExtra(NudgeNotifier.EXTRA_OPEN_MONTHLY_CUP, lastMonth))
        assertEquals(UiScreen.WorldCupSetup, viewModel.currentScreen)
        assertEquals(lastMonth, viewModel.worldCupMonth)
        assertTrue(viewModel.navigateBack())
        assertEquals(UiScreen.Dashboard, viewModel.currentScreen)
    }
}
