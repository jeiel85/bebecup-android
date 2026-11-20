package com.bebecup.app.domain.usecase

import com.bebecup.app.data.ai.PhotoAnalysisEntity

/**
 * Splits analyzed photos into the shortlist and the "아쉬운 후보" (rejected)
 * list. Shared by [BuildAiShortlistUseCase] and [BuildRejectedListUseCase] so
 * both always agree on who made the cut.
 */
object ShortlistRanking {

    /**
     * Below this blur score a photo is treated as shaken. Matches the
     * "얼굴 부분이 조금 흔들렸어요" reject reason in PhotoReasonBuilder.
     */
    const val BLUR_EXCLUDE_THRESHOLD = 0.35f

    data class Split(
        val shortlist: List<PhotoAnalysisEntity>,
        val rejected: List<PhotoAnalysisEntity>
    )

    /**
     * @param excludeBlurry when true ("흔들린 사진은 자동 제외"), shaken photos
     *   never enter the shortlist even if they rank high; they move to rejected.
     */
    fun split(
        analyses: List<PhotoAnalysisEntity>,
        limit: Int,
        excludeBlurry: Boolean
    ): Split {
        val ranked = analyses.sortedByDescending { it.overallScore }
        val eligible = if (excludeBlurry) {
            ranked.filter { it.blurScore >= BLUR_EXCLUDE_THRESHOLD }
        } else {
            ranked
        }
        val shortlist = eligible.take(limit)
        val shortIds = shortlist.mapTo(HashSet()) { it.photoId }
        return Split(shortlist, ranked.filter { it.photoId !in shortIds })
    }
}
