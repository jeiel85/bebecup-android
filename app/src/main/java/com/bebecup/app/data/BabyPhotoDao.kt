package com.bebecup.app.data

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: BabyPhoto): Long

    @Update
    suspend fun updatePhoto(photo: BabyPhoto)

    @Delete
    suspend fun deletePhoto(photo: BabyPhoto)

    @Query("SELECT * FROM baby_photos WHERE id = :id LIMIT 1")
    suspend fun getPhotoById(id: Int): BabyPhoto?

    @Query("SELECT * FROM baby_photos WHERE uriString = :uriString LIMIT 1")
    suspend fun getPhotoByUri(uriString: String): BabyPhoto?

    @Query("UPDATE baby_photos SET isSelectedAsBest = 0")
    suspend fun clearSelectedBest()

    // Targeted single-column updates: never write back a whole (possibly stale)
    // BabyPhoto copy, so concurrent changes to other columns aren't clobbered.

    @Query("UPDATE baby_photos SET isSelectedAsBest = 1, bestSelectedTimestamp = :timestamp WHERE id IN (:ids)")
    suspend fun markBest(ids: List<Int>, timestamp: Long)

    @Query("UPDATE baby_photos SET isSelectedAsBest = 0, bestSelectedTimestamp = 0 WHERE id = :id")
    suspend fun unmarkBest(id: Int)

    @Query("UPDATE baby_photos SET matchesCount = matchesCount + 1 WHERE id = :id")
    suspend fun incrementMatchWins(id: Int)

    @Query("UPDATE baby_photos SET winsCount = winsCount + 1 WHERE id = :id")
    suspend fun incrementChampionships(id: Int)

    @Query("UPDATE baby_photos SET isInPrintCart = :inCart WHERE id = :id")
    suspend fun setInPrintCart(id: Int, inCart: Boolean)

    @Query("UPDATE baby_photos SET printSize = :size, printQuantity = :quantity WHERE id = :id")
    suspend fun updatePrintOptions(id: Int, size: String, quantity: Int)

    @Query("UPDATE baby_photos SET isInPrintCart = 0")
    suspend fun clearPrintCart()

    @Query("DELETE FROM baby_photos WHERE uriString NOT LIKE 'content://%' AND uriString NOT LIKE 'file://%' AND uriString NOT LIKE 'http%'")
    suspend fun pruneMockPhotos()

    // Tournament Records queries
    @Query("SELECT * FROM tournament_records ORDER BY timestamp DESC")
    fun getAllTournaments(): Flow<List<TournamentRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(record: TournamentRecord): Long
}
