package com.bebecup.app

import com.bebecup.app.ai.BabyPhotoScorePolicy
import com.bebecup.app.domain.model.PhotoQualityGrade
import com.bebecup.app.domain.model.PhotoScoreInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BabyPhotoScorePolicyTest {

    private fun input(
        all: Float = 1f,
        faceDetected: Boolean = true
    ) = PhotoScoreInput(
        blurScore = all,
        faceDetected = faceDetected,
        faceCenterScore = all,
        eyeOpenScore = all,
        expressionScore = all,
        exposureScore = all,
        compositionScore = all,
        uniquenessScore = all
    )

    @Test
    fun `perfect input scores 100 and grade S`() {
        val score = BabyPhotoScorePolicy.calculateOverallScore(input(all = 1f))
        assertEquals(100f, score, 0.001f)
        assertEquals(PhotoQualityGrade.S, PhotoQualityGrade.fromScore(score))
    }

    @Test
    fun `zero input scores 0 and grade D`() {
        val score = BabyPhotoScorePolicy.calculateOverallScore(input(all = 0f))
        assertEquals(0f, score, 0.001f)
        assertEquals(PhotoQualityGrade.D, PhotoQualityGrade.fromScore(score))
    }

    @Test
    fun `no face applies the demotion multiplier`() {
        val withFace = BabyPhotoScorePolicy.calculateOverallScore(input(all = 1f, faceDetected = true))
        val noFace = BabyPhotoScorePolicy.calculateOverallScore(input(all = 1f, faceDetected = false))
        assertEquals(65f, noFace, 0.001f) // 100 * 0.65
        assertTrue(noFace < withFace)
    }

    @Test
    fun `grade boundaries match spec bands`() {
        assertEquals(PhotoQualityGrade.S, PhotoQualityGrade.fromScore(90f))
        assertEquals(PhotoQualityGrade.A, PhotoQualityGrade.fromScore(80f))
        assertEquals(PhotoQualityGrade.B, PhotoQualityGrade.fromScore(70f))
        assertEquals(PhotoQualityGrade.C, PhotoQualityGrade.fromScore(60f))
        assertEquals(PhotoQualityGrade.D, PhotoQualityGrade.fromScore(59.9f))
    }
}
