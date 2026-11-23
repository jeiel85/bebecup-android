package com.bebecup.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.FaceState
import com.bebecup.app.data.children.PhotoFaceEntity
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import kotlinx.coroutines.flow.first
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

/** Two children: each one's cup draws only their own photos and is recorded under their name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChildWorldCupFlowTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val db get() = AppDatabase.getDatabase(context)
    private lateinit var viewModel: BabyCupViewModel

    @Before
    fun setUp() {
        context.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = BabyCupViewModel(ApplicationProvider.getApplicationContext())
        viewModel.completeOnboarding()
    }

    @After
    fun tearDown() {
        Thread { db.clearAllTables() }.apply { start(); join() }
    }

    /** Saved photos, each with the given children's faces in it. */
    private fun seed(vararg whoIsIn: List<Long>): List<Int> = runBlocking {
        val dao = db.babyPhotoDao()
        whoIsIn.mapIndexed { i, children ->
            val id = dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = "2026-05-0${i + 1}")).toInt()
            dao.markBest(listOf(id), 1_000L + i)
            db.childrenDao().insertFaces(
                children.map { child ->
                    PhotoFaceEntity(
                        photoId = id, boxLeft = 0f, boxTop = 0f, boxRight = 0.4f, boxBottom = 0.4f,
                        embedding = ByteArray(512), childId = child, state = FaceState.MANUAL, modelVersion = 1
                    )
                }
            )
            id
        }
    }

    @Test
    fun `a child's cup only has that child's photos and is recorded under their name`() {
        val (harin, doyun) = runBlocking {
            db.childrenDao().insertChild(ChildEntity(name = "하린", createdAtMillis = 1L)) to
                db.childrenDao().insertChild(ChildEntity(name = "도윤", createdAtMillis = 2L))
        }
        val ids = seed(listOf(harin), listOf(harin), listOf(doyun), listOf(doyun), listOf(harin, doyun), listOf())
        val harinPhotos = setOf(ids[0], ids[1], ids[4])

        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitUntil(timeoutMillis = 10_000) { viewModel.children.size == 2 && viewModel.photoChildren.size == 5 }
        compose.runOnIdle { viewModel.openWorldCup() }
        compose.waitForIdle()

        // The choices sit below the illustration on the small test screen; check they are offered.
        compose.onNodeWithText("모두 · 6장").assertExists()
        compose.onNodeWithText("하린 · 3장").assertExists()
        compose.onNodeWithText("도윤 · 3장").assertExists()
        compose.runOnIdle { viewModel.selectWorldCupChild(harin) }
        compose.waitForIdle()
        compose.onNodeWithText("하린 이상형 월드컵").assertIsDisplayed()
        compose.onNodeWithTag("start_worldcup_button").performClick()
        compose.waitForIdle()
        assertEquals(UiScreen.WorldCupPlay, viewModel.currentScreen)

        // Play the 2-photo bracket (3 photos → 2강) through; every photo met is 하린's.
        val seen = mutableSetOf<Int>()
        while (viewModel.currentScreen == UiScreen.WorldCupPlay) {
            val match = viewModel.currentMatch!!
            seen += match.left.id
            seen += match.right.id
            compose.runOnIdle { viewModel.vote(match, match.left) }
            compose.waitForIdle()
        }
        assertTrue("$seen ⊄ $harinPhotos", harinPhotos.containsAll(seen))

        compose.waitUntil(timeoutMillis = 10_000) { runBlocking { db.babyPhotoDao().getAllTournaments().first() }.isNotEmpty() }
        val record = runBlocking { db.babyPhotoDao().getAllTournaments().first() }.single()
        assertEquals("하린 이상형 월드컵", record.tournamentTitle)
    }
}
