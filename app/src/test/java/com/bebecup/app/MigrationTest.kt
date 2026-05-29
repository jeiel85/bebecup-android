package com.bebecup.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.data.ALL_MIGRATIONS
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Validates the real v2 → v4 upgrade path preserves user data and ends with a
 * schema Room accepts. We build a genuine v2 database by hand (the original
 * tournament-app schema), then open it through Room with [ALL_MIGRATIONS];
 * Room runs the migrations and validates the result against the compiled v4
 * schema, so any SQL mismatch fails this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "baby_photo_worldcup_database"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createV2Database() {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `baby_photos` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uriString` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT NOT NULL, `takenDate` TEXT NOT NULL, " +
                "`isSelectedAsBest` INTEGER NOT NULL, `bestSelectedTimestamp` INTEGER NOT NULL, " +
                "`winsCount` INTEGER NOT NULL, `matchesCount` INTEGER NOT NULL, " +
                "`isInPrintCart` INTEGER NOT NULL, `printQuantity` INTEGER NOT NULL, `printSize` TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tournament_records` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `winnerPhotoId` INTEGER NOT NULL, " +
                "`winnerTitle` TEXT NOT NULL, `winnerUriString` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, " +
                "`totalParticipants` INTEGER NOT NULL, `tournamentTitle` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO baby_photos " +
                "(uriString,title,description,takenDate,isSelectedAsBest,bestSelectedTimestamp," +
                "winsCount,matchesCount,isInPrintCart,printQuantity,printSize) " +
                "VALUES ('content://legacy/1','우리 아기','첫 미소','2026-05-01',1,123456,2,5,0,1,'4x6')"
        )
        db.version = 2
        db.close()
    }

    @Test
    fun `v2 to v4 migration preserves baby photos and enables AI tables`() = runBlocking {
        createV2Database()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Legacy data survived the upgrade.
        val photo = room.babyPhotoDao().getPhotoById(1)
        assertNotNull(photo)
        assertEquals("우리 아기", photo!!.title)
        assertTrue(photo.isSelectedAsBest)
        assertEquals(2, photo.winsCount)

        // New AI tables exist and the dHash column (added in v4) works.
        room.photoAnalysisDao().insert(
            PhotoAnalysisEntity(
                photoId = 1,
                analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
                blurScore = 0.9f, faceDetected = true, faceCount = 1, faceCenterScore = 0.9f,
                eyeOpenScore = 0.9f, expressionScore = 0.9f, exposureScore = 0.9f, compositionScore = 0.9f,
                duplicatePenalty = 0f, overallScore = 95f, qualityGrade = "S", dHash = 42L,
                rejectReasonsJson = "[]", positiveReasonsJson = "[]", analyzedAtMillis = 1L
            )
        )
        val analyzed = room.photoAnalysisDao().getAnalyzedPhotoIds(PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION)
        assertEquals(listOf(1), analyzed)

        room.close()
    }
}
