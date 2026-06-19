package com.example.data.db

import androidx.room.*
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<LocalMemory>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): LocalMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: LocalMemory): Long

    @Delete
    suspend fun deleteMemory(memory: LocalMemory)

    @Query("SELECT * FROM memories WHERE isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedMemoriesFlow(): Flow<List<LocalMemory>>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY timestamp DESC")
    fun getMemoriesByTypeFlow(type: String): Flow<List<LocalMemory>>

    // Relationships DAO operations
    @Query("SELECT * FROM relationships ORDER BY name ASC")
    fun getAllRelationshipsFlow(): Flow<List<LocalRelationship>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(relationship: LocalRelationship): Long

    @Delete
    suspend fun deleteRelationship(relationship: LocalRelationship)
}
