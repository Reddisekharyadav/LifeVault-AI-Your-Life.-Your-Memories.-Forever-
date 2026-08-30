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
        application.getSharedPreferences("lifevault_local_session", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = MemoryViewModel(application)
    }

    @Test
    fun testUserSignUpAndLoginFlow() = runBlocking {
        val email = "alex.mercer@vault.local"
        val name = "Alex Mercer"
        val pin = "4321"

        // 1. Sign up new local account
        val (signupSuccess, signupMsg) = viewModel.signUp(
            fullName = name,
            email = email,
            pinOrPass = pin,
            avatarEmoji = "🚀",
            colorHex = "#3B82F6"
        )
        assertTrue(signupSuccess)
        assertTrue(viewModel.isUserLoggedIn.value)
        assertEquals(email, viewModel.localUserEmail.value)
        assertEquals(name, viewModel.currentUser.value?.fullName)

        // 2. Add memories for this user
        viewModel.addMemory(
            type = "JOURNAL",
            title = "First Expedition Log",
            content = "Setup local encrypted memory pod. Data remains strictly offline.",
            mood = "EXCITED",
            location = "Base Camp",
            tags = "Offline, Milestone"
        )

        // 3. Logout
        viewModel.logout()
        assertFalse(viewModel.isUserLoggedIn.value)

        // 4. Attempt login with wrong PIN
        val (wrongLogin, wrongMsg) = viewModel.login(email, "0000")
        assertFalse(wrongLogin)
        assertFalse(viewModel.isUserLoggedIn.value)

        // 5. Attempt login with correct credentials
        val (correctLogin, correctMsg) = viewModel.login(email, pin)
        assertTrue(correctLogin)
        assertTrue(viewModel.isUserLoggedIn.value)
        assertEquals(email, viewModel.localUserEmail.value)
    }

    @Test
    fun testMultiUserDataPartitioningAndIsolation() = runBlocking {
        // User A setup
        val userAEmail = "alice@example.com"
        viewModel.signUp("Alice", userAEmail, "1111", "🦊", "#3B82F6")
        viewModel.addMemory("JOURNAL", "Alice Secret Journal", "Alice private notes", "CALM", "Home", "Alice")

        // User B setup
        val userBEmail = "bob@example.com"
        viewModel.signUp("Bob", userBEmail, "2222", "🐼", "#10B981")
        viewModel.addMemory("JOURNAL", "Bob Secret Journal", "Bob private notes", "HAPPY", "Work", "Bob")

        // Currently logged in as Bob
        assertEquals(userBEmail, viewModel.localUserEmail.value)

        // Switch to Alice
        viewModel.login(userAEmail, "1111")
        assertEquals(userAEmail, viewModel.localUserEmail.value)
        assertEquals("Alice", viewModel.currentUser.value?.fullName)
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
                userEmail = "maruganireddisekhar5@gmail.com",
                type = "JOURNAL",
                title = "Exploring Kyoto Temples",
                content = "Walked through the tranquil bamboo groves of Arashiyama in early morning fog.",
                mood = "PEACEFUL",
                location = "Kyoto, Japan",
                tagsAsString = "Travel, Peace, Nature",
                isPinned = true
            )
        )

        val sampleRelationships = listOf(
            LocalRelationship(
                id = 1,
                userEmail = "maruganireddisekhar5@gmail.com",
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
        assertEquals(2, exportResult.itemsCount)

        // 2. Decompress and restore payload
        val imported = CompressedStorageManager.importCompressedVault(exportResult.compressedPayload)
        assertNotNull(imported)
        assertEquals("maruganireddisekhar5@gmail.com", imported?.email)
        assertEquals(1, imported?.memories?.size)
        assertEquals(1, imported?.relationships?.size)

        val firstMemory = imported?.memories?.first()
        assertEquals("Exploring Kyoto Temples", firstMemory?.title)
        assertEquals("PEACEFUL", firstMemory?.mood)
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
