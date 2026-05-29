package com.bebecup.app

import com.bebecup.app.ai.BabyPhotoScorePolicy
import com.bebecup.app.ai.DuplicateClusterer
import com.bebecup.app.ai.DuplicatePenaltyPolicy
import com.bebecup.app.ai.PerceptualHash
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.PhotoScoreInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateGroupingTest {

    // --- PerceptualHash ---

    private fun gradientLuma(w: Int, h: Int, shift: Int = 0): IntArray =
        IntArray(w * h) { i -> (((i % w) + shift) * 255 / w).coerceIn(0, 255) }

    @Test
    fun `identical images hash identically, hamming zero`() {
        val a = PerceptualHash.dHashFromLuma(gradientLuma(32, 32), 32, 32)
        val b = PerceptualHash.dHashFromLuma(gradientLuma(32, 32), 32, 32)
        assertEquals(a, b)
        assertEquals(0, PerceptualHash.hammingDistance(a, b))
    }

    @Test
    fun `very different images hash far apart`() {
        val gradient = PerceptualHash.dHashFromLuma(gradientLuma(32, 32), 32, 32)
        // Reverse gradient → opposite horizontal differences.
        val reversed = PerceptualHash.dHashFromLuma(
            IntArray(32 * 32) { i -> 255 - (((i % 32)) * 255 / 32) }, 32, 32
        )
        assertNotEquals(gradient, reversed)
        assertTrue(PerceptualHash.hammingDistance(gradient, reversed) > 20)
    }

    // --- Clusterer ---

    @Test
    fun `near-duplicate hashes cluster together with highest-score representative`() {
        val base = 0b0L
        val items = listOf(
            DuplicateClusterer.Item(photoId = 1, dHash = base, score = 70f),
            DuplicateClusterer.Item(photoId = 2, dHash = base or 0b111L, score = 95f), // 3 bits off → same cluster
            DuplicateClusterer.Item(photoId = 3, dHash = base.inv(), score = 80f)       // all 64 bits off → separate
        )
        val clusters = DuplicateClusterer.cluster(items, hammingThreshold = 10)
        assertEquals(2, clusters.size)

        val dupGroup = clusters.first { it.memberIds.containsAll(listOf(1, 2)) }
        assertTrue(dupGroup.memberIds.contains(1) && dupGroup.memberIds.contains(2))
        assertEquals(2, dupGroup.representativeId) // highest score (95)

        val singleton = clusters.first { it.memberIds == listOf(3) }
        assertEquals(3, singleton.representativeId)
    }

    // --- Demotion ---

    @Test
    fun `demoting a duplicate lowers its overall score and sets penalty`() {
        val strongInput = PhotoScoreInput(
            blurScore = 1f, faceDetected = true, faceCenterScore = 1f, eyeOpenScore = 1f,
            expressionScore = 1f, exposureScore = 1f, compositionScore = 1f, uniquenessScore = 1f
        )
        val original = PhotoAnalysisEntity(
            photoId = 7,
            analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
            blurScore = 1f, faceDetected = true, faceCount = 1, faceCenterScore = 1f,
            eyeOpenScore = 1f, expressionScore = 1f, exposureScore = 1f, compositionScore = 1f,
            duplicatePenalty = 0f,
            overallScore = BabyPhotoScorePolicy.calculateOverallScore(strongInput),
            qualityGrade = "S",
            dHash = 0L,
            rejectReasonsJson = "[]",
            positiveReasonsJson = "[]",
            analyzedAtMillis = 0L
        )

        val demoted = DuplicatePenaltyPolicy.demote(original)
        assertTrue("score should drop", demoted.overallScore < original.overallScore)
        assertEquals(DuplicatePenaltyPolicy.DUPLICATE_PENALTY, demoted.duplicatePenalty, 0.001f)
        // uniqueness weight is 0.05, so a perfect photo loses 5 points.
        assertEquals(95f, demoted.overallScore, 0.001f)
    }
}
