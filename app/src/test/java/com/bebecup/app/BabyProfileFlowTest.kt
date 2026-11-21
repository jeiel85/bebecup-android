package com.bebecup.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BabyProfileFlowTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var viewModel: BabyCupViewModel

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

    private fun seedBest(vararg takenDates: String) = runBlocking {
        val dao = AppDatabase.getDatabase(context).babyPhotoDao()
        val ids = takenDates.mapIndexed { i, date ->
            dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = date)).toInt()
        }
        dao.markBest(ids, 1_000L)
    }

    /** The card sits below the fold on a small test screen; LazyColumn only composes what it shows. */
    private fun scrollToPrompt() {
        compose.onNodeWithTag("dashboard_view").performScrollToNode(hasTestTag("baby_profile_prompt"))
        compose.waitForIdle()
    }

    @Test
    fun `profile is validated, saved to prefs and cleared`() {
        viewModel.updateBabyProfile("  하린  ", "2026-01-31")
        assertEquals("하린", viewModel.babyName)
        assertEquals("2026-01-31", BebecupPrefs(context).babyBirthDate)
        val photo = BabyPhoto(uriString = "content://x", title = "", description = "", takenDate = "2026-03-31")
        assertEquals("2개월", viewModel.ageOf(photo)?.shortLabel)

        viewModel.updateBabyProfile("하린", "2026-02-30") // impossible date clears the birthday
        assertEquals("", viewModel.babyBirthDate)
        assertNull(viewModel.ageOf(photo))
    }

    @Test
    fun `home asks for the birthday once photos are saved, and best box shows ages after it`() {
        seedBest("2026-01-20", "2026-05-08")
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitForIdle()
        scrollToPrompt()
        compose.onNodeWithTag("baby_profile_prompt").assertIsDisplayed()

        compose.runOnIdle { viewModel.updateBabyProfile("", "2026-01-01") }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("baby_profile_prompt").fetchSemanticsNodes().size)

        compose.runOnIdle { viewModel.navigateTo(UiScreen.BestBox) }
        compose.waitForIdle()
        compose.onNodeWithText("4개월").assertIsDisplayed()
        compose.onNodeWithText("D+20").assertIsDisplayed()
    }

    @Test
    fun `later hides the birthday card for good`() {
        seedBest("2026-03-01")
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitForIdle()
        scrollToPrompt()
        compose.onNodeWithText("다음에").performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag("baby_profile_prompt").fetchSemanticsNodes().size)
        assertEquals(true, BebecupPrefs(context).babyProfilePromptDismissed)
    }
}
