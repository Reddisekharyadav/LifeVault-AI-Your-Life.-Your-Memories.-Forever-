package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.MemoryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LifeVault AI", appName)
    }

    @Test
    fun testUserSignUpLoginAndLockFlow() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MemoryViewModel(application)

        // Create a new customized secure local account
        val (success, msg) = viewModel.signUp(
            fullName = "John Doe",
            email = "john.doe@vault.local",
            pinOrPass = "4321",
            avatarEmoji = "💎",
            colorHex = "#8B5CF6"
        )
        assertTrue(success)

        // Verify John Doe is now active
        val currentUser = viewModel.currentUser.value
        assertNotNull(currentUser)
        assertEquals("John Doe", currentUser?.fullName)
        assertEquals("4321", currentUser?.pinOrPassword)
        assertEquals("💎", currentUser?.avatarEmoji)

        // Lock screen and verify PIN unlock
        viewModel.lockScreen()
        assertTrue(viewModel.isScreenLocked.value)

        // Verify PIN unlock is responsive
        val unlockSuccess = viewModel.attemptUnlock("4321")
        assertTrue(unlockSuccess)
        assertFalse(viewModel.isScreenLocked.value)

        // Try unlocking with an incorrect PIN code
        val unlockFailed = viewModel.attemptUnlock("0000")
        assertFalse(unlockFailed)
    }

    @Test
    fun testAddingCustomMemoriesToVault() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MemoryViewModel(application)

        viewModel.signUp("Test User", "test@vault.local", "1234", "👤", "#3B82F6")

        viewModel.addMemory(
            type = "GRATITUDE",
            title = "A Magical Sunset",
            content = "I saw a radiant golden-purple sky reflecting on the lakes tonight. So grateful to be alive.",
            mood = "HAPPY",
            location = "Lake Shore",
            tags = "Sunset, Gratitude, Serene"
        )

        // Verify memories stream is non-null
        val list = viewModel.allMemories.value
        assertNotNull(list)
    }
}
