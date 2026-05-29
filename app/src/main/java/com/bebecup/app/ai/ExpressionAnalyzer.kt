package com.bebecup.app.ai

/**
 * Parent-friendly expression scoring & labeling (spec §8.7). The app must never
 * judge a baby's face harshly, so labels stay warm and neutral faces are NOT
 * penalized (a calm face is a fine photo). Pure — testable without ML Kit.
 */
object ExpressionAnalyzer {

    /** Allowed labels (spec §8.7) — no harsh terms. */
    enum class ExpressionLabel {
        NATURAL_SMILE,
        PLAYFUL,
        CALM,
        SLEEPING,
        CUTE_POUT,
        UNCLEAR_FACE
    }

    data class ExpressionResult(val score: Float, val label: ExpressionLabel)

    /**
     * @param smilingProb ML Kit smilingProbability (0..1) or null if unknown.
     * @param faceDetected whether a face was found at all.
     */
    fun analyze(smilingProb: Float?, faceDetected: Boolean, sleepingModeEnabled: Boolean): ExpressionResult {
        if (!faceDetected) {
            return ExpressionResult(score = 0.4f, label = ExpressionLabel.UNCLEAR_FACE)
        }
        val smile = smilingProb ?: 0.5f
        if (sleepingModeEnabled && smile < 0.2f) {
            // Treat a serene/asleep face as a valid, pleasant shot.
            return ExpressionResult(score = 0.7f, label = ExpressionLabel.SLEEPING)
        }
        // Neutral floor of 0.5, rising with a genuine smile.
        val score = (0.5f + 0.5f * smile).coerceIn(0f, 1f)
        val label = when {
            smile >= 0.6f -> ExpressionLabel.NATURAL_SMILE
            smile >= 0.3f -> ExpressionLabel.PLAYFUL
            else -> ExpressionLabel.CALM
        }
        return ExpressionResult(score = score, label = label)
    }
}
