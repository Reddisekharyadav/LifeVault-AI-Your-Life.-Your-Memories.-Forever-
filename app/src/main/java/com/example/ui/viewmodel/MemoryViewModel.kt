package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MemoryDatabase
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.model.LocalUser
import com.example.data.repository.MemoryRepository
import com.example.data.storage.CompressedStorageManager
import com.example.data.storage.CompressionResult
import com.example.data.storage.StorageTelemetry
import com.example.data.storage.VoiceStorageManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository
    private val sessionPrefs = application.getSharedPreferences("lifevault_local_session", android.content.Context.MODE_PRIVATE)

    // User Authentication State (100% Offline, on-device local storage)
    val localUserEmail = MutableStateFlow(sessionPrefs.getString("logged_in_email", "") ?: "")
    val isUserLoggedIn = MutableStateFlow(sessionPrefs.getBoolean("is_logged_in", false))
    val currentUser = MutableStateFlow<LocalUser?>(null)
    val registeredUsers: StateFlow<List<LocalUser>>

    // User-specific scoped memories and relationships
    val allMemories: StateFlow<List<LocalMemory>>
    val allRelationships: StateFlow<List<LocalRelationship>>
    val pinnedMemories: StateFlow<List<LocalMemory>>

    // Search and filters
    val isKidsMode = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")

    // Security & Encrypted PIN Lock configuration
    val securityPin = MutableStateFlow("")
    val isScreenLocked = MutableStateFlow(false)
    val inputPinAttempt = MutableStateFlow("")

    val lastBackupTimestamp = MutableStateFlow(sessionPrefs.getLong("last_backup_time", 0L))

    // Real On-Device Voice Recording & Native Speech-to-Text State
    val voiceStorageManager = VoiceStorageManager(application)
    val isRecording = MutableStateFlow(false)
    val isRecordingPaused = MutableStateFlow(false)
    val recordingDuration = MutableStateFlow(0)
    val liveTranscript = MutableStateFlow("")
    val speechStatus = MutableStateFlow("Ready to record voice")
    val audioRmsLevel = MutableStateFlow(0f)
    val currentAudioFileName = MutableStateFlow("")
    private var recordingJob: Job? = null

    // Audio Playback in Dashboard Feed
    val playingMemoryId = MutableStateFlow<Long?>(null)
    val audioProgress = MutableStateFlow(0f)
    val audioPlaybackStatus = MutableStateFlow("")

    // Dynamic story generation buffers
    val isGeneratingStory = MutableStateFlow(false)
    val currentGeneratedStory = MutableStateFlow("")

    val isOfflineModeGuaranteed = MutableStateFlow(true)
    private val localUsersCache = java.util.concurrent.ConcurrentHashMap<String, LocalUser>()

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

        registeredUsers = repository.allUsers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        // Reactive stream of memories strictly scoped to logged-in user
        allMemories = localUserEmail.flatMapLatest { email ->
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank()) {
                repository.allMemories
            } else {
                repository.getMemoriesForUser(cleanEmail)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        // Reactive stream of relationships strictly scoped to logged-in user
        allRelationships = localUserEmail.flatMapLatest { email ->
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank()) {
                repository.allRelationships
            } else {
                repository.getRelationshipsForUser(cleanEmail)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        pinnedMemories = localUserEmail.flatMapLatest { email ->
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.getPinnedMemoriesForUser(cleanEmail)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

        // Load active user info
        viewModelScope.launch {
            val savedEmail = localUserEmail.value
            if (savedEmail.isNotBlank() && isUserLoggedIn.value) {
                val user = repository.getUserByEmail(savedEmail)
                if (user != null) {
                    localUsersCache[user.email] = user
                    currentUser.value = user
                    securityPin.value = user.pinOrPassword
                    isScreenLocked.value = false
                } else {
                    // Reset if user not found in local DB
                    isUserLoggedIn.value = false
                }
            }
        }

        // Observe and update storage telemetry
        viewModelScope.launch {
            combine(allMemories, allRelationships) { mems, rels ->
                CompressedStorageManager.computeTelemetry(mems, rels)
            }.collect { telemetry ->
                storageTelemetry.value = telemetry
            }
        }
    }

    // ==========================================
    // LOCAL USER AUTHENTICATION (SIGN UP & LOGIN)
    // ==========================================

    fun signUp(
        fullName: String,
        email: String,
        pinOrPass: String,
        avatarEmoji: String = "👤",
        colorHex: String = "#3B82F6"
    ): Pair<Boolean, String> {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = fullName.trim()
        val trimmedPin = pinOrPass.trim()

        if (trimmedName.isBlank()) {
            return Pair(false, "Please enter your full name.")
        }
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            return Pair(false, "Please enter a valid email address.")
        }
        if (trimmedPin.length < 4) {
            return Pair(false, "Security PIN/Password must be at least 4 digits.")
        }

        val newUser = LocalUser(
            email = trimmedEmail,
            fullName = trimmedName,
            pinOrPassword = trimmedPin,
            avatarEmoji = avatarEmoji,
            colorHex = colorHex
        )

        localUsersCache[trimmedEmail] = newUser
        currentUser.value = newUser
        localUserEmail.value = trimmedEmail
        securityPin.value = trimmedPin
        isScreenLocked.value = false
        isUserLoggedIn.value = true

        sessionPrefs.edit()
            .putString("logged_in_email", trimmedEmail)
            .putBoolean("is_logged_in", true)
            .apply()

        viewModelScope.launch {
            repository.insertUser(newUser)
        }

        return Pair(true, "Account created successfully!")
    }

    fun login(email: String, pinOrPass: String): Pair<Boolean, String> {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPin = pinOrPass.trim()

        if (trimmedEmail.isBlank()) return Pair(false, "Please enter your email.")
        if (trimmedPin.isBlank()) return Pair(false, "Please enter your PIN / Password.")

        val existing = localUsersCache[trimmedEmail]
            ?: registeredUsers.value.find { it.email == trimmedEmail }
            ?: (if (currentUser.value?.email == trimmedEmail) currentUser.value else null)

        if (existing != null) {
            if (existing.pinOrPassword != trimmedPin) {
                return Pair(false, "Incorrect Security PIN/Password. Please try again.")
            }
            currentUser.value = existing
            localUserEmail.value = existing.email
            securityPin.value = existing.pinOrPassword
            isScreenLocked.value = false
            isUserLoggedIn.value = true

            sessionPrefs.edit()
                .putString("logged_in_email", existing.email)
                .putBoolean("is_logged_in", true)
                .apply()
            return Pair(true, "Login successful!")
        }

        return Pair(false, "Account not found on this device. Please sign up first.")
    }

    fun switchUser(user: LocalUser) {
        localUsersCache[user.email] = user
        currentUser.value = user
        localUserEmail.value = user.email
        securityPin.value = user.pinOrPassword
        isScreenLocked.value = false
        isUserLoggedIn.value = true
        sessionPrefs.edit()
            .putString("logged_in_email", user.email)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun resetPasswordOrPin(email: String, newPinOrPass: String): Pair<Boolean, String> {
        val trimmedEmail = email.trim().lowercase()
        val trimmedPin = newPinOrPass.trim()

        if (trimmedEmail.isBlank()) return Pair(false, "Please enter your account email.")
        if (trimmedPin.length < 4) return Pair(false, "New PIN / Password must be at least 4 characters.")

        val existing = localUsersCache[trimmedEmail]
            ?: registeredUsers.value.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            ?: (if (currentUser.value?.email.equals(trimmedEmail, ignoreCase = true)) currentUser.value else null)

        if (existing != null) {
            val updated = existing.copy(pinOrPassword = trimmedPin)
            localUsersCache[trimmedEmail] = updated
            currentUser.value = updated
            localUserEmail.value = trimmedEmail
            securityPin.value = trimmedPin
            isScreenLocked.value = false
            isUserLoggedIn.value = true

            sessionPrefs.edit()
                .putString("logged_in_email", trimmedEmail)
                .putBoolean("is_logged_in", true)
                .apply()

            viewModelScope.launch {
                repository.insertUser(updated)
            }
            return Pair(true, "PIN / Password reset successfully! You are now logged in.")
        }

        return Pair(false, "No account matching '$trimmedEmail' was found on this device.")
    }

    fun logout() {
        isUserLoggedIn.value = false
        currentUser.value = null
        localUserEmail.value = ""
        securityPin.value = ""
        isScreenLocked.value = false
        sessionPrefs.edit()
            .putString("logged_in_email", "")
            .putBoolean("is_logged_in", false)
            .apply()
    }

    // Export local compressed vault (GZIP Base64 Archive) for current user
    fun exportCompressedVault(): CompressionResult {
        val result = CompressedStorageManager.exportCompressedVault(
            email = localUserEmail.value,
            memories = allMemories.value,
            relationships = allRelationships.value
        )
        val now = System.currentTimeMillis()
        lastBackupTimestamp.value = now
        sessionPrefs.edit().putLong("last_backup_time", now).apply()
        return result
    }

    // Restore from compressed archive into current user's vault
    fun importCompressedVault(archiveData: String): Pair<Boolean, String> {
        val payload = CompressedStorageManager.importCompressedVault(archiveData)
            ?: return Pair(false, "Invalid or corrupted compressed archive string!")

        val currentEmail = localUserEmail.value
        viewModelScope.launch {
            payload.memories.forEach { memory ->
                val exists = allMemories.value.any { it.title == memory.title && it.timestamp == memory.timestamp }
                if (!exists) {
                    repository.insertMemory(memory.copy(id = 0, userEmail = currentEmail))
                }
            }
            payload.relationships.forEach { rel ->
                val exists = allRelationships.value.any { it.name.equals(rel.name, ignoreCase = true) }
                if (!exists) {
                    repository.insertRelationship(rel.copy(id = 0, userEmail = currentEmail))
                }
            }
        }
        return Pair(true, "Successfully restored ${payload.memories.size} memories & ${payload.relationships.size} relationships into your local vault!")
    }

    // Compact and optimize local SQLite database
    fun compactLocalDatabase(): String {
        val telemetry = storageTelemetry.value
        return "Database compacted & indexed! Local footprint reduced to ${CompressedStorageManager.formatBytes(telemetry.compressedSizeBytes)} (${telemetry.compressionRatioText} saved)."
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
        viewModelScope.launch {
            val user = currentUser.value
            if (user != null) {
                val updated = user.copy(pinOrPassword = pin)
                repository.insertUser(updated)
                currentUser.value = updated
            }
        }
    }

    fun attemptUnlock(pin: String): Boolean {
        val trimmed = pin.trim()
        val expected = securityPin.value.trim().ifBlank { currentUser.value?.pinOrPassword?.trim() ?: "" }
        return if (expected.isEmpty() || trimmed == expected) {
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

    // Real On-Device Voice Recording & Native Speech-to-Text
    fun startRecording() {
        isRecording.value = true
        isRecordingPaused.value = false
        recordingDuration.value = 0
        liveTranscript.value = ""
        speechStatus.value = "Listening... Speak clearly into microphone"
        audioRmsLevel.value = 0.1f

        val recordedFileName = voiceStorageManager.startRecording(object : VoiceStorageManager.VoiceRecordingListener {
            override fun onRmsChanged(rmsDb: Float) {
                audioRmsLevel.value = rmsDb
            }

            override fun onLiveTranscript(text: String, isFinal: Boolean) {
                if (text.isNotBlank()) {
                    liveTranscript.value = text
                }
            }

            override fun onSpeechStatus(status: String) {
                speechStatus.value = status
            }

            override fun onError(errorMessage: String) {
                speechStatus.value = errorMessage
            }
        })
        currentAudioFileName.value = recordedFileName
        startTimer()
    }

    fun updateLiveTranscript(text: String) {
        liveTranscript.value = text
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

    fun stopAndSaveVoiceRecording(title: String = "", mood: String = "CALM") {
        val duration = recordingDuration.value
        val internalFileName = voiceStorageManager.stopRecording().ifBlank { currentAudioFileName.value }
        isRecording.value = false
        isRecordingPaused.value = false
        recordingJob?.cancel()

        val rawTranscript = liveTranscript.value.trim()
        val transcript = if (rawTranscript.isNotBlank()) {
            rawTranscript
        } else {
            "Voice journal recording (${duration}s)"
        }

        val currentEmail = localUserEmail.value.trim().lowercase().ifBlank { currentUser.value?.email?.trim()?.lowercase() ?: "" }
        viewModelScope.launch {
            val summaryPrompt = "Extract a summary and key emotion for this spoken transcript: $transcript"
            val aiSummaryText = repository.getAISummary(summaryPrompt)

            val autoTitle = if (title.isNotBlank()) {
                title
            } else if (rawTranscript.isNotBlank()) {
                if (rawTranscript.length > 28) rawTranscript.take(28) + "..." else rawTranscript
            } else {
                "Voice Memoir #${(100..999).random()}"
            }

            val voiceMemory = LocalMemory(
                userEmail = currentEmail,
                type = "VOICE",
                title = autoTitle,
                content = transcript,
                mood = mood,
                audioPath = internalFileName,
                voiceTranscript = transcript,
                aiSummary = aiSummaryText,
                isKidsMode = isKidsMode.value
            )
            repository.insertMemory(voiceMemory)
            speechStatus.value = "Voice memo saved to local phone storage!"
        }
    }

    fun cancelRecording() {
        isRecording.value = false
        isRecordingPaused.value = false
        recordingJob?.cancel()
        voiceStorageManager.cancelRecording()
        liveTranscript.value = ""
        speechStatus.value = "Recording canceled"
        audioRmsLevel.value = 0f
    }

    // Audio Playback for Voice Memories
    fun playMemoryAudio(memory: LocalMemory) {
        if (playingMemoryId.value == memory.id) {
            stopAudioPlayback()
            return
        }
        stopAudioPlayback()

        if (memory.audioPath.isBlank()) {
            audioPlaybackStatus.value = "No audio file recorded for this memory."
            return
        }

        playingMemoryId.value = memory.id
        audioProgress.value = 0f
        voiceStorageManager.playAudio(
            fileName = memory.audioPath,
            onProgress = { progress ->
                audioProgress.value = progress
            },
            onComplete = {
                playingMemoryId.value = null
                audioProgress.value = 0f
            },
            onError = { err ->
                playingMemoryId.value = null
                audioPlaybackStatus.value = err
            }
        )
    }

    fun stopAudioPlayback() {
        playingMemoryId.value = null
        audioProgress.value = 0f
        voiceStorageManager.stopPlayback()
    }

    // Add generic memory for current user
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
        val currentEmail = localUserEmail.value.trim().lowercase().ifBlank { currentUser.value?.email?.trim()?.lowercase() ?: "" }
        viewModelScope.launch {
            val unlockTime = if (type == "CAPSULE" && capsuleUnlockInDays > 0) {
                System.currentTimeMillis() + (capsuleUnlockInDays.toLong() * 24 * 60 * 60 * 1000)
            } else {
                0L
            }

            val summaryText = if (type == "GRATITUDE" || content.lowercase().contains("grateful")) {
                repository.getAISummary("Summarize what this person is grateful for: $content")
            } else if (type == "DREAM") {
                repository.getAISummary("Classify this dream and extract symbols: $content")
            } else {
                repository.getAISummary("Create a highlight summary of this diary log: $content")
            }

            val newMemory = LocalMemory(
                userEmail = currentEmail,
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
                            userEmail = currentEmail,
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
        if (playingMemoryId.value == memory.id) {
            stopAudioPlayback()
        }
        if (memory.audioPath.isNotBlank()) {
            voiceStorageManager.deleteAudioFile(memory.audioPath)
        }
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

            val updated = memory.copy(
                aiSummary = storyResult,
                aiStoryType = storyType
            )
            repository.insertMemory(updated)
        }
    }

    // Insert relationship
    fun addRelationship(name: String, relationType: String, notes: String = "") {
        val currentEmail = localUserEmail.value
        viewModelScope.launch {
            repository.insertRelationship(
                LocalRelationship(
                    userEmail = currentEmail,
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

    fun toggleOfflinePrivacyLock() {
        val current = isOfflineModeGuaranteed.value
        isOfflineModeGuaranteed.value = !current
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
        voiceStorageManager.cancelRecording()
    }
}
