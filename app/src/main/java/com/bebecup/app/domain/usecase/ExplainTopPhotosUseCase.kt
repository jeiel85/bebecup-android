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
 * The top photos are picked with [ShortlistRanking], the same rule the
 * shortlist uses, so the photos explained are the ones the parent sees first.
 *
 * @return number of photos explained.
 */
class ExplainTopPhotosUseCase(
    private val babyRepository: BabyPhotoRepository,
    private val aiRepository: AiCurationRepository,
    private val explainer: LocalVisionExplainer
) {
    /**
     * @param photoIds when non-null, explain only this scan's photos (analysis
     *   rows accumulate across scans); when null, the best across all of them.
     * @param excludeBlurry pass the same value the shortlist is built with.
     */
    suspend operator fun invoke(
        limit: Int = DEFAULT_LIMIT,
        photoIds: Set<Int>? = null,
        excludeBlurry: Boolean = false,
        version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION
    ): Int {
        val scoped = aiRepository.getAllAnalyses(version)
            .let { list -> if (photoIds == null) list else list.filter { it.photoId in photoIds } }
        val top = ShortlistRanking.split(scoped, limit, excludeBlurry).shortlist
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
