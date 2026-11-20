package com.bebecup.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
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
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real home → 보관함 → 월드컵 → 인화함 journey with the production
 * ViewModel and Room database. Screens are captured with Roborazzi when run
 * with -Proborazzi.test.record=true (images land in build/outputs/roborazzi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class JourneyFlowTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: BabyCupViewModel

    @Before
    fun setUp() {
        context.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = BabyCupViewModel(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        // Room refuses clearAllTables() on the main thread.
        Thread { AppDatabase.getDatabase(context).clearAllTables() }.apply { start(); join() }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/journey_$name.png")
    }

    private fun seedBestPhotos(count: Int): List<Int> = runBlocking {
        val dao = AppDatabase.getDatabase(context).babyPhotoDao()
        val ids = (1..count).map { i ->
            dao.insertPhoto(
                BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = "2026-11-0$i")
            ).toInt()
        }
        dao.markBest(ids, 1_000L)
        ids
    }

    @Test
    fun `first launch shows onboarding, then home`() {
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.onNodeWithTag("onboarding_view").assertIsDisplayed()
        capture("01_onboarding_first")
        repeat(3) {
            compose.onNodeWithTag("onboarding_next_button").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("onboarding_start_button").assertIsDisplayed()
        capture("02_onboarding_last")

        compose.onNodeWithTag("onboarding_start_without_nudge_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nudge_hero_card").assertIsDisplayed()
        compose.onNodeWithTag("goto_ai_curation_button").assertIsDisplayed()
        capture("03_home_empty")
    }

    @Test
    fun `world cup from the best box ends in the print box`() {
        viewModel.completeOnboarding()
        val ids = seedBestPhotos(4)
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitForIdle()
        capture("04_home_with_best")

        compose.onNodeWithTag("goto_best_box_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("best_box_view").assertIsDisplayed()
        capture("05_best_box")

        compose.onNodeWithText("월드컵 하기").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bracket_size_4").assertIsDisplayed()
        capture("06_worldcup_setup")
        compose.onNodeWithTag("start_worldcup_button").performClick()
        compose.waitForIdle()
        capture("07_worldcup_play")

        // Double tap on the first match must count once: 4 entrants → exactly 3 matches.
        compose.onNodeWithTag("vote_left_candidate").performClick()
        compose.onNodeWithTag("vote_left_candidate").performClick()
        compose.waitForIdle()
        var safety = 0
        while (viewModel.currentScreen is UiScreen.WorldCupPlay && safety++ < 5) {
            compose.onNodeWithTag("vote_left_candidate").performClick()
            compose.waitForIdle()
        }
        assertTrue(viewModel.currentScreen is UiScreen.WorldCupWinner)
        compose.onNodeWithTag("world_cup_winner_view").assertIsDisplayed()
        capture("08_winner")

        val photos = runBlocking { ids.map { AppDatabase.getDatabase(context).babyPhotoDao().getPhotoById(it)!! } }
        assertEquals("one bracket of 4 = 3 match wins in total", 3, photos.sumOf { it.matchesCount })
        assertEquals(1, photos.sumOf { it.winsCount })

        compose.onNodeWithTag("winner_add_to_print_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("인화함 보기").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("print_cart_view").assertIsDisplayed()
        compose.onNodeWithTag("share_print_button").assertIsDisplayed()
        capture("09_print_box")
    }

    @Test
    fun `settings and empty states render`() {
        viewModel.completeOnboarding()
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.onNodeWithTag("goto_settings_button").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("settings_view").assertIsDisplayed()
        capture("10_settings")

        compose.runOnIdle { viewModel.navigateHome(); viewModel.navigateTo(UiScreen.PrintCart) }
        compose.waitForIdle()
        capture("11_print_empty")
        compose.runOnIdle { viewModel.navigateHome(); viewModel.navigateTo(UiScreen.AiScanSetup) }
        compose.waitForIdle()
        compose.onNodeWithTag("start_ai_curation_btn").assertIsDisplayed()
        capture("12_scan_setup")
    }
}
