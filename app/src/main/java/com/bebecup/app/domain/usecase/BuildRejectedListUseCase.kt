package com.bebecup.app.domain.usecase

import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.ai.AiCurationRepository
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.ShortlistItem

/**
 * The photos that did NOT make the shortlist — analyzed but ranked below the
 * top [shortlistLimit]. Surfaced in a separate "제외된 사진" section so parents
 * can review and override (spec §11.5, §15.3). Rejected photos are never
 * deleted.
 */
class BuildRejectedListUseCase(
    private val babyRepository: BabyPhotoRepository,
    private val aiRepository: AiCurationRepository
) {
    suspend operator fun invoke(
        shortlistLimit: Int,
        maxItems: Int = DEFAULT_MAX,
        version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION
    ): List<ShortlistItem> {
        val all = aiRepository.getAllAnalyses(version).sortedByDescending { it.overallScore }
        return all.drop(shortlistLimit)
            .take(maxItems)
            .mapNotNull { analysis ->
                babyRepository.getPhotoById(analysis.photoId)?.let { ShortlistItem(it, analysis) }
            }
    }

    companion object {
        const val DEFAULT_MAX = 30
    }
}
