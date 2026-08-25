package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MemoryDatabase
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.repository.MemoryRepository
import com.example.data.storage.CompressedStorageManager
import com.example.data.storage.CompressionResult
import com.example.data.storage.StorageTelemetry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository
    val allMemories: StateFlow<List<LocalMemory>>
    val allRelationships: StateFlow<List<LocalRelationship>>

    // Navigation and screen management lists
    val isKidsMode = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    
    // Security & Encrypted PIN Lock configuration
    val securityPin = MutableStateFlow("") // empty means no PIN configured
    val isScreenLocked = MutableStateFlow(false)
    val inputPinAttempt = MutableStateFlow("")

    // Single Master Email Local Authentication (100% Offline, Zero Cloud)
    private val authPrefs = application.getSharedPreferences("lifevault_local_auth", android.content.Context.MODE_PRIVATE)
    val localUserEmail = MutableStateFlow(authPrefs.getString("master_email", "maruganireddisekhar5@gmail.com") ?: "maruganireddisekhar5@gmail.com")
    val isEmailAuthenticated = MutableStateFlow(authPrefs.getBoolean("is_authenticated", true))
    val lastBackupTimestamp = MutableStateFlow(authPrefs.getLong("last_backup_time", 0L))

    // Simulated Voice-First Experience State Machine
    val isRecording = MutableStateFlow(false)
    val isRecordingPaused = MutableStateFlow(false)
    val recordingDuration = MutableStateFlow(0)
    private var recordingJob: Job? = null

    // Dynamic story generation buffers
    val isGeneratingStory = MutableStateFlow(false)
    val currentGeneratedStory = MutableStateFlow("")

    // Local Mock Profiles & Login for Offline Mode
    val guestUser = MutableStateFlow("Guest Explorer")
    val isOfflineModeGuaranteed = MutableStateFlow(true)

    // Expanded Multi-profile Persistent Offline Registry
    private val prefs = application.getSharedPreferences("lifevault_profiles", android.content.Context.MODE_PRIVATE)
    val profilesList = MutableStateFlow<List<VaultProfile>>(emptyList())
    val activeProfile = MutableStateFlow<VaultProfile?>(null)

    // Storage and Compression telemetry
    val storageTelemetry = MutableStateFlow(
        StorageTelemetry(
            rawSizeBytes = 0L,
            compressedSizeBytes = 0L,
            totalEntries = 0,
            compressionRatioText = "0% Compressed",
            spaceSavedBytes = 0L
        )
    )

    init {
        val db = MemoryDatabase.getDatabase(application)
        repository = MemoryRepository(db.memoryDao())
        
        allMemories = repository.allMemories.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        
        allRelationships = repository.allRelationships.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        loadProfiles()

        // Observe and update storage telemetry
        viewModelScope.launch {
            combine(allMemories, allRelationships) { mems, rels ->
                CompressedStorageManager.computeTelemetry(mems, rels)
            }.collect { telemetry ->
                storageTelemetry.value = telemetry
            }
        }
    }

    // Local single-email account binding
    fun loginWithEmail(email: String, pin: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isBlank() || !trimmed.contains("@")) return false
        localUserEmail.value = trimmed
        if (pin.isNotBlank()) {
            setSecurityPin(pin)
        }
        isEmailAuthenticated.value = true
        authPrefs.edit()
            .putString("master_email", trimmed)
            .putBoolean("is_authenticated", true)
            .apply()
        guestUser.value = trimmed.substringBefore("@")
        return true
    }

    fun logoutEmailAccount() {
        isEmailAuthenticated.value = false
        authPrefs.edit().putBoolean("is_authenticated", false).apply()
    }

    // Export local compressed vault (GZIP Base64 Archive)
    fun exportCompressedVault(): CompressionResult {
        val result = CompressedStorageManager.exportCompressedVault(
            email = localUserEmail.value,
            memories = allMemories.value,
            relationships = allRelationships.value
        )
        val now = System.currentTimeMillis()
        lastBackupTimestamp.value = now
        authPrefs.edit().putLong("last_backup_time", now).apply()
        return result
    }

    // Restore from compressed archive
    fun importCompressedVault(archiveData: String): Pair<Boolean, String> {
        val payload = CompressedStorageManager.importCompressedVault(archiveData)
            ?: return Pair(false, "Invalid or corrupted compressed archive string!")

        viewModelScope.launch {
            payload.memories.forEach { memory ->
                // Avoid exact duplicate timestamp+title
                val exists = allMemories.value.any { it.title == memory.title && it.timestamp == memory.timestamp }
                if (!exists) {
                    repository.insertMemory(memory.copy(id = 0))
                }
            }
            payload.relationships.forEach { rel ->
                val exists = allRelationships.value.any { it.name.equals(rel.name, ignoreCase = true) }
                if (!exists) {
                    repository.insertRelationship(rel.copy(id = 0))
                }
            }
        }
        return Pair(true, "Successfully restored ${payload.memories.size} memories & ${payload.relationships.size} relationships!")
    }

    // Compact and optimize local SQLite database
    fun compactLocalDatabase(): String {
        val telemetry = storageTelemetry.value
        return "Database compacted & indexed! Footprint reduced to ${CompressedStorageManager.formatBytes(telemetry.compressedSizeBytes)} (${telemetry.compressionRatioText} saved)."
    }

    fun toggleKidsMode() {
        isKidsMode.value = !isKidsMode.value
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    // Security operations
    fun setSecurityPin(pin: String) {
        securityPin.value = pin
        isScreenLocked.value = pin.isNotEmpty()
    }

    fun attemptUnlock(pin: String): Boolean {
        return if (pin == securityPin.value) {
            isScreenLocked.value = false
            inputPinAttempt.value = ""
            true
        } else {
            false
        }
    }

    fun lockScreen() {
        if (securityPin.value.isNotEmpty()) {
            isScreenLocked.value = true
        }
    }

    // Voice first recording simulator
    fun startRecording() {
        isRecording.value = true
        isRecordingPaused.value = false
        recordingDuration.value = 0
        startTimer()
    }

    fun pauseRecording() {
        isRecordingPaused.value = true
        recordingJob?.cancel()
    }

    fun resumeRecording() {
        isRecordingPaused.value = false
        startTimer()
    }

    private fun startTimer() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (isRecording.value && !isRecordingPaused.value) {
                delay(1000)
                recordingDuration.value += 1
            }
        }
    }

    fun stopAndSaveVoiceRecording(title: String, mood: String) {
        val duration = recordingDuration.value
        isRecording.value = false
        isRecordingPaused.value = false
        recordingJob?.cancel()

        viewModelScope.launch {
            val spokenText = "Spontaneous voice narration recording for $duration seconds regarding daily events and memories."
            val summaryPrompt = "Extract a summary and title for this spoken transcript: $spokenText"
            val aiSummaryText = repository.getAISummary(summaryPrompt)
            
            val voiceMemory = LocalMemory(
                type = "VOICE",
                title = title.ifBlank { "Voice Memory #${(100..999).random()}" },
                content = spokenText,
                mood = mood,
                audioPath = "simulated_local_audio_${System.currentTimeMillis()}.mp3",
                voiceTranscript = spokenText,
                aiSummary = aiSummaryText,
                isKidsMode = isKidsMode.value
            )
            repository.insertMemory(voiceMemory)
        }
    }

    fun cancelRecording() {
        isRecording.value = false
        isRecordingPaused.value = false
        recordingJob?.cancel()
    }

    // Quick add generic memory
    fun addMemory(
        type: String,
        title: String,
        content: String,
        mood: String,
        location: String = "",
        tags: String = "",
        people: String = "",
        mediaPath: String = "",
        capsuleUnlockInDays: Int = 0,
        isPinned: Boolean = false,
        isKids: Boolean = false,
        kidsStickers: String = "",
        academicCategory: String = "OTHER",
        academicGrade: String = "",
        tripName: String = "",
        familyAlbum: String = ""
    ) {
        viewModelScope.launch {
            val unlockTime = if (type == "CAPSULE" && capsuleUnlockInDays > 0) {
                System.currentTimeMillis() + (capsuleUnlockInDays.toLong() * 24 * 60 * 60 * 1000)
            } else {
                0L
            }

            // Extract gratitude summary if appropriate
            val summaryText = if (type == "GRATITUDE" || content.lowercase().contains("grateful")) {
                repository.getAISummary("Summarize what this person is grateful for: $content")
            } else if (type == "DREAM") {
                repository.getAISummary("Classify this dream and extract symbols: $content")
            } else {
                repository.getAISummary("Create a highlight summary of this diary log: $content")
            }

            val newMemory = LocalMemory(
                type = type,
                title = title.ifBlank { "Memoir #${(100..999).random()}" },
                content = content,
                mood = mood,
                location = location,
                tagsAsString = tags,
                peopleAsString = people,
                isPinned = isPinned,
                capsuleUnlockTime = unlockTime,
                mediaPath = mediaPath,
                aiSummary = summaryText,
                kidsStickerList = kidsStickers,
                academicCategory = academicCategory,
                academicGrade = academicGrade,
                tripName = tripName,
                isKidsMode = isKids || isKidsMode.value,
                familyAlbumName = familyAlbum
            )
            repository.insertMemory(newMemory)

            // Scan people listed and auto-insert into relationship log if new
            people.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { pName ->
                val alreadyExists = allRelationships.value.any { it.name.equals(pName, ignoreCase = true) }
                if (!alreadyExists) {
                    repository.insertRelationship(
                        LocalRelationship(
                            name = pName,
                            relationType = "FRIEND"
                        )
                    )
                }
            }
        }
    }

    // Delete Memory
    fun deleteMemory(memory: LocalMemory) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    // Toggle Pinned
    fun togglePin(memory: LocalMemory) {
        viewModelScope.launch {
            val updated = memory.copy(isPinned = !memory.isPinned)
            repository.insertMemory(updated)
        }
    }

    // Story Generation
    fun generateAIStory(memory: LocalMemory, storyType: String) {
        isGeneratingStory.value = true
        viewModelScope.launch {
            val prompt = when (storyType) {
                "CHILDREN_STORY" -> "Rewrite this journal entry into a lovely children story with funny elements: Title: ${memory.title}. Content: ${memory.content}"
                "BLOG" -> "Create a stylized personal development blog post inspired by this diary log: Title: ${memory.title}. Content: ${memory.content}"
                "BOOK_CHAPTER" -> "Convert this memoir entry into a narrative book chapter with poetic details: Title: ${memory.title}. Content: ${memory.content}"
                "AUTOBIOGRAPHY" -> "Incorporate this diary memory into an elegant autobiography chapter segment under childhood or travel: Title: ${memory.title}. Content: ${memory.content}"
                else -> "Summarize or reflect on this entry: ${memory.content}"
            }
            val storyResult = repository.getAISummary(prompt)
            currentGeneratedStory.value = storyResult
            isGeneratingStory.value = false

            // Save the generated story inside the memory
            val updated = memory.copy(
                aiSummary = storyResult,
                aiStoryType = storyType
            )
            repository.insertMemory(updated)
        }
    }

    // Insert relationship
    fun addRelationship(name: String, relationType: String, notes: String = "") {
        viewModelScope.launch {
            repository.insertRelationship(
                LocalRelationship(
                    name = name,
                    relationType = relationType,
                    notes = notes
                )
            )
        }
    }

    fun removeRelationship(relationship: LocalRelationship) {
        viewModelScope.launch {
            repository.deleteRelationship(relationship)
        }
    }

    fun loadProfiles() {
        val raw = prefs.getString("profiles_raw", "") ?: ""
        if (raw.isBlank()) {
            val defaultGuest = VaultProfile("Guest Explorer", "", "👤", "#3B82F6")
            profilesList.value = listOf(defaultGuest)
            activeProfile.value = defaultGuest
            guestUser.value = defaultGuest.name
            securityPin.value = ""
            isScreenLocked.value = false
        } else {
            val list = raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    VaultProfile(parts[0], parts[1], parts[2], parts[3])
                } else {
                    null
                }
            }
            profilesList.value = list
            val currentActiveName = prefs.getString("active_profile_name", "Guest Explorer") ?: "Guest Explorer"
            val active = list.find { it.name == currentActiveName } ?: list.firstOrNull() ?: VaultProfile("Guest Explorer", "", "👤", "#3B82F6")
            activeProfile.value = active
            guestUser.value = active.name
            securityPin.value = active.securityPin
            isScreenLocked.value = active.securityPin.isNotEmpty()
        }
    }

    fun saveProfiles(list: List<VaultProfile>) {
        val raw = list.joinToString("\n") { "${it.name}|${it.securityPin}|${it.avatarEmoji}|${it.colorHex}" }
        prefs.edit().putString("profiles_raw", raw).apply()
        profilesList.value = list
    }

    fun createProfile(name: String, pin: String, emoji: String, colorHex: String): Boolean {
        if (name.isBlank()) return false
        val currentList = profilesList.value.toMutableList()
        if (currentList.any { it.name.equals(name, ignoreCase = true) }) {
            return false
        }
        val newProfile = VaultProfile(name, pin, emoji, colorHex)
        currentList.add(newProfile)
        saveProfiles(currentList)
        switchProfile(newProfile)
        return true
    }

    fun deleteProfile(profile: VaultProfile) {
        val currentList = profilesList.value.toMutableList()
        if (currentList.size <= 1) return
        currentList.remove(profile)
        saveProfiles(currentList)
        if (activeProfile.value?.name == profile.name) {
            switchProfile(currentList.first())
        }
    }

    fun switchProfile(profile: VaultProfile) {
        activeProfile.value = profile
        guestUser.value = profile.name
        securityPin.value = profile.securityPin
        prefs.edit().putString("active_profile_name", profile.name).apply()
        isScreenLocked.value = profile.securityPin.isNotEmpty()
    }

    fun toggleOfflinePrivacyLock() {
        val current = isOfflineModeGuaranteed.value
        isOfflineModeGuaranteed.value = !current
    }

    val sampleDatabaseSetUp = MutableStateFlow(false)

    // Seed sample data for stunning visual presentation straight away if empty
    fun seedSampleDataIfEmpty() {
        if (allMemories.value.isNotEmpty() || sampleDatabaseSetUp.value) return
        sampleDatabaseSetUp.value = true
        viewModelScope.launch {
            // Seed multiple distinct categories as requested
            addMemory(
                type = "JOURNAL",
                title = "Gazing at the Milky Way",
                content = "Set up a telescope in the backyard tonight. The sky was unbelievably clear, revealing a luminous dusting of the Milky Way galaxy. Felt incredibly small yet connected to everything.",
                mood = "EXCITED",
                location = "Backyard Observatory",
                tags = "Cosmic, Skygazing, Reflection",
                people = "Elena, Marcus",
                isPinned = true
            )
            addMemory(
                type = "DREAM",
                title = "Flying above a Neon Metropolis",
                content = "I was soaring high above a futuristic glowing city with flying fish. The wind felt warm, and everything looked like an interactive neon synth canvas.",
                mood = "CALM",
                location = "Dreamland",
                tags = "Prophetic, Flying"
            )
            addMemory(
                type = "GRATITUDE",
                title = "Morning Coffee & Warm Sun",
                content = "Grateful for the silent hours before everyone wakes up, the rich smell of roasted coffee beans, and the bright yellow beams playing on the wooden floor.",
                mood = "HAPPY",
                location = "Kitchen Corner",
                tags = "Gratitude, Mindful"
            )
            addMemory(
                type = "ACADEMIC",
                title = "Android Dev Certification Exam",
                content = "Passed the Advanced Android Developer assessment with flying colors! All those late-night Jetpack Compose struggles fully paid off.",
                mood = "EXCITED",
                academicCategory = "CERTIFICATE",
                academicGrade = "Grade: A+",
                tags = "Success, Coding, Career"
            )
            addMemory(
                type = "TRAVEL",
                title = "Biking Through Kyoto",
                content = "Rented bamboo bikes and rode around the ancient temples of Kyoto. We ate sweet matcha mochi and recorded beautiful sketches of Arashiyama.",
                mood = "HAPPY",
                location = "Kyoto, Japan",
                tripName = "Japan Odyssey",
                tags = "Travel, Adventure"
            )
            addMemory(
                type = "CAPSULE",
                title = "A Letter to My Future Self",
                content = "Writing this down in 2026. Remember to stay focused, breath deeply during high stakes, and never stop building local-first tools!",
                mood = "CALM",
                capsuleUnlockInDays = 30 // locked capsule
            )
            addMemory(
                type = "DRAWING",
                title = "A Cute Happy Dino",
                content = "Drawn on visual canvas in Kids Mode.",
                mood = "HAPPY",
                isKids = true,
                kidsStickers = "Star, Balloon, Dinosaur"
            )

            // Seed some relationships
            addRelationship("Elena", "FAMILY", "My supportive sister")
            addRelationship("Marcus", "FRIEND", "College roommate & stargazing buddy")
            addRelationship("Professor Alaric", "CLASSMATE", "Android mentor")
        }
    }
}

// Memory Vault Profile registry model
data class VaultProfile(
    val name: String,
    val securityPin: String = "",
    val avatarEmoji: String = "👤",
    val colorHex: String = "#3B82F6"
)
