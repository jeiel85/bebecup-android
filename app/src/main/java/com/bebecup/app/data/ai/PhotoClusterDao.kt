package com.bebecup.app.data.ai

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoClusterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cluster: PhotoClusterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(clusters: List<PhotoClusterEntity>)

    @Query("SELECT * FROM photo_clusters ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<PhotoClusterEntity>>

    @Query("SELECT * FROM photo_clusters WHERE clusterKey = :clusterKey LIMIT 1")
    suspend fun getByKey(clusterKey: String): PhotoClusterEntity?

    @Query("DELETE FROM photo_clusters")
    suspend fun deleteAll()
}
