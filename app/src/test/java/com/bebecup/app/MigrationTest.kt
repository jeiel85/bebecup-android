package com.bebecup.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.bebecup.app.data.ALL_MIGRATIONS
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.MIGRATION_6_7
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.FaceReferenceEntity
import com.bebecup.app.data.children.FaceState
import com.bebecup.app.data.children.PhotoChildRow
import com.bebecup.app.data.children.PhotoFaceEntity
import kotlinx.coroutines.flow.first
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
 * Validates the real v2 → v5 upgrade path preserves user data and ends with a
 * schema Room accepts. We build a genuine v2 database by hand (the original
 * tournament-app schema), then open it through Room with [ALL_MIGRATIONS];
 * Room runs the migrations and validates the result against the compiled v5
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
        db.execSQL(
            "INSERT INTO tournament_records " +
                "(winnerPhotoId,winnerTitle,winnerUriString,timestamp,totalParticipants,tournamentTitle) " +
                "VALUES (1,'우리 아기','content://legacy/1',123456,8,'베스트 아기사진 이상형 월드컵')"
        )
        db.version = 2
        db.close()
    }

    @Test
    fun `v2 to v5 migration preserves photos and cups and adds the new columns`() = runBlocking {
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

        // The old cup survived and is not a 이달의 월드컵 (monthKey added in v5).
        val cups = room.babyPhotoDao().getAllTournaments().first()
        assertEquals(1, cups.size)
        assertEquals(8, cups.single().totalParticipants)
        assertNull(cups.single().monthKey)
        assertEquals(emptyList<Any>(), room.babyPhotoDao().getMonthlyCups())
        assertNull(cups.single().childId)

        room.close()
    }

    @Test
    fun `v6 adds the children and face tables and marks old photos as not looked at`() = runBlocking {
        createV2Database()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val dao = room.childrenDao()

        assertEquals(0, room.babyPhotoDao().getPhotoById(1)!!.faceIdVersion)
        assertEquals(1, dao.getKeptPhotosWithoutFaces(1).size)

        val childId = dao.insertChild(ChildEntity(name = "하린", birthDate = "2026-01-31", createdAtMillis = 1L))
        dao.replaceFaces(
            photoId = 1,
            faces = listOf(
                PhotoFaceEntity(
                    photoId = 1, boxLeft = 0.1f, boxTop = 0.1f, boxRight = 0.4f, boxBottom = 0.4f,
                    embedding = ByteArray(512), childId = childId, state = FaceState.MANUAL, modelVersion = 1
                )
            ),
            version = 1
        )
        dao.insertReferences(listOf(FaceReferenceEntity(childId = childId, embedding = ByteArray(512), sourceFaceId = 1L, createdAtMillis = 1L, modelVersion = 1)))
        assertEquals(listOf(PhotoChildRow(photoId = 1, childId = childId)), dao.observePhotoChildren().first())
        assertEquals(emptyList<Any>(), dao.getKeptPhotosWithoutFaces(1))

        // Deleting a child makes its faces unknown again and drops its named examples.
        dao.deleteChild(childId)
        val face = dao.getFacesInPhoto(1).single().face
        assertNull(face.childId)
        assertEquals(FaceState.UNKNOWN, face.state)
        assertEquals(emptyList<Any>(), dao.getReferences(1))

        room.close()
    }

    @Test
    fun `v7 gives v0_12 child cups their child back from the title`() = runBlocking {
        createV2Database()
        // Bring the v2 file to v6 with the real migrations, then add v0.12.0 data.
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        ALL_MIGRATIONS.filter { it.endVersion <= 6 }.forEach { it.migrate(db) }
                    }
                })
                .build()
        )
        helper.writableDatabase.apply {
            // 윤 is the end of 도윤: it must not claim 도윤's cups.
            execSQL("INSERT INTO children (id, name, birthDate, createdAtMillis) VALUES (1, '하린', '', 1), (2, '도윤', '', 2), (3, '윤', '', 3)")
            fun cup(title: String, month: String?) = execSQL(
                "INSERT INTO tournament_records " +
                    "(winnerPhotoId, winnerTitle, winnerUriString, timestamp, totalParticipants, tournamentTitle, monthKey) " +
                    "VALUES (1, '우리 아기', 'content://legacy/1', 1, 4, ?, ?)",
                arrayOf(title, month)
            )
            cup("하린 이상형 월드컵", null)
            cup("2026년 10월의 도윤 월드컵", "2026-10")
            cup("2026년 10월의 월드컵", "2026-10")
            cup("윤 이상형 월드컵", null)
        }
        helper.close()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val childByTitle = room.babyPhotoDao().getAllTournaments().first().associate { it.tournamentTitle to it.childId }
        assertEquals(
            mapOf(
                "베스트 아기사진 이상형 월드컵" to null,
                "하린 이상형 월드컵" to 1L,
                "2026년 10월의 도윤 월드컵" to 2L,
                "2026년 10월의 월드컵" to null,
                "윤 이상형 월드컵" to 3L
            ),
            childByTitle
        )
        room.close()
    }
}
