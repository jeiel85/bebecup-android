package com.bebecup.app.data.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCurationSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: AiCurationSessionEntity): Long

    @Update
    suspend fun update(session: AiCurationSessionEntity)

    @Query("SELECT * FROM ai_curation_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): AiCurationSessionEntity?

    @Query("SELECT * FROM ai_curation_sessions ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<AiCurationSessionEntity?>

    @Query("SELECT * FROM ai_curation_sessions ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<AiCurationSessionEntity>>

    @Query("DELETE FROM ai_curation_sessions")
    suspend fun deleteAll()
}
