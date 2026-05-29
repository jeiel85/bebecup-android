package com.bebecup.app

import com.bebecup.app.ai.PhotoReasonBuilder
import com.bebecup.app.domain.model.PhotoScoreInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoReasonBuilderTest {

    private fun input(
        blur: Float = 0.9f,
        eye: Float = 0.9f,
        expression: Float = 0.9f,
        faceCenter: Float = 0.9f,
        uniqueness: Float = 0.9f,
        exposure: Float = 0.9f,
        sleeping: Boolean = false
    ) = PhotoScoreInput(
        blurScore = blur,
        faceDetected = true,
        faceCenterScore = faceCenter,
        eyeOpenScore = eye,
        expressionScore = expression,
        exposureScore = exposure,
        compositionScore = 0.9f,
        uniquenessScore = uniqueness,
        sleepingModeEnabled = sleeping
    )

    @Test
    fun `strong photo lists positive reasons`() {
        val reasons = PhotoReasonBuilder.buildPositiveReasons(input())
        assertTrue(reasons.contains("얼굴이 선명하게 보여요"))
        assertTrue(reasons.contains("눈을 또렷하게 뜨고 있어요"))
        assertTrue(reasons.contains("비슷한 사진 중에서도 가장 좋은 후보예요"))
    }

    @Test
    fun `blurry dark photo lists reject reasons`() {
        val reasons = PhotoReasonBuilder.buildRejectReasons(
            input(blur = 0.2f, exposure = 0.2f), duplicatePenalty = 0.6f
        )
        assertTrue(reasons.contains("얼굴 부분이 조금 흔들렸어요"))
        assertTrue(reasons.contains("사진이 조금 어둡거나 밝게 찍혔어요"))
        assertTrue(reasons.contains("비슷한 사진 중 더 선명한 후보가 있어요"))
    }

    @Test
    fun `closed eyes are not flagged in sleeping mode`() {
        val awake = PhotoReasonBuilder.buildRejectReasons(input(eye = 0.1f, sleeping = false), 0f)
        val sleeping = PhotoReasonBuilder.buildRejectReasons(input(eye = 0.1f, sleeping = true), 0f)
        assertTrue(awake.contains("활동 사진 기준으로는 눈이 감겨 있어요"))
        assertFalse(sleeping.contains("활동 사진 기준으로는 눈이 감겨 있어요"))
    }
}
