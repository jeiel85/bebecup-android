package com.bebecup.app.domain.model

/**
 * Quality bands for an analyzed photo, derived from the 0..100 overall score.
 * See spec §8.9.
 */
enum class PhotoQualityGrade(val label: String) {
    S("S"),
    A("A"),
    B("B"),
    C("C"),
    D("D");

    /** Whether this grade should appear in the AI shortlist by default. */
    val isStronglyRecommended: Boolean get() = this == S || this == A

    /** Kept as a backup candidate (visible but not headline). */
    val isBackup: Boolean get() = this == B

    companion object {
        fun fromScore(overallScore: Float): PhotoQualityGrade = when {
            overallScore >= 90f -> S
            overallScore >= 80f -> A
            overallScore >= 70f -> B
            overallScore >= 60f -> C
            else -> D
        }
    }
}
