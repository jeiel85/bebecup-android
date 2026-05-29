package com.bebecup.app

import com.bebecup.app.ai.RuleBasedVisionExplainer
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.AiPhotoExplanation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedVisionExplainerTest {

    private fun analysis(score: Float, blur: Float = 0.9f, eye: Float = 0.9f, expr: Float = 0.9f) =
        PhotoAnalysisEntity(
            photoId = 1,
            analysisVersion = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION,
            blurScore = blur, faceDetected = true, faceCount = 1, faceCenterScore = 0.9f,
            eyeOpenScore = eye, expressionScore = expr, exposureScore = 0.9f, compositionScore = 0.9f,
            duplicatePenalty = 0f, overallScore = score, qualityGrade = "S", dHash = 0L,
            rejectReasonsJson = "[]", positiveReasonsJson = "[]", analyzedAtMillis = 0L
        )

    @Test
    fun `strong photo gets a warm recommend sentence from rule-based fallback`() = runBlocking {
        val explainer = RuleBasedVisionExplainer()
        val result = explainer.explain("생일 미소", analysis(score = 95f))
        assertEquals(AiPhotoExplanation.Source.RULE_BASED, result.source)
        assertTrue(result.summaryKo.contains("선명"))
        assertTrue(result.summaryKo.endsWith("추천해요."))
    }

    @Test
    fun `low-quality photo gets a gentle check sentence, never harsh`() = runBlocking {
        val explainer = RuleBasedVisionExplainer()
        val result = explainer.explain("", analysis(score = 40f, blur = 0.2f, eye = 0.2f, expr = 0.2f))
        assertTrue(result.summaryKo.endsWith("확인해보시면 좋아요."))
        listOf("나쁜", "실패", "못", "별로").forEach {
            assertTrue("must not contain harsh word: $it", !result.summaryKo.contains(it))
        }
    }
}
