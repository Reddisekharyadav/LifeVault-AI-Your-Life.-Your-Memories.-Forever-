package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class LocalMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                       // "JOURNAL", "VOICE", "DREAM", "ACADEMIC", "TRAVEL", "GRATITUDE", "CAPSULE", "DRAWING"
    val title: String,
    val content: String,                    // Text or key description
    val timestamp: Long = System.currentTimeMillis(),
    val mood: String = "CALM",              // "HAPPY", "SAD", "ANGRY", "EXCITED", "CALM", "TIRED"
    val location: String = "",
    val tagsAsString: String = "",          // Comma-separated
    val peopleAsString: String = "",        // Comma-separated
    val isPinned: Boolean = false,          // Memory Vault (pinned forever)
    val capsuleUnlockTime: Long = 0,        // Unlock timestamp for Memory Capsules (0 if not capsule)
    val mediaPath: String = "",             // Photo path, Drawing base64, etc.
    val audioPath: String = "",             // Path to local voice recording
    val voiceTranscript: String = "",       // Local / AI generated transcript
    val aiSummary: String = "",             // Smart highlighting description
    val aiStoryType: String = "NONE",       // "CHILDREN_STORY", "BLOG", "BOOK_CHAPTER", "AUTOBIOGRAPHY"
    val kidsStickerList: String = "",       // Stored stickers (comma separated)
    val academicCategory: String = "OTHER",  // "EXAM", "PROJECT", "CERTIFICATE", "INTERNSHIP"
    val academicGrade: String = "",         // Exam scores or grade text
    val tripName: String = "",              // For grouped Travel Journals
    val isKidsMode: Boolean = false,        // If generated within Kids Mode UI
    val familyAlbumName: String = ""        // Family album name
) {
    val tags: List<String>
        get() = if (tagsAsString.isBlank()) emptyList() else tagsAsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val people: List<String>
        get() = if (peopleAsString.isBlank()) emptyList() else peopleAsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val stickers: List<String>
        get() = if (kidsStickerList.isBlank()) emptyList() else kidsStickerList.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val isCapsuleLocked: Boolean
        get() = type == "CAPSULE" && capsuleUnlockTime > System.currentTimeMillis()
}
