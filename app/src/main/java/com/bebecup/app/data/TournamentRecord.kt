package com.bebecup.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tournament_records")
data class TournamentRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val winnerPhotoId: Int,
    val winnerTitle: String,
    val winnerUriString: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalParticipants: Int,
    val tournamentTitle: String,
    /** "yyyy-MM" for a 이달의 월드컵 (photos taken that month); null for other cups. Added in DB v5. */
    val monthKey: String? = null,
    /**
     * The child whose cup it was; null for a family cup (one child or none
     * registered, or a cup from before v0.13.0 that mixed siblings). No foreign
     * key: a cup stays in the hall after its child is removed. Added in DB v7.
     */
    val childId: Long? = null
)
