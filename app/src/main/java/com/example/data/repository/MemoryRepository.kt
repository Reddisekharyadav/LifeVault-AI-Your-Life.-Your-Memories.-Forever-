package com.example.data.repository

import com.example.data.db.MemoryDao
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.api.GeminiClient
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<LocalMemory>> = memoryDao.getAllMemoriesFlow()
    val pinnedMemories: Flow<List<LocalMemory>> = memoryDao.getPinnedMemoriesFlow()
    val allRelationships: Flow<List<LocalRelationship>> = memoryDao.getAllRelationshipsFlow()

    fun getMemoriesByType(type: String): Flow<List<LocalMemory>> {
        return memoryDao.getMemoriesByTypeFlow(type)
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

    /**
     * Leverage GeminiClient for generating AI stories, summaries, gratitude maps, and dream interpretations.
     */
    suspend fun getAISummary(prompt: String): String {
        return GeminiClient.generateContent(prompt)
    }
}
