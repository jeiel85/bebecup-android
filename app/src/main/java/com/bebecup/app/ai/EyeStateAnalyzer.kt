package com.bebecup.app.ai

/**
 * Turns ML Kit eye-open probabilities into a 0..1 score (spec §8.6).
 * Pure — testable without ML Kit. Closed eyes are NOT auto-rejected when the
 * parent allows sleeping shots.
 */
object EyeStateAnalyzer {

    /** Returned when no probability is available (e.g. classification off / no face). */
    private const val UNKNOWN = 0.5f

    /** Floor applied in sleeping mode so closed eyes don't tank the score. */
    private const val SLEEPING_FLOOR = 0.7f

    /**
     * @param leftProb ML Kit leftEyeOpenProbability (0..1) or null if unknown.
     * @param rightProb ML Kit rightEyeOpenProbability (0..1) or null if unknown.
     */
    fun eyeOpenScore(leftProb: Float?, rightProb: Float?, sleepingModeEnabled: Boolean): Float {
        val probs = listOfNotNull(leftProb, rightProb)
        val base = if (probs.isEmpty()) UNKNOWN else probs.average().toFloat()
        val score = if (sleepingModeEnabled) maxOf(base, SLEEPING_FLOOR) else base
        return score.coerceIn(0f, 1f)
    }
}
