package com.bebecup.app.ai

import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.domain.model.PhotoQualityGrade
import com.bebecup.app.domain.model.PhotoScoreInput

/**
 * Applies a duplicate demotion to a cached analysis: a non-representative photo
 * in a near-duplicate cluster gets its uniqueness zeroed and a penalty set,
 * then its overall score/grade are recomputed via [BabyPhotoScorePolicy].
 * Pure (no JSON / no DB) so it is unit-testable; the reject-reason string is
 * appended separately by the use case.
 */
object DuplicatePenaltyPolicy {

    const val DUPLICATE_UNIQUENESS = 0.0f
    const val DUPLICATE_PENALTY = 0.7f
    const val DUPLICATE_REASON = "비슷한 사진 중 더 선명한 후보가 있어요"

    /** Recompute a non-representative duplicate's score with uniqueness removed. */
    fun demote(entity: PhotoAnalysisEntity): PhotoAnalysisEntity {
        val input = toScoreInput(entity, uniqueness = DUPLICATE_UNIQUENESS)
        val overall = BabyPhotoScorePolicy.calculateOverallScore(input)
        return entity.copy(
            duplicatePenalty = DUPLICATE_PENALTY,
            overallScore = overall,
            qualityGrade = PhotoQualityGrade.fromScore(overall).label
        )
    }

    private fun toScoreInput(e: PhotoAnalysisEntity, uniqueness: Float) = PhotoScoreInput(
        blurScore = e.blurScore,
        faceDetected = e.faceDetected,
        faceCenterScore = e.faceCenterScore,
        eyeOpenScore = e.eyeOpenScore,
        expressionScore = e.expressionScore,
        exposureScore = e.exposureScore,
        compositionScore = e.compositionScore,
        uniquenessScore = uniqueness
    )
}
