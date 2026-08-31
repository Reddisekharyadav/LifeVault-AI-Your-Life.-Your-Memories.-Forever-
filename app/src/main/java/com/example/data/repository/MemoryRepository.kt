package com.example.data.repository

import com.example.data.db.MemoryDao
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.model.LocalUser
import com.example.data.api.GeminiClient
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<LocalMemory>> = memoryDao.getAllMemoriesFlow()
    val allRelationships: Flow<List<LocalRelationship>> = memoryDao.getAllRelationshipsFlow()
    val allUsers: Flow<List<LocalUser>> = memoryDao.getAllUsersFlow()

    fun getMemoriesForUser(userEmail: String): Flow<List<LocalMemory>> {
        return memoryDao.getMemoriesForUserFlow(userEmail)
    }

    fun getRelationshipsForUser(userEmail: String): Flow<List<LocalRelationship>> {
        return memoryDao.getRelationshipsForUserFlow(userEmail)
    }

    fun getPinnedMemoriesForUser(userEmail: String): Flow<List<LocalMemory>> {
        return memoryDao.getPinnedMemoriesForUserFlow(userEmail)
    }

    fun getMemoriesByTypeForUser(userEmail: String, type: String): Flow<List<LocalMemory>> {
        return memoryDao.getMemoriesByTypeForUserFlow(userEmail, type)
    }

    suspend fun getMemoryById(id: Long): LocalMemory? {
        return memoryDao.getMemoryById(id)
    }

    suspend fun insertMemory(memory: LocalMemory): Long {
        return memoryDao.insertMemory(memory)
    }

    suspend fun deleteMemory(memory: LocalMemory) {
        memoryDao.deleteMemory(memory)
    }

    suspend fun insertRelationship(relationship: LocalRelationship): Long {
        return memoryDao.insertRelationship(relationship)
    }

    suspend fun deleteRelationship(relationship: LocalRelationship) {
        memoryDao.deleteRelationship(relationship)
    }

    suspend fun getUserByEmail(email: String): LocalUser? {
        return memoryDao.getUserByEmail(email)
    }

    suspend fun insertUser(user: LocalUser) {
        memoryDao.insertUser(user)
    }

    suspend fun deleteUser(user: LocalUser) {
        memoryDao.deleteUser(user)
    }

    /**
     * Leverage GeminiClient for generating AI stories, summaries, gratitude maps, and dream interpretations.
     */
    suspend fun getAISummary(prompt: String): String {
        return GeminiClient.generateContent(prompt)
    }

    suspend fun formatVoiceToPersonalDiary(
        rawTranscript: String = "",
        durationSec: Int = 0,
        audioBytes: ByteArray? = null,
        audioMimeType: String = "audio/mp4"
    ): com.example.data.api.DiaryConversionResult {
        return GeminiClient.formatVoiceToPersonalDiary(
            rawTranscript = rawTranscript,
            durationSec = durationSec,
            audioBytes = audioBytes,
            audioMimeType = audioMimeType
        )
    }
}

