package com.bebecup.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bebecup.app.ai.faceid.FaceSample
import com.bebecup.app.ai.faceid.FaceVectors
import com.bebecup.app.ai.faceid.PhotoFaceReader
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.FaceState
import com.bebecup.app.data.children.FamilyFaces
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two children in one family: once the parent names one photo of each, the
 * other photos are sorted by child on their own, and nothing the parent
 * decided is ever changed by the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyFacesTest {

    private lateinit var db: AppDatabase
    private val rng = Random(7)
    private val harin = unit()
    private val doyun = unit()
    private val mom = unit()

    /** photo uri → faces the fake reader "finds" in it. */
    private val photoFaces = mutableMapOf<String, List<FloatArray>>()
    private var reads = 0

    private val reader = object : PhotoFaceReader {
        override suspend fun read(uri: Uri): List<FaceSample>? {
            reads++
            return photoFaces[uri.toString()]?.mapIndexed { i, v ->
                FaceSample(0.1f * i, 0.1f, 0.1f * i + 0.2f, 0.3f, v)
            }
        }
    }

    private lateinit var faces: FamilyFaces

    private fun unit() = FaceVectors.normalized(FloatArray(FaceVectors.DIMENSIONS) { rng.nextFloat() * 2 - 1 })

    private fun near(base: FloatArray, s: Float = 0.8f): FloatArray {
        val noise = unit()
        val dot = FaceVectors.cosine(noise, base)
        val ortho = FaceVectors.normalized(FloatArray(base.size) { noise[it] - dot * base[it] })
        val c = kotlin.math.sqrt(1 - s * s)
        return FaceVectors.normalized(FloatArray(base.size) { s * base[it] + c * ortho[it] })
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        faces = FamilyFaces(db.childrenDao(), reader = { reader }, now = { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun keptPhoto(name: String, vararg found: FloatArray): BabyPhoto {
        val uri = "content://test/$name"
        photoFaces[uri] = found.toList()
        val id = db.babyPhotoDao().insertPhoto(BabyPhoto(uriString = uri, title = name, description = "", takenDate = "2026-05-01")).toInt()
        db.babyPhotoDao().markBest(listOf(id), 1L)
        return db.babyPhotoDao().getPhotoById(id)!!
    }

    private suspend fun childrenOf(photo: BabyPhoto): Set<Long> =
        db.childrenDao().observePhotoChildren().first().filter { it.photoId == photo.id }.map { it.childId }.toSet()

    @Test
    fun `naming one face of each child sorts the other photos by child`() = runBlocking {
        val dao = db.childrenDao()
        val harinId = dao.insertChild(ChildEntity(name = "하린", createdAtMillis = 1L))
        val doyunId = dao.insertChild(ChildEntity(name = "도윤", createdAtMillis = 2L))
        val h1 = keptPhoto("h1", near(harin))
        val h2 = keptPhoto("h2", near(harin))
        val h3 = keptPhoto("h3", near(harin))
        val d1 = keptPhoto("d1", near(doyun))
        val d2 = keptPhoto("d2", near(doyun))
        val both = keptPhoto("both", near(harin), near(doyun))

        assertEquals(6, faces.identify(faces.keptPhotosToLookAt()))
        // Nobody named yet: everything is unknown, grouped into the two children.
        val groups = faces.unknownGroups()
        assertEquals(listOf(4, 3), groups.map { it.faces.size })

        faces.name(groups[0].faceIds, harinId)
        faces.name(groups[1].faceIds, doyunId)
        assertEquals(setOf(harinId), childrenOf(h1))
        assertEquals(setOf(doyunId), childrenOf(d2))
        assertEquals(setOf(harinId, doyunId), childrenOf(both))
        assertTrue(faces.unknownGroups().isEmpty())

        // A new photo of 도윤 is sorted on its own, without asking.
        val d3 = keptPhoto("d3", near(doyun))
        faces.identify(faces.keptPhotosToLookAt())
        assertEquals(setOf(doyunId), childrenOf(d3))
        val face = faces.facesIn(d3.id).single().face
        assertEquals(FaceState.AUTO, face.state)

        // Photos already looked at are not read again.
        val readsBefore = reads
        faces.identify(listOf(db.babyPhotoDao().getPhotoById(h2.id)!!, db.babyPhotoDao().getPhotoById(h3.id)!!))
        assertEquals(readsBefore, reads)
        assertEquals(setOf(doyunId), childrenOf(d1))
    }

    @Test
    fun `the parent's choice is never changed and a parent's face stops being asked about`() = runBlocking {
        val dao = db.childrenDao()
        val harinId = dao.insertChild(ChildEntity(name = "하린", createdAtMillis = 1L))
        val doyunId = dao.insertChild(ChildEntity(name = "도윤", createdAtMillis = 2L))
        val withMom = keptPhoto("mom1", near(harin), near(mom))
        keptPhoto("h1", near(harin))
        keptPhoto("mom2", near(mom))
        faces.identify(faces.keptPhotosToLookAt())

        val momFaces = faces.unknownGroups().first { g -> g.faces.any { it.face.photoId == withMom.id && it.face.boxLeft > 0f } }
        faces.name(momFaces.faceIds, null) // 아이가 아니에요
        val harinFaces = faces.unknownGroups().single()
        faces.name(harinFaces.faceIds, harinId)
        assertEquals(setOf(harinId), childrenOf(withMom))

        // The parent says this 하린-looking face is 도윤 after all; matching again keeps it.
        val harinFace = faces.facesIn(withMom.id).first { it.face.childId == harinId }.face
        faces.name(listOf(harinFace.id), doyunId)
        faces.rematch()
        assertEquals(setOf(doyunId), childrenOf(withMom))

        // Mom shows up again in a new photo: matched as "not a child", not asked about.
        val mom3 = keptPhoto("mom3", near(mom))
        faces.identify(faces.keptPhotosToLookAt())
        val face = faces.facesIn(mom3.id).single().face
        assertNull(face.childId)
        assertEquals(FaceState.AUTO, face.state)
        assertTrue(faces.unknownGroups(minFaces = 1).none { g -> g.faces.any { it.face.photoId == mom3.id } })
    }

    @Test
    fun `an automatic match written late never replaces the parent's choice`() = runBlocking {
        val dao = db.childrenDao()
        val harinId = dao.insertChild(ChildEntity(name = "하린", createdAtMillis = 1L))
        val doyunId = dao.insertChild(ChildEntity(name = "도윤", createdAtMillis = 2L))
        val photo = keptPhoto("p", near(harin))
        faces.identify(faces.keptPhotosToLookAt())
        val face = faces.facesIn(photo.id).single().face
        faces.name(listOf(face.id), doyunId)
        // A rematch that read the face before the parent named it now writes its older answer.
        dao.setUndecidedFaces(listOf(face.id), harinId, FaceState.AUTO)
        val after = faces.facesIn(photo.id).single().face
        assertEquals(doyunId, after.childId)
        assertEquals(FaceState.MANUAL, after.state)
    }

    @Test
    fun `deleting a child makes its faces unknown again`() = runBlocking {
        val dao = db.childrenDao()
        val harinId = dao.insertChild(ChildEntity(name = "하린", createdAtMillis = 1L))
        keptPhoto("h1", near(harin))
        keptPhoto("h2", near(harin))
        faces.identify(faces.keptPhotosToLookAt())
        faces.name(faces.unknownGroups().single().faceIds, harinId)

        dao.deleteChild(harinId)
        faces.rematch()
        assertEquals(1, faces.unknownGroups().size)
        assertTrue(dao.observePhotoChildren().first().isEmpty())
    }
}
