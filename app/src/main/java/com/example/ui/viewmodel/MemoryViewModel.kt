package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MemoryDatabase
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.repository.MemoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemoryRepository
    val allMemories: StateFlow<List<LocalMemory>>
    val allRelationships: StateFlow<List<LocalRelationship>>

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
    }

    // Navigation and screen management lists
    val isKidsMode = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    
    // Security & Encrypted PIN Lock configuration
    val securityPin = MutableStateFlow("") // empty means no PIN configured
    val isScreenLocked = MutableStateFlow(false)
    val inputPinAttempt = MutableStateFlow("")

    // Simulated Voice-First Experience State Machine
    val isRecording = MutableStateFlow(false)
    val isRecordingPaused = MutableStateFlow(false)
    val recordingDuration = MutableStateFlow(0)
    private var recordingJob: Job? = null

    // Dynamic story generation buffers
    val isGeneratingStory = MutableStateFlow(false)
    val currentGeneratedStory = MutableStateFlow("")

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

    // Local Mock Profiles & Login for Offline Mode
    val guestUser = MutableStateFlow("Guest Explorer")
    val isGoogleSignedIn = MutableStateFlow(false)

    fun googleSignInSimulate() {
        isGoogleSignedIn.value = true
        guestUser.value = "Google User (Synced)"
    }

    fun googleSignOutSimulate() {
        isGoogleSignedIn.value = false
        guestUser.value = "Guest Explorer"
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
