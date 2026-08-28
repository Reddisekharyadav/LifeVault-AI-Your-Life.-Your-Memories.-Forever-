package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
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
        application.getSharedPreferences("lifevault_profiles", Context.MODE_PRIVATE).edit().clear().commit()
        application.getSharedPreferences("lifevault_local_auth", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = MemoryViewModel(application)
        viewModel.setSecurityPin("")
    }

    @Test
    fun testNavigationTabsClickableAndSwitchViews() {
        composeTestRule.setContent {
            MyApplicationTheme {
                StandardVaultWorkspace(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify Dashboard tab is present
        composeTestRule.onNodeWithText("Dashboard").assertExists()

        // Click Journal Lab tab
        composeTestRule.onNodeWithText("Journal Lab").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Return to Dashboard
        composeTestRule.onNodeWithText("Dashboard").assertExists().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testDashboardElementsAndSearch() {
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
        composeTestRule.setContent {
            MyApplicationTheme {
                ProfileManagerDialog(viewModel = viewModel, onDismiss = {})
            }
        }
        composeTestRule.waitForIdle()

        // Check Email & Storage tab buttons
        composeTestRule.onNodeWithText("Email & Storage").assertExists().performClick()
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

        // Switch to Vault Profiles tab inside the dialog
        composeTestRule.onNodeWithText("Vault Profiles").assertExists().performClick()
        composeTestRule.waitForIdle()

        // Click "Create New Account" button
        composeTestRule.onNodeWithTag("create_new_account_btn")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // Enter new account name
        composeTestRule.onNodeWithTag("new_account_name_input")
            .assertExists()
            .performTextInput("Secret Research Vault")

        composeTestRule.waitForIdle()

        // Click Seal Account button
        composeTestRule.onNodeWithTag("save_new_account_btn")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
    }

    @Test
    fun testSecurityVaultLockerInteractions() {
        composeTestRule.setContent {
            MyApplicationTheme {
                SecurityVaultScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify Locker screen elements render
        composeTestRule.onNodeWithText("100% Offline Vault & Privacy Protection").assertExists()
        composeTestRule.onNodeWithText("🔒 100% LOCAL").assertExists()
    }
}
