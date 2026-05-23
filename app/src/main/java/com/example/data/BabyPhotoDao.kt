package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyPhotoDao {
    // Baby Photos queries
    @Query("SELECT * FROM baby_photos ORDER BY takenDate DESC")
    fun getAllPhotos(): Flow<List<BabyPhoto>>

    @Query("SELECT * FROM baby_photos WHERE isSelectedAsBest = 1 ORDER BY bestSelectedTimestamp DESC")
    fun getBestPhotos(): Flow<List<BabyPhoto>>

    @Query("SELECT * FROM baby_photos WHERE isInPrintCart = 1 ORDER BY id DESC")
    fun getPrintCartPhotos(): Flow<List<BabyPhoto>>

    @Query("SELECT COUNT(*) FROM baby_photos WHERE isPreset = 1")
    suspend fun getPresetCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: BabyPhoto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<BabyPhoto>)

    @Update
    suspend fun updatePhoto(photo: BabyPhoto)

    @Delete
    suspend fun deletePhoto(photo: BabyPhoto)

    @Query("SELECT * FROM baby_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: Int): BabyPhoto?

    @Query("UPDATE baby_photos SET isSelectedAsBest = 0")
    suspend fun clearSelectedBest()

    // Tournament Records queries
    @Query("SELECT * FROM tournament_records ORDER BY timestamp DESC")
    fun getAllTournaments(): Flow<List<TournamentRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(record: TournamentRecord): Long
}
