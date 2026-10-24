package com.lifevault.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * LocalIntelligenceTest — plain JUnit 4, no Android, no Robolectric.
 *
 * Run in Android Studio: right-click this file -> "Run LocalIntelligenceTest".
 * Or from the command line once the Gradle wrapper exists (see INTEGRATION.md
 * step 13): ./gradlew testDebugUnitTest --tests "com.lifevault.intelligence.*"
 *
 * NOTE: these tests have NOT been run in this environment — there is no JDK
 * or Android SDK here (see INTEGRATION.md "What was NOT verified"). Run them
 * in Android Studio before release and fix any failure there; do not ship
 * the pack with failing tests.
 *
 * Package note: if the app package was renamed (INTEGRATION.md step 0), the
 * package line above and the import of LocalIntelligence must match it.
 */
class LocalIntelligenceTest {

    private fun millis(year: Int, month: Int, day: Int): Long {
        // Calendar months are 0-based: month 1 = January.
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month - 1, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Before
    fun useUtcForDateTests() {
        // On This Day / streak logic uses the device timezone; pinning it to
        // UTC makes these tests deterministic wherever they run.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    // --- (b) Mood: negation ------------------------------------------------

    @Test
    fun mood_negationFlipsHappy() {
        val result = classifyMood("I am not happy today")
        assertTrue("Negated 'happy' must not classify as HAPPY", result.topMood != Mood.HAPPY)
        assertTrue("Negated word must be reported", "happy" in result.negatedWords)
        assertFalse("Negated word must not count as contributing", "happy" in result.contributingWords)
    }

    @Test
    fun mood_notSadDoesNotClassifyAsSad() {
        val result = classifyMood("I am not sad at all, I feel wonderful")
        assertEquals(Mood.HAPPY, result.topMood)
    }

    @Test
    fun mood_intensityAndTopTwo() {
        val result = classifyMood("I am very happy and so excited, really happy")
        assertEquals(Mood.HAPPY, result.topMood)
        assertEquals(Mood.EXCITED, result.secondMood)
        assertTrue(result.confidence in 0.0..1.0)
        assertTrue(result.confidence > 0.5)
    }

    @Test
    fun mood_newMoodsMatchWebV2() {
        assertEquals(Mood.GRATEFUL, classifyMood("I am so grateful and thankful today").topMood)
        assertEquals(Mood.REFLECTIVE, classifyMood("I keep thinking and remembering those days").topMood)
        assertEquals(Mood.ANXIOUS, classifyMood("I feel anxious and worried about tomorrow").topMood)
        assertEquals(Mood.CALM, classifyMood("A calm and peaceful quiet morning").topMood)
    }

    @Test
    fun mood_gratefulIsNotHappy() {
        // ROUND 2: GRATEFUL words were moved out of HAPPY.
        val result = classifyMood("I am grateful for this")
        assertEquals(Mood.GRATEFUL, result.topMood)
    }

    @Test
    fun mood_noMoodWordsIsUnknownCalmWithZeroConfidence() {
        val result = classifyMood("Went to the shop and bought milk")
        assertEquals(Mood.CALM, result.topMood)
        assertEquals(0.0, result.confidence, 0.0001)
    }

    // --- (a) Search ranking -------------------------------------------------

    @Test
    fun search_titleBeatsBody() {
        val titleMatch = SearchableMemory(id = 1, title = "Beach day", content = "nothing relevant here")
        val bodyMatch = SearchableMemory(id = 2, title = "A quiet afternoon", content = "we went to the beach and swam")
        val results = searchMemories("beach", listOf(bodyMatch, titleMatch))
        assertEquals(2, results.size)
        assertEquals(1L, results[0].memory.id)
        assertTrue("title" in results[0].matchedFields)
    }

    @Test
    fun search_synonymExpandsQuery() {
        val memory = SearchableMemory(id = 1, title = "Seaside walk", content = "waves and sand")
        val results = searchMemories("beach", listOf(memory))
        assertEquals(1, results.size)
    }

    @Test
    fun search_emptyQueryReturnsNothing() {
        val memory = SearchableMemory(id = 1, title = "Beach day")
        assertTrue(searchMemories("", listOf(memory)).isEmpty())
        assertTrue(searchMemories("the and", listOf(memory)).isEmpty())
    }

    // --- (c) Extractive summary ---------------------------------------------

    @Test
    fun summary_quotesOriginalTextOnly() {
        val text = "We went to the beach. The beach was windy and bright. " +
            "We built a sandcastle at the beach. Then we went home."
        val summary = summarizeExtractive(text, maxSentences = 2)
        assertEquals(2, summary.size)
        for (sentence in summary) {
            assertTrue("Summary must quote the original verbatim: $sentence", text.contains(sentence))
        }
        // The beach-heavy sentences must win over the generic last sentence.
        assertTrue(summary.any { it.contains("beach") })
    }

    @Test
    fun summary_shortTextReturnsEverything() {
        val text = "One short memory."
        assertEquals(listOf("One short memory."), summarizeExtractive(text, maxSentences = 3))
        assertTrue(summarizeExtractive("", 3).isEmpty())
    }

    // --- (d) On This Day / streaks / counts ---------------------------------

    @Test
    fun onThisDay_matchesMonthAndDayOtherYearOnly() {
        val today = millis(2026, 10, 24)
        val sameDayLastYear = SearchableMemory(id = 1, title = "Last year", timestamp = millis(2025, 10, 24))
        val sameDayTwoYearsAgo = SearchableMemory(id = 2, title = "Two years ago", timestamp = millis(2024, 10, 24))
        val differentDay = SearchableMemory(id = 3, title = "Wrong day", timestamp = millis(2025, 10, 23))
        val todayItself = SearchableMemory(id = 4, title = "Today", timestamp = millis(2026, 10, 24))
        val result = onThisDay(listOf(sameDayLastYear, sameDayTwoYearsAgo, differentDay, todayItself), today)
        assertEquals(listOf(1L, 2L), result.map { it.memory.id })
        assertEquals(listOf(1, 2), result.map { it.yearsAgo })
    }

    @Test
    fun streaks_currentAndLongest() {
        // 3-day run ending yesterday + an older isolated day.
        val memories = listOf(
            SearchableMemory(timestamp = millis(2026, 10, 21)),
            SearchableMemory(timestamp = millis(2026, 10, 22)),
            SearchableMemory(timestamp = millis(2026, 10, 23)),
            SearchableMemory(timestamp = millis(2026, 10, 10))
        )
        val result = computeStreaks(memories, millis(2026, 10, 24))
        assertEquals(3, result.currentStreakDays)
        assertEquals(3, result.longestStreakDays)
    }

    @Test
    fun streaks_emptyIsZero() {
        assertEquals(StreakResult(0, 0), computeStreaks(emptyList(), millis(2026, 10, 24)))
    }

    @Test
    fun mostMentioned_countsPeoplePlacesTags() {
        val memories = listOf(
            SearchableMemory(people = listOf("Ama", "Ben"), location = "Goa", tags = listOf("travel")),
            SearchableMemory(people = listOf("ama"), location = "Goa", tags = listOf("travel", "beach")),
            SearchableMemory(people = listOf("Ben"), location = "Delhi", tags = listOf("travel"))
        )
        val counts = mostMentioned(memories)
        assertEquals("Ama" to 2, counts.people.first())
        assertEquals("Goa" to 2, counts.places.first())
        assertEquals("travel" to 3, counts.tags.first())
    }

    @Test
    fun monthlyCounts_groupsByMonth() {
        val memories = listOf(
            SearchableMemory(timestamp = millis(2026, 10, 1)),
            SearchableMemory(timestamp = millis(2026, 10, 24)),
            SearchableMemory(timestamp = millis(2026, 9, 5))
        )
        assertEquals(mapOf("2026-09" to 1, "2026-10" to 2), monthlyCounts(memories))
    }

    // --- (e) Tag suggestions --------------------------------------------------

    @Test
    fun tagSuggestions_hashtagsPeopleTopicsButNeverAutoApply() {
        val suggestions = suggestTags(
            text = "A wonderful #beach day with Ama, swimming at the beach all day",
            existingTags = listOf("beach"),
            knownPeople = listOf("Ama", "Ben")
        )
        val tags = suggestions.map { it.tag }
        // Existing tag is not re-suggested; hashtag #beach is already a tag.
        assertFalse("beach" in tags)
        assertTrue("ama" in tags)
        assertTrue(suggestions.first { it.tag == "ama" }.source == TagSource.PERSON)
        // This function only returns suggestions — the caller must apply them
        // on tap. There is deliberately no "apply" side effect to test here.
    }

    // --- Supporting helpers ---------------------------------------------------

    @Test
    fun formatMmSs_formatsVoiceTimer() {
        assertEquals("00:00", formatMmSs(0))
        assertEquals("00:45", formatMmSs(45))
        assertEquals("02:05", formatMmSs(125))
        assertEquals("00:00", formatMmSs(-5))
    }

    @Test
    fun cloudConsent_defaultsOffAndAudioSeparate() {
        val consent = CloudConsent()
        assertFalse(consent.canSend(ContentKind.TEXT))
        assertFalse(consent.canSend(ContentKind.AUDIO))
        val textOnly = CloudConsent(textAllowed = true, audioAllowed = false)
        assertTrue(textOnly.canSend(ContentKind.TEXT))
        assertFalse("Audio must never ride on a text-only consent", textOnly.canSend(ContentKind.AUDIO))
    }
}
