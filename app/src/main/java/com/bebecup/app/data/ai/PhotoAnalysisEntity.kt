package com.bebecup.app.data.ai

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bebecup.app.data.BabyPhoto

/**
 * Cached on-device AI/CV analysis result for a single [BabyPhoto].
 *
 * One row per (photoId, analysisVersion). Keeping the analysis separate from
 * [BabyPhoto] lets gallery scans stay fast and lets us re-run scoring under a
 * new [analysisVersion] without touching the user-facing photo identity/state.
 *
 * All scores are normalized to 0.0..1.0 unless noted. [overallScore] is 0..100.
 * NOTE: analysis data is local-only and never leaves the device.
 */
@Entity(
    tableName = "photo_analysis",
    foreignKeys = [
        ForeignKey(
            entity = BabyPhoto::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("photoId"),
        Index("overallScore"),
        Index("analysisVersion")
    ]
)
data class PhotoAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    // FK to baby_photos.id (Int, matching the existing schema).
    val photoId: Int,
    val analysisVersion: Int,

    val blurScore: Float,
    val faceDetected: Boolean,
    val faceCount: Int,
    val faceCenterScore: Float,
    val eyeOpenScore: Float,
    val expressionScore: Float,
    val exposureScore: Float,
    val compositionScore: Float,
    val duplicatePenalty: Float,

    val overallScore: Float,
    val qualityGrade: String,

    /** dHash for near-duplicate clustering (spec §8.8); 0 if not computed. */
    val dHash: Long = 0L,

    // JSON-encoded List<String> of parent-friendly reasons.
    val rejectReasonsJson: String,
    val positiveReasonsJson: String,
    val aiReasonKo: String? = null,

    val analyzedAtMillis: Long
) {
    companion object {
        /** Bump when the scoring pipeline changes so stale rows are recomputed. */
        const val CURRENT_ANALYSIS_VERSION = 1
    }
}
