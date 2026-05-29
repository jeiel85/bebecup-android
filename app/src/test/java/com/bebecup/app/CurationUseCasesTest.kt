package com.bebecup.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.ai.PhotoAnalyzer
import com.bebecup.app.ai.RuleBasedVisionExplainer
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.data.ai.RoomAiCurationRepository
import com.bebecup.app.domain.usecase.AnalyzePhotoQualityUseCase
import com.bebecup.app.domain.usecase.BuildAiShortlistUseCase
import com.bebecup.app.domain.usecase.BuildRejectedListUseCase
import com.bebecup.app.domain.usecase.ClusterDuplicatesUseCase
import com.bebecup.app.domain.usecase.ExplainTopPhotosUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Integration tests for the AI curation use cases over a real in-memory Room DB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurationUseCasesTest {

    private lateinit var db: AppDatabase
    private lateinit var babyRepo: BabyPhotoRepository
    private lateinit var aiRepo: RoomAiCurationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        babyRepo = BabyPhotoRepository(db.babyPhotoDao())
        aiRepo = RoomAiCurationRepository(db.photoAnalysisDao(), db.photoClusterDao(), db.aiCurationSessionDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertPhoto(title: String): Int =
        babyRepo.insertPhoto(BabyPhoto(uriString = "content://$title", title = title, description = "", takenDate = "2026-05-01")).toInt()

    private fun analysis(photoId: Int, score: Float, dHash: Long = 0L, dim: Float = 0.9f) = PhotoAnalysisEntity(
        photoId = photoId,
        analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
        blurScore = dim, faceDetected = true, faceCount = 1, faceCenterScore = dim,
        eyeOpenScore = dim, expressionScore = dim, exposureScore = dim, compositionScore = dim,
        duplicatePenalty = 0f, overallScore = score, qualityGrade = "S", dHash = dHash,
        rejectReasonsJson = "[]", positiveReasonsJson = "[]", analyzedAtMillis = 0L
    )

    @Test
    fun `shortlist returns top scorers and rejected returns the rest`() = runBlocking {
        val a = insertPhoto("A"); val b = insertPhoto("B"); val c = insertPhoto("C")
        aiRepo.saveAnalyses(listOf(analysis(a, 60f), analysis(b, 95f), analysis(c, 80f)))

        val shortlist = BuildAiShortlistUseCase(babyRepo, aiRepo)(limit = 2)
        assertEquals(listOf("B", "C"), shortlist.map { it.photo.title }) // sorted desc

        val rejected = BuildRejectedListUseCase(babyRepo, aiRepo)(shortlistLimit = 2)
        assertEquals(listOf("A"), rejected.map { it.photo.title })
    }

    @Test
    fun `clustering demotes the weaker near-duplicate`() = runBlocking {
        val strong = insertPhoto("strong"); val weak = insertPhoto("weak"); val unique = insertPhoto("unique")
        // strong & weak share a dHash → duplicates; unique is far away.
        // Dimensions are consistent with the stored score so demote genuinely lowers it.
        aiRepo.saveAnalyses(listOf(
            analysis(strong, 90f, dHash = 0L, dim = 0.9f),
            analysis(weak, 50f, dHash = 0L, dim = 0.5f),
            analysis(unique, 85f, dHash = (-1L), dim = 0.9f) // 64 bits different
        ))

        val groups = ClusterDuplicatesUseCase(aiRepo)(nowMillis = 1L)
        assertEquals(1, groups) // one duplicate group

        // weaker duplicate got penalized + score recomputed lower; representative untouched.
        val weakAfter = aiRepo.getAnalysis(weak)!!
        assertTrue(weakAfter.duplicatePenalty > 0f)
        assertTrue("demoted score should drop below its prior 50", weakAfter.overallScore < 50f)
        assertEquals(0f, aiRepo.getAnalysis(strong)!!.duplicatePenalty, 0.001f)
        assertEquals(90f, aiRepo.getAnalysis(strong)!!.overallScore, 0.001f)
    }

    @Test
    fun `explain top photos caches a korean reason`() = runBlocking {
        val a = insertPhoto("A")
        aiRepo.saveAnalysis(analysis(a, 95f))
        val count = ExplainTopPhotosUseCase(babyRepo, aiRepo, RuleBasedVisionExplainer())(limit = 5)
        assertEquals(1, count)
        assertNotNull(aiRepo.getAnalysis(a)!!.aiReasonKo)
    }

    @Test
    fun `analyze skips already-analyzed photos`() = runBlocking {
        val a = insertPhoto("A"); val b = insertPhoto("B")
        aiRepo.saveAnalysis(analysis(a, 90f)) // a already analyzed

        val fake = object : PhotoAnalyzer {
            var calls = 0
            override suspend fun analyze(photoId: Int, uri: Uri, sleepingModeEnabled: Boolean, nowMillis: Long): PhotoAnalysisEntity {
                calls++
                return analysis(photoId, 75f)
            }
        }

        val analyzed = AnalyzePhotoQualityUseCase(aiRepo, fake)(
            photos = listOf(BabyPhoto(id = a, uriString = "content://A", title = "A", description = "", takenDate = "2026-05-01"),
                BabyPhoto(id = b, uriString = "content://B", title = "B", description = "", takenDate = "2026-05-01")),
            sleepingModeEnabled = false,
            nowMillis = 1L
        )
        assertEquals(1, analyzed)        // only b analyzed
        assertEquals(1, fake.calls)
        assertFalse(aiRepo.getAnalyzedPhotoIds().isEmpty())
        assertNotNull(aiRepo.getAnalysis(b))
    }
}
