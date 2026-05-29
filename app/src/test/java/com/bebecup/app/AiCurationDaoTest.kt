package com.bebecup.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.ai.AiCurationSessionEntity
import com.bebecup.app.data.ai.CurationStatus
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the Phase 2 AI-curation schema (v3): analysis cache with FK to
 * baby_photos, top-by-score ordering, session lifecycle, and the privacy
 * "delete all local analysis data" path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiCurationDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun analysis(photoId: Int, score: Float, grade: String) = PhotoAnalysisEntity(
        photoId = photoId,
        analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
        blurScore = 0.8f,
        faceDetected = true,
        faceCount = 1,
        faceCenterScore = 0.7f,
        eyeOpenScore = 0.9f,
        expressionScore = 0.8f,
        exposureScore = 0.7f,
        compositionScore = 0.6f,
        duplicatePenalty = 0.0f,
        overallScore = score,
        qualityGrade = grade,
        rejectReasonsJson = "[]",
        positiveReasonsJson = "[\"선명해요\"]",
        analyzedAtMillis = 1_000L
    )

    @Test
    fun `analysis cache stores and returns top photos by score`() = runBlocking {
        val photoDao = db.babyPhotoDao()
        val analysisDao = db.photoAnalysisDao()

        val idA = photoDao.insertPhoto(BabyPhoto(uriString = "content://a", title = "A", description = "", takenDate = "2026-05-01")).toInt()
        val idB = photoDao.insertPhoto(BabyPhoto(uriString = "content://b", title = "B", description = "", takenDate = "2026-05-02")).toInt()

        analysisDao.insertAll(
            listOf(
                analysis(idA, 72f, "B"),
                analysis(idB, 91f, "S")
            )
        )

        val version = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION
        val top = analysisDao.getTopByScore(version, limit = 1)
        assertEquals(1, top.size)
        assertEquals(idB, top.first().photoId)

        val analyzedIds = analysisDao.getAnalyzedPhotoIds(version)
        assertTrue(analyzedIds.containsAll(listOf(idA, idB)))

        assertNotNull(analysisDao.getForPhoto(idA, version))
    }

    @Test
    fun `deleting a baby photo cascades to its analysis`() = runBlocking {
        val photoDao = db.babyPhotoDao()
        val analysisDao = db.photoAnalysisDao()

        val photo = BabyPhoto(uriString = "content://c", title = "C", description = "", takenDate = "2026-05-03")
        val id = photoDao.insertPhoto(photo).toInt()
        analysisDao.insert(analysis(id, 80f, "A"))

        photoDao.deletePhoto(photo.copy(id = id))

        val version = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION
        assertNull(analysisDao.getForPhoto(id, version))
    }

    @Test
    fun `session lifecycle and delete-all wipe local data`() = runBlocking {
        val analysisDao = db.photoAnalysisDao()
        val sessionDao = db.aiCurationSessionDao()
        val photoDao = db.babyPhotoDao()

        val sessionId = sessionDao.insert(
            AiCurationSessionEntity(
                scanRangeStartMillis = 0L,
                scanRangeEndMillis = 100L,
                status = CurationStatus.CREATED,
                createdAtMillis = 10L
            )
        ).toInt()

        val stored = sessionDao.getById(sessionId)
        assertNotNull(stored)
        sessionDao.update(stored!!.copy(status = CurationStatus.COMPLETED, completedAtMillis = 20L))
        assertEquals(CurationStatus.COMPLETED, sessionDao.getById(sessionId)?.status)

        val id = photoDao.insertPhoto(BabyPhoto(uriString = "content://d", title = "D", description = "", takenDate = "2026-05-04")).toInt()
        analysisDao.insert(analysis(id, 60f, "C"))

        analysisDao.deleteAll()
        sessionDao.deleteAll()

        assertTrue(analysisDao.getAnalyzedPhotoIds(PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION).isEmpty())
        assertNull(sessionDao.getById(sessionId))
    }
}
