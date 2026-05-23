package com.bebecup.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "baby_photos")
data class BabyPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uriString: String,              // Photo Picker content:// URI or another locally readable image URI
    val title: String,                  // e.g., "첫 소풍 미소"
    val description: String,            // e.g., "날씨가 맑았던 한강 공원에서의 첫 외출"
    val takenDate: String,              // e.g., "2026-05-20"
    val isSelectedAsBest: Boolean = false, // Chosen as a best shot pool for World Cups
    val bestSelectedTimestamp: Long = 0L,  // To sort by recent best shot
    val winsCount: Int = 0,             // Number of tournament championships won
    val matchesCount: Int = 0,          // Total rounds or matchups won across all World Cups
    val isInPrintCart: Boolean = false, // Marked for ZZIXX print selection
    val printQuantity: Int = 1,         // Print count
    val printSize: String = "4x6"       // Size e.g., "3x5", "4x6", "D4", "Wallet"
) : Serializable
