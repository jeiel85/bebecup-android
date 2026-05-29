package com.bebecup.app.ai

import com.bebecup.app.domain.model.PhotoQualityGrade
import com.bebecup.app.domain.model.PhotoScoreInput

/**
 * Deterministic, dependency-free scoring policy (spec §16). Pure functions only
 * so it is fully unit-testable without Android. No VLM is involved here — MVP
 * scoring is rule-based and explainable.
 */
object BabyPhotoScorePolicy {

    // Weights sum to 1.0 (spec §8.9).
    private const val W_BLUR = 0.25f
    private const val W_FACE_CENTER = 0.20f
    private const val W_EYE_OPEN = 0.20f
    private const val W_EXPRESSION = 0.15f
    private const val W_EXPOSURE = 0.10f
    private const val W_COMPOSITION = 0.05f
    private const val W_UNIQUENESS = 0.05f

    /** Multiplier applied when no face is detected — demote, don't reject. */
    private const val NO_FACE_PENALTY = 0.65f

    /** @return overall score in 0..100. */
    fun calculateOverallScore(input: PhotoScoreInput): Float {
        val weighted =
            input.blurScore * W_BLUR +
                input.faceCenterScore * W_FACE_CENTER +
                input.eyeOpenScore * W_EYE_OPEN +
                input.expressionScore * W_EXPRESSION +
                input.exposureScore * W_EXPOSURE +
                input.compositionScore * W_COMPOSITION +
                input.uniquenessScore * W_UNIQUENESS

        val scaled = if (input.faceDetected) weighted else weighted * NO_FACE_PENALTY
        return (scaled * 100f).coerceIn(0f, 100f)
    }

    fun grade(input: PhotoScoreInput): PhotoQualityGrade =
        PhotoQualityGrade.fromScore(calculateOverallScore(input))
}
