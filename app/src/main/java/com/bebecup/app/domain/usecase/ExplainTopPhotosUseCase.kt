package com.bebecup.app.domain.usecase

import com.bebecup.app.ai.LocalVisionExplainer
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.ai.AiCurationRepository
import com.bebecup.app.data.ai.PhotoAnalysisEntity

/**
 * Generates a natural-language Korean explanation for the top [limit] photos
 * only (spec §8.10 — VLM/explanation runs on a small set, never every image)
 * and caches it on the analysis row. Local-only; degrades gracefully when no
 * VLM is present (rule-based explainer).
 *
 * @return number of photos explained.
 */
class ExplainTopPhotosUseCase(
    private val babyRepository: BabyPhotoRepository,
    private val aiRepository: AiCurationRepository,
    private val explainer: LocalVisionExplainer
) {
    suspend operator fun invoke(
        limit: Int = DEFAULT_LIMIT,
        version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION
    ): Int {
        val top = aiRepository.getTopByScore(limit, version)
        var explained = 0
        for (analysis in top) {
            if (!analysis.aiReasonKo.isNullOrBlank()) continue // already explained
            val title = babyRepository.getPhotoById(analysis.photoId)?.title ?: ""
            val explanation = explainer.explain(title, analysis)
            aiRepository.saveAnalysis(analysis.copy(aiReasonKo = explanation.summaryKo))
            explained++
        }
        return explained
    }

    companion object {
        const val DEFAULT_LIMIT = 8
    }
}
