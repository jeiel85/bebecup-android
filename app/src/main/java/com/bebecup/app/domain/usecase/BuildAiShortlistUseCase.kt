package com.bebecup.app.domain.usecase

import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.ai.AiCurationRepository
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.ShortlistItem

/**
 * Builds the AI shortlist: the top-scoring analyzed photos, paired with their
 * analysis, ordered best-first (spec §3.1 / §8.9). Default size 16 (a 16-photo
 * tournament bracket); callers may request 8.
 */
class BuildAiShortlistUseCase(
    private val babyRepository: BabyPhotoRepository,
    private val aiRepository: AiCurationRepository
) {
    suspend operator fun invoke(limit: Int = DEFAULT_LIMIT): List<ShortlistItem> {
        val top = aiRepository.getTopByScore(limit, PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION)
        return top.mapNotNull { analysis ->
            babyRepository.getPhotoById(analysis.photoId)?.let { photo ->
                ShortlistItem(photo = photo, analysis = analysis)
            }
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 16
    }
}
