package com.bebecup.app

import com.bebecup.app.ai.faceid.FaceAlignment
import com.bebecup.app.ai.faceid.FacePoint
import com.bebecup.app.ai.faceid.FaceVectors
import com.bebecup.app.domain.children.FaceMatcher
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FaceMatcherTest {

    private val rng = Random(42)

    private fun randomUnit(): FloatArray = FaceVectors.normalized(FloatArray(FaceVectors.DIMENSIONS) { rng.nextFloat() * 2 - 1 })

    /** A vector whose cosine with unit [base] is about [similarity]. */
    private fun near(base: FloatArray, similarity: Float): FloatArray {
        val noise = randomUnit()
        // Remove the part along base, so noise is orthogonal to it.
        val dot = FaceVectors.cosine(noise, base)
        val ortho = FaceVectors.normalized(FloatArray(base.size) { noise[it] - dot * base[it] })
        val s = similarity
        val c = kotlin.math.sqrt(1 - s * s)
        return FaceVectors.normalized(FloatArray(base.size) { s * base[it] + c * ortho[it] })
    }

    @Test
    fun `vectors survive the byte round trip and foreign sizes are refused`() {
        val v = randomUnit()
        assertArrayEquals(v, FaceVectors.fromBytes(FaceVectors.toBytes(v)), 0f)
        assertNull(FaceVectors.fromBytes(ByteArray(10)))
    }

    @Test
    fun `a face clearly like one child is matched to that child`() {
        val harin = randomUnit()
        val doyun = randomUnit()
        val refs = mapOf(1L to List(3) { near(harin, 0.8f) }, 2L to List(3) { near(doyun, 0.8f) })
        val match = FaceMatcher.bestMatch(near(harin, 0.8f), refs)
        assertNotNull(match)
        assertEquals(1L, match!!.person)
    }

    @Test
    fun `a face too unlike everyone stays unknown`() {
        val refs = mapOf(1L to listOf(randomUnit()), 2L to listOf(randomUnit()))
        assertNull(FaceMatcher.bestMatch(randomUnit(), refs))
    }

    @Test
    fun `a face about as close to both siblings stays unknown instead of being guessed`() {
        val shared = randomUnit()
        // Two siblings who look much alike: their examples are all near one face.
        val refs = mapOf(1L to List(3) { near(shared, 0.75f) }, 2L to List(3) { near(shared, 0.75f) })
        assertNull(FaceMatcher.bestMatch(near(shared, 0.9f), refs))
    }

    @Test
    fun `a child is matched at most once per photo, to the closer face`() {
        val harin = randomUnit()
        val refs = mapOf(1L to List(3) { near(harin, 0.85f) })
        val close = near(harin, 0.85f)
        val farther = near(harin, 0.6f)
        val matches = FaceMatcher.assignPhoto(listOf(farther, close), refs)
        assertNull(matches[0])
        assertEquals(1L, matches[1]!!.person)
    }

    @Test
    fun `not-a-child examples can match several faces in one photo`() {
        val parent = randomUnit()
        val refs = mapOf(FaceMatcher.NOT_CHILD to List(3) { near(parent, 0.85f) })
        val matches = FaceMatcher.assignPhoto(listOf(near(parent, 0.85f), near(parent, 0.85f)), refs)
        assertEquals(listOf(FaceMatcher.NOT_CHILD, FaceMatcher.NOT_CHILD), matches.map { it?.person })
    }

    @Test
    fun `several different adults marked not-a-child are each still recognised`() {
        val adults = List(3) { randomUnit() }
        // One example per adult, all under "not a child".
        val refs = mapOf(FaceMatcher.NOT_CHILD to adults.map { near(it, 0.9f) }, 1L to List(3) { near(randomUnit(), 0.8f) })
        adults.forEach { adult ->
            assertEquals(FaceMatcher.NOT_CHILD, FaceMatcher.bestMatch(near(adult, 0.9f), refs)?.person)
        }
    }

    @Test
    fun `unknown faces are grouped by person, largest group first`() {
        val a = randomUnit()
        val b = randomUnit()
        val faces = listOf(near(a, 0.8f), near(b, 0.8f), near(a, 0.8f), near(a, 0.8f), near(b, 0.8f), randomUnit())
        val groups = FaceMatcher.cluster(faces)
        assertEquals(listOf(0, 2, 3), groups[0])
        assertEquals(listOf(1, 4), groups[1])
        assertEquals(listOf(listOf(5)), groups.drop(2))
    }

    @Test
    fun `alignment recovers a rotated, scaled and moved face whatever order the points come in`() {
        val template = listOf(FaceAlignment.EYE_IMAGE_LEFT, FaceAlignment.EYE_IMAGE_RIGHT, FaceAlignment.MOUTH_IMAGE_LEFT, FaceAlignment.MOUTH_IMAGE_RIGHT)
        // Upright, tilted, and lying on its side (a baby photographed from above).
        for (angle in listOf(0.0, 0.3, -0.6, Math.PI / 2, Math.PI)) {
            val scale = 2.5f
            fun inPhoto(p: FacePoint) = FacePoint(
                (scale * (cos(angle) * p.x - sin(angle) * p.y) + 40f).toFloat(),
                (scale * (sin(angle) * p.x + cos(angle) * p.y) - 12f).toFloat()
            )
            val (el, er, ml, mr) = template.map(::inPhoto)
            // The detector's naming must not matter: give the points swapped.
            for (t in listOf(FaceAlignment.forFace(el, er, ml, mr)!!, FaceAlignment.forFace(er, el, mr, ml)!!)) {
                template.forEach { p ->
                    val back = t.map(inPhoto(p))
                    assertEquals("angle $angle", p.x, back.x, 0.01f)
                    assertEquals("angle $angle", p.y, back.y, 0.01f)
                }
            }
        }
    }

    @Test
    fun `alignment refuses landmarks that all sit on one point`() {
        val p = FacePoint(10f, 10f)
        assertNull(FaceAlignment.forFace(p, p, p, p))
    }
}
