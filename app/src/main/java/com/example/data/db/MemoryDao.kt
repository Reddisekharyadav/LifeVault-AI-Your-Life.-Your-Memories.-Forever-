package com.example.data.db

import androidx.room.*
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.model.LocalUser
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE LOWER(TRIM(userEmail)) = LOWER(TRIM(:userEmail)) OR userEmail = '' OR :userEmail = '' ORDER BY timestamp DESC")
    fun getMemoriesForUserFlow(userEmail: String): Flow<List<LocalMemory>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemoriesFlow(): Flow<List<LocalMemory>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): LocalMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: LocalMemory): Long

    @Delete
    suspend fun deleteMemory(memory: LocalMemory)

    @Query("SELECT * FROM memories WHERE (LOWER(TRIM(userEmail)) = LOWER(TRIM(:userEmail)) OR userEmail = '' OR :userEmail = '') AND isPinned = 1 ORDER BY timestamp DESC")
    fun getPinnedMemoriesForUserFlow(userEmail: String): Flow<List<LocalMemory>>

    @Query("SELECT * FROM memories WHERE (LOWER(TRIM(userEmail)) = LOWER(TRIM(:userEmail)) OR userEmail = '' OR :userEmail = '') AND type = :type ORDER BY timestamp DESC")
    fun getMemoriesByTypeForUserFlow(userEmail: String, type: String): Flow<List<LocalMemory>>

    // Relationships DAO operations
    @Query("SELECT * FROM relationships WHERE LOWER(TRIM(userEmail)) = LOWER(TRIM(:userEmail)) OR userEmail = '' OR :userEmail = '' ORDER BY name ASC")
    fun getRelationshipsForUserFlow(userEmail: String): Flow<List<LocalRelationship>>

    @Query("SELECT * FROM relationships ORDER BY name ASC")
    fun getAllRelationshipsFlow(): Flow<List<LocalRelationship>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(relationship: LocalRelationship): Long

    @Delete
    suspend fun deleteRelationship(relationship: LocalRelationship)

    // User Accounts DAO operations
    @Query("SELECT * FROM users WHERE LOWER(TRIM(email)) = LOWER(TRIM(:email)) LIMIT 1")
    suspend fun getUserByEmail(email: String): LocalUser?

    @Query("SELECT * FROM users ORDER BY createdAt DESC")
    fun getAllUsersFlow(): Flow<List<LocalUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: LocalUser)

    @Delete
    suspend fun deleteUser(user: LocalUser)
}

