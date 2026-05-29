package com.bebecup.app.domain.model

/**
 * Normalized (0.0..1.0) per-dimension scores fed into [com.bebecup.app.ai.BabyPhotoScorePolicy].
 * Higher is better for every field except [duplicatePenalty] (higher = more
 * redundant). See spec §16.
 */
data class PhotoScoreInput(
    val blurScore: Float,
    val faceDetected: Boolean,
    val faceCenterScore: Float,
    val eyeOpenScore: Float,
    val expressionScore: Float,
    val exposureScore: Float,
    val compositionScore: Float,
    val uniquenessScore: Float,
    val sleepingModeEnabled: Boolean = false
)
