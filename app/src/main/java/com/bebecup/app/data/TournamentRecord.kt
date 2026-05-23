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
    val tournamentTitle: String
)
