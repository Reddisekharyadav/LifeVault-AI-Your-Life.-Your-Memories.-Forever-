package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.LocalMemory
import com.example.data.model.LocalRelationship
import com.example.data.storage.CompressedStorageManager
import com.example.ui.viewmodel.MemoryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LifeVaultFullSystemTest {

    private lateinit var application: Application
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("lifevault_profiles", Context.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("lifevault_local_auth", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = MemoryViewModel(application)
        viewModel.setSecurityPin("")
    }

    @Test
    fun testMasterEmailAccountBindingAndLocalAuth() {
        // Test binding the master user email offline
        val testEmail = "maruganireddisekhar5@gmail.com"
        val success = viewModel.loginWithEmail(testEmail, "1234")
        assertTrue(success)
        assertEquals(testEmail, viewModel.localUserEmail.value)
        assertTrue(viewModel.isEmailAuthenticated.value)
        assertEquals("1234", viewModel.securityPin.value)

        // Invalid email should fail
        val invalidSuccess = viewModel.loginWithEmail("invalidemail", "")
        assertFalse(invalidSuccess)
    }

    @Test
    fun testSecurityPinLockAndUnlockFlow() {
        // Configure a 4-digit PIN lock
        viewModel.setSecurityPin("9876")
        assertEquals("9876", viewModel.securityPin.value)
        assertTrue(viewModel.isScreenLocked.value)

        // Attempt invalid PIN
        val wrongAttempt = viewModel.attemptUnlock("0000")
        assertFalse(wrongAttempt)
        assertTrue(viewModel.isScreenLocked.value)

        // Attempt correct PIN
        val correctAttempt = viewModel.attemptUnlock("9876")
        assertTrue(correctAttempt)
        assertFalse(viewModel.isScreenLocked.value)
    }

    @Test
    fun testGzipCompressionAndLosslessDecompressionEngine() {
        val sampleMemories = listOf(
            LocalMemory(
                id = 1,
                type = "JOURNAL",
                title = "Exploring Kyoto Temples",
                content = "Walked through the tranquil bamboo groves of Arashiyama in early morning fog. The stone lanterns were covered in moss.",
                mood = "PEACEFUL",
                location = "Kyoto, Japan",
                tagsAsString = "Travel, Peace, Nature",
                isPinned = true
            ),
            LocalMemory(
                id = 2,
                type = "MILESTONE",
                title = "Defended Master's Thesis",
                content = "Presented research on decentralized neural computing. Awarded distinction honors by the committee.",
                mood = "TRIUMPHANT",
                location = "Auditorium Hall",
                tagsAsString = "Academic, Milestone, Victory",
                isPinned = false
            )
        )

        val sampleRelationships = listOf(
            LocalRelationship(
                id = 1,
                name = "Elena Vance",
                relationType = "FAMILY",
                notes = "Always calls on Sunday afternoons"
            )
        )

        // 1. Export as GZIP Base64 payload
        val exportResult = CompressedStorageManager.exportCompressedVault(
            email = "maruganireddisekhar5@gmail.com",
            memories = sampleMemories,
            relationships = sampleRelationships
        )

        assertNotNull(exportResult.compressedPayload)
        assertTrue(exportResult.compressedPayload.isNotBlank())
        assertTrue(exportResult.rawBytes > 0)
        assertTrue(exportResult.compressedBytes > 0)
        assertEquals(3, exportResult.itemsCount)

        // 2. Decompress and restore payload
        val imported = CompressedStorageManager.importCompressedVault(exportResult.compressedPayload)
        assertNotNull(imported)
        assertEquals("maruganireddisekhar5@gmail.com", imported?.email)
        assertEquals(2, imported?.memories?.size)
        assertEquals(1, imported?.relationships?.size)

        val firstMemory = imported?.memories?.first()
        assertEquals("Exploring Kyoto Temples", firstMemory?.title)
        assertEquals("PEACEFUL", firstMemory?.mood)
        assertEquals("Kyoto, Japan", firstMemory?.location)
    }

    @Test
    fun testStorageTelemetryComputation() {
        val memories = listOf(
            LocalMemory(
                id = 1,
                type = "GRATITUDE",
                title = "Morning Coffee & Sunshine",
                content = "Grateful for the warm morning breeze and fresh aromatic coffee on the porch.",
                mood = "CALM",
                location = "Home Patio",
                tagsAsString = "Gratitude, Morning, Coffee"
            )
        )
        val relationships = listOf(
            LocalRelationship(
                id = 1,
                name = "Sarah",
                relationType = "FRIEND",
                notes = "College roommate"
            )
        )

        val telemetry = CompressedStorageManager.computeTelemetry(memories, relationships)
        assertTrue(telemetry.rawSizeBytes > 0)
        assertTrue(telemetry.compressedSizeBytes > 0)
        assertEquals(2, telemetry.totalEntries)
        assertNotNull(telemetry.compressionRatioText)
    }

    @Test
    fun testMemoryCreationAndSearchFiltering() = runBlocking {
        // Insert memories into the repository via ViewModel
        viewModel.addMemory(
            type = "TRAVEL",
            title = "Himalayan Sunrise Trek",
            content = "Watched the golden sun crest over snowcapped peaks at 4000 meters elevation.",
            mood = "EXCITED",
            location = "Annapurna Base Camp",
            tags = "Trek, Mountain, Adventure"
        )

        // Search for Trek
        viewModel.updateSearchQuery("Himalayan")
        assertEquals("Himalayan", viewModel.searchQuery.value)

        // Clear search
        viewModel.updateSearchQuery("")
        assertEquals("", viewModel.searchQuery.value)
    }

    @Test
    fun testVoiceRecordingStateMachine() {
        // Start recording
        viewModel.startRecording()
        assertTrue(viewModel.isRecording.value)
        assertFalse(viewModel.isRecordingPaused.value)

        // Pause recording
        viewModel.pauseRecording()
        assertTrue(viewModel.isRecording.value)
        assertTrue(viewModel.isRecordingPaused.value)

        // Resume recording
        viewModel.resumeRecording()
        assertTrue(viewModel.isRecording.value)
        assertFalse(viewModel.isRecordingPaused.value)

        // Stop recording and save
        viewModel.stopAndSaveVoiceRecording("Audio Journal #1", "PEACEFUL")
        assertFalse(viewModel.isRecording.value)
        assertFalse(viewModel.isRecordingPaused.value)
    }

    @Test
    fun testKidsModeAndOfflinePrivacyToggles() {
        val initialKidsMode = viewModel.isKidsMode.value
        viewModel.toggleKidsMode()
        assertEquals(!initialKidsMode, viewModel.isKidsMode.value)

        val initialPrivacy = viewModel.isOfflineModeGuaranteed.value
        viewModel.toggleOfflinePrivacyLock()
        assertEquals(!initialPrivacy, viewModel.isOfflineModeGuaranteed.value)
    }

    @Test
    fun testDatabaseCompactionFeedback() {
        val message = viewModel.compactLocalDatabase()
        assertNotNull(message)
        assertTrue(message.contains("Database compacted"))
    }
}
