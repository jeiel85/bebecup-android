package com.bebecup.app

import android.content.Context
import android.os.Looper
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
import com.bebecup.app.data.children.FaceState
import com.bebecup.app.data.children.PhotoFaceEntity
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
import org.robolectric.Shadows.shadowOf
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

    /** Room answers on its own threads, then the view model continues on the main looper. */
    private fun waitUntil(condition: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition never became true")
    }

    private fun seedBest(vararg takenDates: String): List<Int> = runBlocking {
        val dao = AppDatabase.getDatabase(context).babyPhotoDao()
        val ids = takenDates.mapIndexed { i, date ->
            dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = date)).toInt()
        }
        dao.markBest(ids, 1_000L)
        ids
    }

    /** Put [childId]'s face in [photoId], as face-ID or the parent would. */
    private fun tag(photoId: Int, childId: Long) = runBlocking {
        AppDatabase.getDatabase(context).childrenDao().insertFaces(
            listOf(
                PhotoFaceEntity(
                    photoId = photoId, boxLeft = 0f, boxTop = 0f, boxRight = 0.5f, boxBottom = 0.5f,
                    embedding = ByteArray(512), childId = childId, state = FaceState.MANUAL, modelVersion = 1
                )
            )
        )
    }

    /** The card sits below the fold on a small test screen; LazyColumn only composes what it shows. */
    private fun scrollToPrompt() {
        compose.onNodeWithTag("dashboard_view").performScrollToNode(hasTestTag("baby_profile_prompt"))
        compose.waitForIdle()
    }

    @Test
    fun `a child is validated, saved and edited`() {
        viewModel.addChild("  하린  ", "2026-01-31")
        waitUntil { viewModel.children.isNotEmpty() }
        val child = viewModel.children.single()
        assertEquals("하린", child.name)
        assertEquals("2026-01-31", child.birthDate)
        val photo = BabyPhoto(uriString = "content://x", title = "", description = "", takenDate = "2026-03-31")
        // One child: photos where nobody was found are that child's.
        assertEquals("2개월", viewModel.ageOf(photo)?.shortLabel)

        viewModel.updateChild(child.id, "하린", "2026-02-30") // impossible date clears the birthday
        waitUntil { viewModel.children.single().birthDate == "" }
        assertNull(viewModel.ageOf(photo))

        viewModel.addChild("   ", "2026-01-01") // a name is required
        viewModel.addChild("도윤", null)
        waitUntil { viewModel.children.size == 2 }
        assertEquals(listOf("하린", "도윤"), viewModel.children.map { it.name })
    }

    @Test
    fun `the pre-v6 baby profile becomes the first child`() {
        context.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        Thread { AppDatabase.getDatabase(context).clearAllTables() }.apply { start(); join() }
        BebecupPrefs(context).apply {
            babyName = "하린"
            babyBirthDate = "2026-01-31"
        }
        val upgraded = BabyCupViewModel(ApplicationProvider.getApplicationContext())
        waitUntil { upgraded.children.isNotEmpty() }
        assertEquals("하린", upgraded.children.single().name)
        assertEquals("2026-01-31", upgraded.children.single().birthDate)
        assertEquals(true, BebecupPrefs(context).legacyProfileMoved)
    }

    @Test
    fun `with two children each photo shows its own child's age`() {
        val (harinPhoto, doyunPhoto) = seedBest("2026-05-01", "2026-05-01")
        viewModel.addChild("하린", "2026-01-01")
        viewModel.addChild("도윤", "2024-05-01")
        waitUntil { viewModel.children.size == 2 }
        val (harin, doyun) = viewModel.children
        tag(harinPhoto, harin.id)
        tag(doyunPhoto, doyun.id)
        waitUntil { viewModel.photoChildren.size == 2 }

        fun photo(id: Int) = runBlocking { AppDatabase.getDatabase(context).babyPhotoDao().getPhotoById(id)!! }
        assertEquals("4개월", viewModel.ageOf(photo(harinPhoto))?.shortLabel)
        assertEquals("24개월", viewModel.ageOf(photo(doyunPhoto))?.shortLabel)
        // With two children, a photo nobody was found in has no age.
        val untagged = BabyPhoto(uriString = "content://y", title = "", description = "", takenDate = "2026-05-01")
        assertNull(viewModel.ageOf(untagged))
    }

    @Test
    fun `home asks for the birthday once photos are saved, and best box shows ages after it`() {
        seedBest("2026-01-20", "2026-05-08")
        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitForIdle()
        scrollToPrompt()
        compose.onNodeWithTag("baby_profile_prompt").assertIsDisplayed()

        compose.runOnIdle { viewModel.addChild("하린", "2026-01-01") }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("baby_profile_prompt").fetchSemanticsNodes().isEmpty()
        }

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
