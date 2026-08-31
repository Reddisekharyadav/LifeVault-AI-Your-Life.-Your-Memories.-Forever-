package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.GeminiClient
import com.example.ui.screens.PersonalDiaryScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MemoryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PersonalDiaryVoiceToTextTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var application: Application
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.getSharedPreferences("lifevault_local_session", Context.MODE_PRIVATE).edit().clear().commit()
        viewModel = MemoryViewModel(application)
        viewModel.signUp("Test User", "test@diary.local", "1234", "📖", "#3B82F6")
    }

    @Test
    fun testVoiceToTextPipelineAndEmotionPrediction() = runBlocking {
        // 1. Test speech transcript input with grateful emotion
        val spokenWordsGrateful = "I had a wonderful coffee with my family today. Truly grateful for all their love and support."
        viewModel.updateLiveTranscript(spokenWordsGrateful)
        assertEquals(spokenWordsGrateful, viewModel.liveTranscript.value)

        val gratefulResult = GeminiClient.formatVoiceToPersonalDiary(rawTranscript = spokenWordsGrateful, durationSec = 10)

        // Verify emotion was accurately categorized as GRATEFUL
        assertEquals("GRATEFUL", gratefulResult.predictedEmotion)
        assertTrue("Title should not be blank", gratefulResult.title.isNotBlank())
        assertTrue("Content must contain Date header", gratefulResult.diaryContent.contains("📅"))
        assertTrue("Content must contain Mood tag", gratefulResult.diaryContent.contains("🌿 Mood:"))
        assertTrue("Content must contain bullet points with spoken words", gratefulResult.diaryContent.contains("• I had a wonderful coffee"))
        assertTrue("Content must contain bullet points for second sentence", gratefulResult.diaryContent.contains("• Truly grateful"))

        // 2. Test speech transcript input with excited emotion
        val spokenWordsExcited = "We won the championship game today! It was an amazing and awesome victory."
        val excitedResult = GeminiClient.formatVoiceToPersonalDiary(rawTranscript = spokenWordsExcited, durationSec = 15)
        assertEquals("EXCITED", excitedResult.predictedEmotion)
        assertTrue(excitedResult.diaryContent.contains("• We won the championship"))
        assertTrue(excitedResult.diaryContent.contains("• It was an amazing and awesome victory"))

        // 3. Test speech transcript input with tired emotion
        val spokenWordsTired = "Exhausted after a long 14 hour work shift. Need to sleep and rest deeply."
        val tiredResult = GeminiClient.formatVoiceToPersonalDiary(rawTranscript = spokenWordsTired, durationSec = 8)
        assertEquals("TIRED", tiredResult.predictedEmotion)
        assertTrue(tiredResult.diaryContent.contains("• Exhausted after a long"))
    }

    @Test
    fun testPersonalDiaryTemplateGenerationForAllEmotions() = runBlocking {
        val testCases = mapOf(
            "Thank you so much for this blessed day" to "GRATEFUL",
            "We won the tournament and celebrated an awesome victory" to "EXCITED",
            "Such a happy and joyful moment with lots of sweet smiles" to "HAPPY",
            "Feeling so sad and lonely missing my hometown" to "SAD",
            "Completely exhausted, need rest and sleep immediately" to "TIRED",
            "I feel so frustrated and angry about the delayed train" to "ANGRY",
            "Sitting quietly by the lake, pondering and reflecting on life" to "REFLECTIVE",
            "Breathing in fresh air with calm and peaceful state" to "CALM"
        )

        testCases.forEach { (sampleSpeech, expectedEmotion) ->
            val result = GeminiClient.formatVoiceToPersonalDiary(rawTranscript = sampleSpeech, durationSec = 5)
            assertEquals("Emotion for '$sampleSpeech' should match", expectedEmotion, result.predictedEmotion)
            assertTrue("Template must contain date header", result.diaryContent.contains("📅"))
            assertTrue("Template must contain mood header", result.diaryContent.contains("🌿 Mood:"))
            assertTrue("Template must contain bullet point prefix", result.diaryContent.contains("• "))
            assertFalse("Template must never contain placeholder demo text", result.diaryContent.contains("Dear Diary,\n\n• Woke up feeling"))
        }
    }

    @Test
    fun testPersonalDiaryUiEditorAndTemplateFormatting() {
        composeTestRule.setContent {
            MyApplicationTheme {
                PersonalDiaryScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify UI components are rendered
        composeTestRule.onNodeWithText("Personal Daily Diary").assertExists()
        composeTestRule.onNodeWithTag("tab_diary_editor").assertExists()
        composeTestRule.onNodeWithTag("tab_past_diaries").assertExists()
        composeTestRule.onNodeWithTag("system_voice_speech_btn").assertExists()
        composeTestRule.onNodeWithTag("diary_mic_btn").assertExists()

        // 2. Scroll to and Click Insert Diary Template button
        composeTestRule.onNodeWithTag("lazy_diary_column")
            .performScrollToNode(hasTestTag("insert_template_btn"))
        composeTestRule.onNodeWithTag("insert_template_btn")
            .assertExists()
            .performClick()
        composeTestRule.waitForIdle()

        // 3. Scroll to Diary Editor text field and enter text
        composeTestRule.onNodeWithTag("lazy_diary_column")
            .performScrollToNode(hasTestTag("diary_text_editor_field"))
        composeTestRule.onNodeWithTag("diary_text_editor_field")
            .assertExists()
            .performTextInput("Completed morning workout and read 20 pages of my book.")

        composeTestRule.waitForIdle()

        // 4. Set Diary Title
        composeTestRule.onNodeWithTag("lazy_diary_column")
            .performScrollToNode(hasTestTag("diary_title_field"))
        composeTestRule.onNodeWithTag("diary_title_field")
            .assertExists()
            .performTextInput("Productive Sunday Morning")

        composeTestRule.waitForIdle()

        // 5. Scroll to and Click Save Diary Entry
        composeTestRule.onNodeWithTag("lazy_diary_column")
            .performScrollToNode(hasTestTag("save_diary_entry_btn"))
        composeTestRule.onNodeWithTag("save_diary_entry_btn")
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // 6. Scroll back to top and verify tab switches / is clickable
        composeTestRule.onNodeWithTag("lazy_diary_column")
            .performScrollToNode(hasTestTag("tab_past_diaries"))
        composeTestRule.onNodeWithTag("tab_past_diaries").assertExists().performClick()
        composeTestRule.waitForIdle()
    }
}
