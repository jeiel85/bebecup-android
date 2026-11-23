package com.bebecup.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.ShortlistItem
import com.bebecup.app.domain.monthly.MonthlyCup
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Two children: siblings never share a cup; each one's draws only their own photos and is recorded as theirs. */
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

    private fun twoChildren(): Pair<Long, Long> = runBlocking {
        db.childrenDao().insertChild(ChildEntity(name = "하린", createdAtMillis = 1L)) to
            db.childrenDao().insertChild(ChildEntity(name = "도윤", createdAtMillis = 2L))
    }

    /** Saved photos taken on [takenDate], each with the given children's faces in it. */
    private fun seed(vararg whoIsIn: List<Long>, takenDate: (Int) -> String = { "2026-05-0${it + 1}" }): List<Int> = runBlocking {
        val dao = db.babyPhotoDao()
        whoIsIn.mapIndexed { i, children ->
            val id = dao.insertPhoto(BabyPhoto(uriString = "content://test/$i", title = "사진 $i", description = "", takenDate = takenDate(i))).toInt()
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

    /** Votes for the left photo until the cup ends; returns every photo that played. */
    private fun playThrough(): Set<Int> {
        val seen = mutableSetOf<Int>()
        while (viewModel.currentScreen == UiScreen.WorldCupPlay) {
            val match = viewModel.currentMatch!!
            seen += match.left.id
            seen += match.right.id
            compose.runOnIdle { viewModel.vote(match, match.left) }
            compose.waitForIdle()
        }
        return seen
    }

    /** Room answers on its own threads, then the view model continues on the main looper. */
    private fun waitOnRoom(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("condition never became true")
    }

    private fun recordedCups() = runBlocking { db.babyPhotoDao().getAllTournaments().first() }

    @Test
    fun `a child's cup only has that child's photos and is recorded under their name`() {
        val (harin, doyun) = twoChildren()
        val ids = seed(listOf(harin), listOf(harin), listOf(doyun), listOf(doyun), listOf(harin, doyun), listOf())
        val harinPhotos = setOf(ids[0], ids[1], ids[4])

        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitUntil(timeoutMillis = 10_000) { viewModel.children.size == 2 && viewModel.photoChildren.size == 5 }
        compose.runOnIdle { viewModel.openWorldCup() }
        compose.waitForIdle()

        // Siblings: no cup over everyone; it opens on the first child's.
        compose.onNodeWithText("모두 · 6장").assertDoesNotExist()
        compose.onNodeWithTag("cup_child_all").assertDoesNotExist()
        compose.onNodeWithText("하린 이상형 월드컵").assertIsDisplayed()
        // The choices sit below the illustration on the small test screen; check they are offered.
        compose.onNodeWithText("하린 · 3장").assertExists()
        compose.onNodeWithText("도윤 · 3장").assertExists()
        // Even asked for directly, a cup over both is refused.
        compose.runOnIdle { viewModel.startWorldCup(size = 4, month = null, child = null) }
        compose.waitForIdle()
        assertEquals(UiScreen.WorldCupSetup, viewModel.currentScreen)

        compose.onNodeWithTag("start_worldcup_button").performClick()
        compose.waitForIdle()
        assertEquals(UiScreen.WorldCupPlay, viewModel.currentScreen)

        // Play the 2-photo bracket (3 photos → 2강) through; every photo met is 하린's.
        val seen = playThrough()
        assertTrue("$seen ⊄ $harinPhotos", harinPhotos.containsAll(seen))

        compose.waitUntil(timeoutMillis = 10_000) { recordedCups().isNotEmpty() }
        val record = recordedCups().single()
        assertEquals("하린 이상형 월드컵", record.tournamentTitle)
        assertEquals(harin, record.childId)
    }

    @Test
    fun `siblings with no photo known to be theirs get no cup`() {
        twoChildren()
        seed(listOf(), listOf(), listOf(), listOf())

        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitUntil(timeoutMillis = 10_000) { viewModel.children.size == 2 && viewModel.bestPhotos.value.size == 4 }
        compose.runOnIdle { viewModel.openWorldCup() }
        compose.waitForIdle()

        compose.onNodeWithTag("cup_needs_children").assertIsDisplayed()
        compose.onNodeWithTag("start_worldcup_button").assertDoesNotExist()
        compose.onNodeWithTag("cup_needs_children").performClick()
        compose.waitForIdle()
        assertEquals(UiScreen.Children, viewModel.currentScreen)
    }

    @Test
    fun `the cup straight from a curation round is one child's`() {
        val (harin, doyun) = twoChildren()
        val ids = seed(listOf(harin), listOf(harin), listOf(doyun), listOf(doyun), listOf(doyun), listOf())
        val doyunPhotos = setOf(ids[2], ids[3], ids[4])

        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitUntil(timeoutMillis = 10_000) { viewModel.children.size == 2 && viewModel.photoChildren.size == 5 }
        val photos = runBlocking { ids.map { db.babyPhotoDao().getPhotoById(it)!! } }
        compose.runOnIdle {
            viewModel.showCurationResult(
                photos.mapIndexed { i, photo ->
                    ShortlistItem(
                        photo,
                        PhotoAnalysisEntity(
                            photoId = photo.id, analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
                            blurScore = 0.9f, faceDetected = true, faceCount = 1, faceCenterScore = 0.9f,
                            eyeOpenScore = 0.9f, expressionScore = 0.9f, exposureScore = 0.9f, compositionScore = 0.9f,
                            duplicatePenalty = 0f, overallScore = 90f - i, qualityGrade = "A",
                            rejectReasonsJson = "[]", positiveReasonsJson = "[]", analyzedAtMillis = 1L
                        )
                    )
                }
            )
        }
        compose.waitForIdle()

        // Both can have a cup: the button asks whose first.
        assertEquals(listOf(harin to 2, doyun to 3), viewModel.curationCupChoices().map { it.first.id to it.second })
        compose.onNodeWithText("6장 보관하고 아이별 월드컵").assertExists()
        compose.onNodeWithTag("start_worldcup_from_ai_btn").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("누구의 월드컵부터 할까요?").assertIsDisplayed()
        compose.onNodeWithTag("ai_cup_child_$doyun").performClick()
        waitOnRoom { viewModel.currentScreen == UiScreen.WorldCupPlay }

        val seen = playThrough()
        assertTrue("$seen ⊄ $doyunPhotos", doyunPhotos.containsAll(seen))
        compose.waitUntil(timeoutMillis = 10_000) { recordedCups().isNotEmpty() }
        assertEquals(doyun, recordedCups().single().childId)
    }

    @Test
    fun `last month's cup is offered to each sibling in turn`() {
        val (harin, doyun) = twoChildren()
        val lastMonth = MonthlyCup.previousMonth(MonthlyCup.today())
        seed(listOf(harin), listOf(harin), listOf(doyun), listOf(doyun), takenDate = { "$lastMonth-0${it + 1}" })

        compose.setContent { MyApplicationTheme { MainContentScreen(viewModel) } }
        compose.waitUntil(timeoutMillis = 10_000) { viewModel.children.size == 2 && viewModel.photoChildren.size == 4 }
        fun due() = viewModel.pendingMonthlyCup(viewModel.bestPhotos.value, recordedCups())
        compose.waitUntil(timeoutMillis = 10_000) { viewModel.bestPhotos.value.size == 4 }
        assertEquals(harin, due()?.childId)

        compose.runOnIdle { viewModel.openWorldCup(lastMonth, harin) }
        compose.waitForIdle()
        compose.onNodeWithTag("start_worldcup_button").performClick()
        compose.waitForIdle()
        playThrough()
        compose.onNodeWithTag("winner_title").assertTextContains("${viewModel.monthLabel(lastMonth)}의 하린 사진!")
        compose.waitUntil(timeoutMillis = 10_000) { recordedCups().isNotEmpty() }
        assertEquals(MonthlyCup.Held(lastMonth, harin), recordedCups().single().let { MonthlyCup.Held(it.monthKey!!, it.childId) })

        // 하린's month is decided; 도윤's is still waiting, and is where the setup opens for that month.
        assertEquals(doyun, due()?.childId)
        assertEquals(doyun, viewModel.defaultCupChild(viewModel.bestPhotos.value, lastMonth, recordedCups()))
    }
}
