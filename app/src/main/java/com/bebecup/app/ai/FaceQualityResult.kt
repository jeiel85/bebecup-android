package com.bebecup.app.ai

/**
 * Output of [FaceQualityAnalyzer]. Derived scores (centered, area) are 0..1;
 * raw probabilities/angles are passed through for the pure analyzers
 * ([EyeStateAnalyzer], [ExpressionAnalyzer]) and the scoring policy.
 */
data class FaceQualityResult(
    val faceDetected: Boolean,
    val faceCount: Int,
    val faceCenterScore: Float,
    val faceAreaRatio: Float,
    val headYaw: Float?,
    val headPitch: Float?,
    val headRoll: Float?,
    val leftEyeOpenProb: Float?,
    val rightEyeOpenProb: Float?,
    val smilingProb: Float?
) {
    companion object {
        val NONE = FaceQualityResult(
            faceDetected = false,
            faceCount = 0,
            faceCenterScore = 0f,
            faceAreaRatio = 0f,
            headYaw = null,
            headPitch = null,
            headRoll = null,
            leftEyeOpenProb = null,
            rightEyeOpenProb = null,
            smilingProb = null
        )
    }
}
