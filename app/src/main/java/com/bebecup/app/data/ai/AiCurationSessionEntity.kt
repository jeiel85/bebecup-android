package com.bebecup.app.data.ai

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks one AI curation scan session (its scan range, progress counts, and
 * lifecycle [status]). Lets the UI resume/report on a scan and lets us avoid
 * re-running an in-flight scan.
 */
@Entity(tableName = "ai_curation_sessions")
data class AiCurationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val scanRangeStartMillis: Long,
    val scanRangeEndMillis: Long,

    val totalFoundCount: Int = 0,
    val analyzedCount: Int = 0,
    val rejectedCount: Int = 0,
    val shortlistedCount: Int = 0,

    val status: String,
    val createdAtMillis: Long,
    val completedAtMillis: Long? = null
)

/** Lifecycle states for [AiCurationSessionEntity.status]. */
object CurationStatus {
    const val CREATED = "CREATED"
    const val SCANNING = "SCANNING"
    const val ANALYZING = "ANALYZING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val CANCELED = "CANCELED"
}
