package com.bebecup.app.ai

import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.AiPhotoExplanation
import com.bebecup.app.domain.model.PhotoQualityGrade

/**
 * Produces a natural-language Korean explanation for a photo (spec §8.10).
 *
 * The domain layer depends only on this interface — never on a concrete model
 * (Qwen / MiniCPM / Gemma …). A local VLM implementation can be dropped in
 * later behind [com.bebecup.app.ai.AiModelAvailability] without touching
 * callers; the app must remain fully usable without any VLM.
 */
interface LocalVisionExplainer {
    suspend fun explain(photoTitle: String, analysis: PhotoAnalysisEntity): AiPhotoExplanation
}

/**
 * Deterministic, dependency-free explainer used when no local VLM is available
 * (the MVP/commercial default). Composes a warm sentence from the grade and the
 * already-computed positive reasons. Never describes the baby harshly (§17).
 */
class RuleBasedVisionExplainer : LocalVisionExplainer {

    override suspend fun explain(photoTitle: String, analysis: PhotoAnalysisEntity): AiPhotoExplanation =
        AiPhotoExplanation(summaryKo = build(analysis), source = AiPhotoExplanation.Source.RULE_BASED)

    private fun build(analysis: PhotoAnalysisEntity): String {
        val grade = PhotoQualityGrade.fromScore(analysis.overallScore)
        val highlights = mutableListOf<String>()
        if (analysis.blurScore >= 0.6f) highlights += "선명하고"
        if (analysis.eyeOpenScore >= 0.6f) highlights += "눈이 또렷하며"
        if (analysis.expressionScore >= 0.6f) highlights += "표정이 자연스러워"
        if (analysis.faceCenterScore >= 0.6f && highlights.size < 3) highlights += "얼굴이 중앙에 담겨"

        val body = if (highlights.isEmpty()) {
            "전체적인 분위기가 좋아"
        } else {
            highlights.joinToString(" ")
        }

        return when {
            grade.isStronglyRecommended -> "$body 이번 주 베스트 후보로 추천해요."
            grade.isBackup -> "$body 백업 후보로 담아두기 좋아요."
            else -> "$body 한 번 더 확인해보시면 좋아요."
        }
    }
}
