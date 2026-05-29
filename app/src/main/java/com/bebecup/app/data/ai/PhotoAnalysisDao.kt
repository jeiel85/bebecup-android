package com.bebecup.app.data.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: PhotoAnalysisEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(analyses: List<PhotoAnalysisEntity>)

    @Update
    suspend fun update(analysis: PhotoAnalysisEntity)

    @Query("SELECT * FROM photo_analysis WHERE photoId = :photoId AND analysisVersion = :version LIMIT 1")
    suspend fun getForPhoto(photoId: Int, version: Int): PhotoAnalysisEntity?

    /** Photo ids that already have a cached result for [version] — used to skip re-analysis. */
    @Query("SELECT photoId FROM photo_analysis WHERE analysisVersion = :version")
    suspend fun getAnalyzedPhotoIds(version: Int): List<Int>

    /** All cached analyses for [version] — used by duplicate clustering. */
    @Query("SELECT * FROM photo_analysis WHERE analysisVersion = :version")
    suspend fun getAllForVersion(version: Int): List<PhotoAnalysisEntity>

    /** Top-scoring analyses for shortlist building. */
    @Query("SELECT * FROM photo_analysis WHERE analysisVersion = :version ORDER BY overallScore DESC LIMIT :limit")
    fun observeTopByScore(version: Int, limit: Int): Flow<List<PhotoAnalysisEntity>>

    @Query("SELECT * FROM photo_analysis WHERE analysisVersion = :version ORDER BY overallScore DESC LIMIT :limit")
    suspend fun getTopByScore(version: Int, limit: Int): List<PhotoAnalysisEntity>

    /** Privacy: "delete all local analysis data". */
    @Query("DELETE FROM photo_analysis")
    suspend fun deleteAll()
}
