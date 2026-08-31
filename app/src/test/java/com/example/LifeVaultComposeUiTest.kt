package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MemoryViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class LifeVaultComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("lifevault_local_session", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = MemoryViewModel(application)
        viewModel.setSecurityPin("")
    }

    @Test
    fun testAuthScreenRendersAndAllowsTabSwitching() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Check AuthScreen title and branding
        composeTestRule.onNodeWithText("LifeVault AI").assertExists()
        composeTestRule.onNodeWithText("Private, 100% Offline Local Memory Vault").assertExists()

        // Test Tab Switching between Sign In and Create Account
        composeTestRule.onNodeWithTag("tab_sign_up").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Create Your First Offline Account").assertExists()

        composeTestRule.onNodeWithTag("tab_sign_in").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Sign In to Your Local Vault").assertExists()
    }

    @Test
    fun testNavigationTabsClickableAndSwitchViews() {
        // Sign up first
        viewModel.signUp("Test User", "test@vault.local", "1234", "👤", "#3B82F6")

        composeTestRule.setContent {
            MyApplicationTheme {
                StandardVaultWorkspace(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify Dashboard tab is present
        composeTestRule.onNodeWithText("Dashboard").assertExists()

        // Click Personal Diary tab
        composeTestRule.onNodeWithText("📖 Personal Diary").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Return to Dashboard
        composeTestRule.onNodeWithText("Dashboard").assertExists().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testDashboardElementsAndSearch() {
        // Sign up first
        viewModel.signUp("Test User", "test@vault.local", "1234", "👤", "#3B82F6")

        composeTestRule.setContent {
            MyApplicationTheme {
                DashboardScreen(viewModel = viewModel, onNavigateToCreator = {})
            }
        }
        composeTestRule.waitForIdle()

        // Verify Hero Banner text
        composeTestRule.onNodeWithText("LifeVault AI").assertExists()

        // Find the natural language search text field and enter text
        composeTestRule.onNodeWithTag("natural_ai_search")
            .assertExists()
            .performTextInput("Kyoto Zen")

        composeTestRule.waitForIdle()
    }

    @Test
    fun testProfileManagerDialogInteraction() {
        viewModel.signUp("Alex", "alex@vault.local", "1234", "👤", "#3B82F6")

        composeTestRule.setContent {
            MyApplicationTheme {
                ProfileManagerDialog(viewModel = viewModel, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        // Check Active Vault tab
        composeTestRule.onNodeWithText("Active Vault").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Click Export GZIP button
        composeTestRule.onNodeWithTag("export_archive_btn")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // Click Compact DB button
        composeTestRule.onNodeWithTag("compact_db_btn")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // Switch to Device Accounts tab inside the dialog
        composeTestRule.onNodeWithText("Device Accounts").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Click "Create Another Local Account" button
        composeTestRule.onNodeWithTag("create_new_account_btn")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
    }
}
