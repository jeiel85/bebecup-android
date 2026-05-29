package com.bebecup.app.data.ai

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A near-duplicate / burst group of photos. Used in Phase 5 to avoid
 * recommending many nearly identical shots: only the [representativePhotoId]
 * is surfaced strongly, the rest are demoted.
 */
@Entity(
    tableName = "photo_clusters",
    indices = [Index("clusterKey")]
)
data class PhotoClusterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val clusterKey: String,
    val representativePhotoId: Int,
    // JSON-encoded List<Int> of member baby_photos.id.
    val photoIdsJson: String,
    val createdAtMillis: Long
)
