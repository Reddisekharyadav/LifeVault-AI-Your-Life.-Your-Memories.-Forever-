package com.lifevault.intelligence

/*
 * LocalIntelligence.kt — LifeVault AI v2, Android improvement pack (Round 1)
 *
 * Pure Kotlin. No Android imports, no Room, no Compose, no network, no
 * coroutines. That is deliberate: this file must be unit-testable on a plain
 * JVM with JUnit, and every function below runs 100% on-device.
 *
 * DROP-IN NOTE — package name:
 *   The v1 app still uses the prototype package `com.example`. INTEGRATION.md
 *   step 0 renames it to a real product identity (for example
 *   `com.lifevault.app`). When you copy this file into the app, change the
 *   package line above to match whatever the app's renamed package is, and
 *   update the import in the test file the same way. Do NOT ship two copies
 *   under two different packages.
 *
 * WHAT THIS FILE DOES NOT DO (by design):
 *   - It never calls Gemini, Firebase, or any cloud API.
 *   - It never writes to a database, a file, or the clipboard.
 *   - It never auto-applies tags, moods, or summaries to a memory. Every
 *     function returns a suggestion / ranking / result; the UI layer decides
 *     whether to show it, and the user decides whether to keep it.
 *   - It never invents text. The summarizer only quotes sentences that already
 *     exist in the entry (see summarizeExtractive).
 *   - It contains no API keys, secrets, or credentials.
 */

// ---------------------------------------------------------------------------
// Shared text helpers
// ---------------------------------------------------------------------------

/** Words that carry no search / mood / summary signal. Kept small on purpose. */
val STOPWORDS: Set<String> = setOf(
    "a", "an", "the", "and", "or", "but", "if", "then", "else", "when", "while",
    "of", "at", "by", "for", "with", "about", "into", "through", "after",
    "over", "between", "out", "against", "during", "without", "before",
    "under", "around", "among", "to", "from", "in", "on", "is", "am", "are",
    "was", "were", "be", "been", "being", "have", "has", "had", "having",
    "do", "does", "did", "doing", "i", "me", "my", "myself", "we", "our",
    "ours", "you", "your", "yours", "he", "him", "his", "she", "her", "hers",
    "it", "its", "they", "them", "their", "theirs", "this", "that", "these",
    "those", "there", "here", "what", "which", "who", "whom", "how", "why",
    "so", "too", "very", "just", "also", "than", "then", "as", "can", "could",
    "will", "would", "should", "may", "might", "must", "shall"
)

/**
 * Synonym groups. The key is the canonical term; the values are words that
 * should also match it. Only used for SEARCH (query expansion), never to
 * rewrite the user's text. Keep this list short and obvious — a wrong synonym
 * here silently changes search results, so never add a guess.
 */
val SYNONYM_GROUPS: Map<String, List<String>> = mapOf(
    "happy" to listOf("joyful", "glad", "cheerful", "delighted", "content"),
    "sad" to listOf("unhappy", "down", "blue", "gloomy", "sorrowful"),
    "angry" to listOf("furious", "mad", "annoyed", "irritated", "frustrated"),
    "excited" to listOf("thrilled", "eager", "pumped", "enthusiastic"),
    "tired" to listOf("exhausted", "sleepy", "weary", "drained"),
    "calm" to listOf("peaceful", "relaxed", "serene", "tranquil"),
    "beach" to listOf("seaside", "shore", "coast"),
    "family" to listOf("relatives", "kin"),
    "birthday" to listOf("bday"),
    "travel" to listOf("trip", "journey", "vacation", "holiday"),
    "friend" to listOf("friends", "buddy", "pal")
)

/** Canonical term for a word, or the word itself when it is not a synonym. */
fun canonicalTerm(word: String): String {
    if (word in SYNONYM_GROUPS) return word
    for ((canonical, synonyms) in SYNONYM_GROUPS) {
        if (word in synonyms) return canonical
    }
    return word
}

/**
 * Stemming-lite: strips common English suffixes, guarded by a minimum length
 * so short words are never mangled ("bus" stays "bus", "sad" stays "sad").
 * This is NOT a real stemmer and must not be presented as one. It only helps
 * "running" match "run" and "memories" match "memory" in local search.
 */
fun stemLite(word: String): String {
    var w = word
    if (w.length > 6 && w.endsWith("ing")) w = w.dropLast(3)
    else if (w.length > 5 && w.endsWith("ies")) w = w.dropLast(3) + "y"
    else if (w.length > 5 && w.endsWith("ed")) w = w.dropLast(2)
    else if (w.length > 4 && w.endsWith("es")) w = w.dropLast(2)
    else if (w.length > 4 && w.endsWith("s") && !w.endsWith("ss")) w = w.dropLast(1)
    // Collapse a doubled final letter left by suffix stripping ("runn" -> "run").
    if (w.length > 3 && w[w.length - 1] == w[w.length - 2]) w = w.dropLast(1)
    return w
}

/**
 * Tokenizes text for search / mood / summary work: lowercase, split on anything
 * that is not a letter or digit, drop stopwords, stem-lite, map synonyms to
 * their canonical term. Hashtags lose the '#' so "#beach" matches "beach".
 */
fun tokenize(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return text.lowercase()
        .replace("#", " ")
        .split(Regex("[^a-z0-9']+"))
        .map { it.trim('\'') }
        .filter { it.isNotBlank() && it !in STOPWORDS }
        // Retest fix: canonicalise BEFORE stemming, then again after.
        // Stemming first broke synonyms ("delighted" -> "delight", which is
        // not in the synonym map, so it no longer matched "happy";
        // "exhausted" -> "exhaust" no longer matched "tired"). Canonical
        // first means both the query and the document land on the same
        // canonical term, and the second canonical pass is a no-op safety
        // net for stems that happen to be synonyms themselves.
        .map { canonicalTerm(stemLite(canonicalTerm(it))) }
}

/** Raw tokens in order, stopwords kept — needed for the mood negation window. */
fun tokenizeRaw(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return text.lowercase()
        .replace("#", " ")
        .split(Regex("[^a-z0-9']+"))
        .map { it.trim('\'') }
        .filter { it.isNotBlank() }
}

// ---------------------------------------------------------------------------
// (a) Field-weighted BM25-like search
// ---------------------------------------------------------------------------

/**
 * Minimal, dependency-free view of a memory for search / insights. The UI layer
 * maps a real LocalMemory to this (title, content, voiceTranscript, tags,
 * people, location, timestamp) — this file never imports the Room entity.
 */
data class SearchableMemory(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val transcript: String = "",
    val tags: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val location: String = "",
    val timestamp: Long = 0L
)

data class SearchResult(
    val memory: SearchableMemory,
    val score: Double,
    /** Field names that actually matched, e.g. ["title", "tags"]. */
    val matchedFields: List<String>
)

/** Field weights. Title beats body on purpose — see the unit test. */
object SearchWeights {
    const val TITLE = 5.0
    const val TAGS = 3.0
    const val PEOPLE = 3.0
    const val LOCATION = 2.0
    const val CONTENT = 1.0
    const val TRANSCRIPT = 1.0
}

/**
 * BM25-like ranking over a small, already-loaded list of memories.
 *
 * How it works, honestly: for each query term, IDF = ln(1 + (N - df + 0.5) /
 * (df + 0.5)) rewards rare terms; TF saturation tf * (k1 + 1) / (tf + k1)
 * stops one repeated word from dominating; a light length normalisation
 * (b = 0.75) stops very long entries from winning by word count alone.
 * Each field's contribution is multiplied by its weight above.
 *
 * This replaces v1's substring search. It does NOT replace the database:
 * INTEGRATION.md step 3 has the Room query fetch candidates first (a LIKE
 * query is fine as a coarse pre-filter), and this function does the ranking
 * in memory. Do not run this over the whole vault on the main thread for a
 * large library — call it from Dispatchers.Default and only over the
 * current user's memories (see the blank-email fix in INTEGRATION.md).
 *
 * Returns results sorted by score descending. Terms that match nothing
 * contribute nothing; an empty query returns an empty list, never everything.
 */
fun searchMemories(query: String, memories: List<SearchableMemory>): List<SearchResult> {
    val queryTerms = tokenize(query).distinct()
    if (queryTerms.isEmpty() || memories.isEmpty()) return emptyList()

    val docs = memories.map { memory ->
        mapOf(
            "title" to tokenize(memory.title),
            "tags" to memory.tags.flatMap { tokenize(it) },
            "people" to memory.people.flatMap { tokenize(it) },
            "location" to tokenize(memory.location),
            "content" to tokenize(memory.content),
            "transcript" to tokenize(memory.transcript)
        )
    }
    val weights = mapOf(
        "title" to SearchWeights.TITLE,
        "tags" to SearchWeights.TAGS,
        "people" to SearchWeights.PEOPLE,
        "location" to SearchWeights.LOCATION,
        "content" to SearchWeights.CONTENT,
        "transcript" to SearchWeights.TRANSCRIPT
    )
    val docLengths = docs.map { fields -> fields.values.sumOf { it.size } }
    val avgLength = docLengths.average().takeIf { it > 0 } ?: 1.0
    val n = memories.size.toDouble()
    val k1 = 1.2
    val b = 0.75

    // Document frequency per query term, across all fields of a memory.
    val df = queryTerms.associateWith { term ->
        docs.count { fields -> fields.values.any { term in it } }
    }

    return memories.mapIndexed { index, memory ->
        val fields = docs[index]
        var score = 0.0
        val matched = mutableListOf<String>()
        for (term in queryTerms) {
            val termDf = df[term] ?: 0
            if (termDf == 0) continue
            val idf = kotlin.math.ln(1.0 + (n - termDf + 0.5) / (termDf + 0.5))
            for ((field, tokens) in fields) {
                val tf = tokens.count { it == term }
                if (tf == 0) continue
                if (field !in matched) matched.add(field)
                val lengthNorm = 1.0 - b + b * (docLengths[index] / avgLength)
                val tfComponent = tf * (k1 + 1.0) / (tf + k1 * lengthNorm)
                score += (weights[field] ?: 1.0) * idf * tfComponent
            }
        }
        SearchResult(memory, score, matched)
    }.filter { it.score > 0.0 }.sortedByDescending { it.score }
}

// ---------------------------------------------------------------------------
// (b) Mood classifier v2 — negation window + intensity + top-2 + confidence
// ---------------------------------------------------------------------------

/** ROUND 2: the same 8 moods as web v2 (Happy, Calm, Grateful, Excited, Tired,
 *  Sad, Reflective, Anxious). The Round-1 set had only 6 and included ANGRY,
 *  which web v2 does not use. ANGRY was removed deliberately for NEW entries:
 *  anger words in new text ("furious", "mad", "annoyed") fall through to
 *  no-mood (unknown) rather than being forced into a label the web app
 *  cannot display. That is NOT the rule for STORED legacy values: entries
 *  already saved with mood ANGRY must use the explicit mapping in
 *  INTEGRATION.md Step 4a — display "Angry (legacy)", count them in a
 *  legacy bucket, never silently re-label them as ANXIOUS, SAD, or CALM,
 *  and never silently drop them. See legacyMoodDisplay() below.
 *  GRATEFUL words were moved out of HAPPY so "grateful" no longer classifies
 *  as HAPPY. */
enum class Mood { HAPPY, CALM, GRATEFUL, EXCITED, TIRED, SAD, REFLECTIVE, ANXIOUS }


private val MOOD_WORDS: Map<Mood, Set<String>> = mapOf(
    Mood.HAPPY to setOf("happy", "joy", "joyful", "glad", "delight", "smile", "smil", "laugh", "love", "lov", "lovely", "wonderful", "great", "beautiful", "proud", "cheerful", "content"),
    Mood.CALM to setOf("calm", "peace", "peaceful", "relax", "serene", "quiet", "still", "gentle", "tranquil", "restful", "breathe"),
    Mood.GRATEFUL to setOf("grateful", "thankful", "blessed", "appreciat", "gratitude"),
    Mood.EXCITED to setOf("excit", "thrill", "amazing", "awesome", "fantastic", "incredible", "eager", "celebrat", "party", "adventure", "wow"),
    Mood.TIRED to setOf("tired", "exhaust", "sleepy", "weary", "drain", "fatigue", "sleepless", "overwhelm", "burnout"),
    Mood.SAD to setOf("sad", "unhappy", "cry", "crying", "tears", "lonely", "miss", "missing", "grief", "loss", "lost", "hurt", "heartbroken", "down", "gloomy", "sorrow"),
    Mood.REFLECTIVE to setOf("reflect", "thinking", "remember", "nostalgia", "ponder", "reminisc") // NOTE: web v2 also matches the two-word phrase 'looking back'; this single-token classifier cannot, so that phrase alone will not classify here.,
    Mood.ANXIOUS to setOf("anxious", "worri", "nervous", "stress", "afraid", "panic", "anxiety")
)

/**
 * Words checked against stemmed tokens. Retest fix: the mood words
 * themselves must be stemmed before comparing, because classifyMood()
 * stems the input token first. Comparing a stemmed token to an unstemmed
 * word silently broke real cases: "thinking" stems to "think" but the
 * list held "thinking"; "blessed" stems to "bles" but the list held
 * "blessed"; "still" stems to "stil", "miss" to "mis", "stress" to
 * "stres", "amazing" to "amaz", "anxious" to "anxiou" — none of those
 * matched their own list entry. Stemming both sides fixes them, because
 * the same stemLite() is applied to both. The "smil"/"lov" stems in
 * HAPPY exist for the same reason: "smiling" stems to "smil" and
 * "loving" starts with "lov", so the full forms alone did not match.
 */
private fun moodOfToken(token: String): Mood? {
    for ((mood, words) in MOOD_WORDS) {
        if (words.any { val stemmedWord = stemLite(it); token == stemmedWord || token.startsWith(stemmedWord) }) return mood
    }
    return null
}

/**
 * Display label for a mood value already stored in the database.
 *
 * Explicit legacy mapping (see INTEGRATION.md Step 4a, which is the
 * authoritative rule — do not change this function without changing it
 * there too):
 *  - "ANGRY" (any case) -> "Angry (legacy)". It is preserved as a
 *    legacy label, counted separately, and offered no new-entry choice.
 *    It is NEVER converted to ANXIOUS, SAD, CALM, or "unknown" silently.
 *  - Any other stored value -> returned trimmed and unchanged, so the
 *    function never silently re-labels a mood the user already saved.
 * New entries must be created from the 8 Mood values only; this function
 * does not create moods, it only labels stored ones.
 */
fun legacyMoodDisplay(storedMood: String): String {
    return if (storedMood.trim().uppercase() == "ANGRY") "Angry (legacy)" else storedMood.trim()
}

private val NEGATIONS = setOf("not", "never", "no", "n't", "dont", "don't", "cant", "can't", "wont", "won't", "isnt", "isn't", "wasnt", "wasn't", "didnt", "didn't")
private val INTENSIFIERS = mapOf("very" to 1.5, "extremely" to 1.8, "so" to 1.5, "really" to 1.4, "super" to 1.5, "incredibly" to 1.7)
private val SOFTENERS = mapOf("slightly" to 0.6, "bit" to 0.6, "little" to 0.7, "somewhat" to 0.7, "kind" to 0.7)

/** Negation looks back this many tokens: "not at all happy" still negates. */
const val NEGATION_WINDOW = 3

data class MoodResult(
    /** Best guess, or CALM with confidence 0.0 when no mood words were found. */
    val topMood: Mood,
    val secondMood: Mood?,
    /** 0.0–1.0. Top score's share of all positive mood scores. Low = uncertain. */
    val confidence: Double,
    /** Words that contributed, in text order. Shown in UI as "why" evidence. */
    val contributingWords: List<String>,
    /** Mood words that were negated ("not happy") — never counted positively. */
    val negatedWords: List<String>,
    /** Raw per-mood scores, for debugging / UI detail. Negative = negated. */
    val scores: Map<Mood, Double>
)

/**
 * Classifies mood from text. Rules, in order:
 *  1. A mood word preceded within NEGATION_WINDOW tokens by a negation word
 *     has its score flipped (negative): "I am not happy" must NOT classify
 *     as HAPPY. Negated words are reported separately and never add to a mood.
 *  2. An intensifier / softener immediately before the mood word scales it.
 *  3. The top two moods by score are returned with a confidence share.
 *  4. No mood words at all -> CALM, confidence 0.0, second = null. That is an
 *     explicit "unknown", not a claim the user is calm — the UI must not
 *     present confidence 0.0 as a finding.
 *
 * This is a lexicon heuristic, not a model and not a diagnosis. Never label
 * it "AI mood detection" in the UI without that caveat.
 */
fun classifyMood(text: String): MoodResult {
    val raw = tokenizeRaw(text)
    val scores = mutableMapOf<Mood, Double>()
    val contributing = mutableListOf<String>()
    val negated = mutableListOf<String>()
    for (i in raw.indices) {
        val stemmed = stemLite(raw[i])
        val mood = moodOfToken(stemmed) ?: continue
        val windowStart = maxOf(0, i - NEGATION_WINDOW)
        val isNegated = raw.subList(windowStart, i).any { it in NEGATIONS }
        var weight = 1.0
        if (i > 0) {
            weight = INTENSIFIERS[raw[i - 1]] ?: SOFTENERS[raw[i - 1]] ?: 1.0
        }
        if (isNegated) {
            scores[mood] = (scores[mood] ?: 0.0) - weight
            negated.add(raw[i])
        } else {
            scores[mood] = (scores[mood] ?: 0.0) + weight
            contributing.add(raw[i])
        }
    }
    val positive = scores.filterValues { it > 0 }
    if (positive.isEmpty()) {
        return MoodResult(Mood.CALM, null, 0.0, contributing, negated, scores)
    }
    val ranked = positive.entries.sortedByDescending { it.value }
    val total = positive.values.sum()
    val confidence = (ranked.first().value / total).coerceIn(0.0, 1.0)
    return MoodResult(
        topMood = ranked.first().key,
        secondMood = ranked.getOrNull(1)?.key,
        confidence = confidence,
        contributingWords = contributing,
        negatedWords = negated,
        scores = scores
    )
}

// ---------------------------------------------------------------------------
// (c) Extractive summarizer — quotes original text only
// ---------------------------------------------------------------------------

private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+")

/**
 * Returns up to [maxSentences] sentences, in their ORIGINAL order, chosen by
 * keyword frequency (how often a sentence's non-stopwords appear in the whole
 * entry) plus a small position bonus for early sentences.
 *
 * Guarantee: every returned sentence is a verbatim substring of [text].
 * Nothing is paraphrased, generated, or invented. If the entry has fewer
 * sentences than requested, all of them are returned. Blank text returns an
 * empty list. This is the replacement for v1's canned Gemini fallback text:
 * when cloud help is off (the default), the UI shows this summary and labels
 * it "From your own words" — never "AI summary".
 */
fun summarizeExtractive(text: String, maxSentences: Int = 3): List<String> {
    if (text.isBlank() || maxSentences <= 0) return emptyList()
    val sentences = text.trim().split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotBlank() }
    if (sentences.size <= maxSentences) return sentences
    val frequencies = mutableMapOf<String, Int>()
    for (sentence in sentences) {
        for (token in tokenize(sentence)) frequencies[token] = (frequencies[token] ?: 0) + 1
    }
    val scored = sentences.mapIndexed { index, sentence ->
        val keywordScore = tokenize(sentence).sumOf { frequencies[it] ?: 0 }.toDouble()
        val positionBonus = when (index) {
            0 -> 0.5
            1 -> 0.25
            else -> 0.0
        }
        index to (keywordScore + positionBonus)
    }
    val chosen = scored.sortedByDescending { it.second }.take(maxSentences).map { it.first }.sorted()
    return chosen.map { sentences[it] }
}

/** Convenience join for UI display. Empty list -> empty string, never a fallback sentence. */
fun summarizeExtractiveText(text: String, maxSentences: Int = 3): String =
    summarizeExtractive(text, maxSentences).joinToString(" ")

// ---------------------------------------------------------------------------
// (d) True insights — On This Day, streaks, most-mentioned, monthly counts
// ---------------------------------------------------------------------------

data class OnThisDayEntry(val memory: SearchableMemory, val yearsAgo: Int)

/** Calendar fields for a timestamp, in the device's default timezone. */
private fun calendarParts(timestamp: Long): Triple<Int, Int, Int> {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    return Triple(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH),
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

/**
 * TRUE On This Day: memories from the same month + day as [todayTimestamp]
 * but a different year. This replaces v1's `memory.id % 2L == 0L ||
 * tags.contains("Cosmic")` filter, which showed arbitrary entries and had
 * nothing to do with the date. Sorted newest-first (smallest yearsAgo first).
 * Entries from today itself are excluded (they are not flashbacks).
 */
fun onThisDay(memories: List<SearchableMemory>, todayTimestamp: Long): List<OnThisDayEntry> {
    val (todayYear, todayMonth, todayDay) = calendarParts(todayTimestamp)
    return memories.mapNotNull { memory ->
        val (year, month, day) = calendarParts(memory.timestamp)
        if (month == todayMonth && day == todayDay && year != todayYear) {
            OnThisDayEntry(memory, yearsAgo = todayYear - year)
        } else null
    }.sortedBy { it.yearsAgo }
}

data class StreakResult(
    /** Consecutive days with at least one memory, ending today or yesterday. */
    val currentStreakDays: Int,
    val longestStreakDays: Int
)

/** Day key (days since epoch, device timezone) for streak / monthly math. */
private fun dayKey(timestamp: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis / 86_400_000L
}

/**
 * Streaks from the set of days that have at least one memory. A day with no
 * entry breaks a streak. "Current" counts back from today; if today has no
 * entry yet, it counts back from yesterday (so a user is not shown 0 on a
 * day they simply haven't written in yet). Empty input -> 0 / 0.
 */
fun computeStreaks(memories: List<SearchableMemory>, todayTimestamp: Long): StreakResult {
    val days = memories.map { dayKey(it.timestamp) }.toSet()
    if (days.isEmpty()) return StreakResult(0, 0)
    val sorted = days.sorted()
    var longest = 1
    var run = 1
    for (i in 1 until sorted.size) {
        run = if (sorted[i] == sorted[i - 1] + 1) run + 1 else 1
        if (run > longest) longest = run
    }
    val today = dayKey(todayTimestamp)
    var cursor = if (today in days) today else today - 1
    var current = 0
    while (cursor in days) {
        current++
        cursor--
    }
    return StreakResult(current, longest)
}

data class MentionCounts(
    val people: List<Pair<String, Int>>,
    val places: List<Pair<String, Int>>,
    val tags: List<Pair<String, Int>>
)

/**
 * Most-mentioned people, places (location field), and tags, each sorted by
 * count descending, then name ascending for stable ties. Matching is
 * case-insensitive and trimmed ("Ama" and "ama " count as one person); the
 * returned name is the first spelling seen. Empty fields are ignored.
 */
fun mostMentioned(memories: List<SearchableMemory>, topN: Int = 5): MentionCounts {
    fun count(values: List<String>): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        val display = mutableMapOf<String, String>()
        for (raw in values) {
            val key = raw.trim().lowercase()
            if (key.isBlank()) continue
            counts[key] = (counts[key] ?: 0) + 1
            display.putIfAbsent(key, raw.trim())
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(topN)
            .map { (display[it.key] ?: it.key) to it.value }
    }
    return MentionCounts(
        people = count(memories.flatMap { it.people }),
        places = count(memories.map { it.location }.filter { it.isNotBlank() }),
        tags = count(memories.flatMap { it.tags })
    )
}

/**
 * Monthly counts as "yyyy-MM" -> count, sorted by month ascending. Uses the
 * device timezone, matching how the user experiences dates in the app.
 */
fun monthlyCounts(memories: List<SearchableMemory>): Map<String, Int> {
    val counts = sortedMapOf<String, Int>()
    for (memory in memories) {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = memory.timestamp
        val key = "%04d-%02d".format(
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1
        )
        counts[key] = (counts[key] ?: 0) + 1
    }
    return counts
}

// ---------------------------------------------------------------------------
// (e) Tag suggestions — suggestions only, never auto-applied
// ---------------------------------------------------------------------------

enum class TagSource { HASHTAG, PERSON, TOPIC }

data class TagSuggestion(val tag: String, val source: TagSource)

/**
 * Suggests tags the user may want to add. Sources, in order:
 *  1. Hashtags actually written in the text (#beach -> "beach").
 *  2. Known people (from [knownPeople]) whose names appear in the text.
 *  3. Frequent topic words (length >= 4, non-stopword) not already a tag.
 *
 * CONTRACT: this function only RETURNS suggestions. The caller must show
 * them as tappable chips and only add the ones the user taps. Auto-applying
 * suggestions would silently rewrite the user's own organisation of their
 * memories, so no code path in this file does that, and INTEGRATION.md
 * forbids the UI layer from doing it silently either.
 */
fun suggestTags(
    text: String,
    existingTags: List<String> = emptyList(),
    knownPeople: List<String> = emptyList()
): List<TagSuggestion> {
    val existing = existingTags.map { it.trim().lowercase() }.toSet()
    val suggestions = linkedMapOf<String, TagSuggestion>()

    fun offer(tag: String, source: TagSource) {
        val clean = tag.trim().lowercase()
        if (clean.isBlank() || clean in existing || clean in suggestions) return
        suggestions[clean] = TagSuggestion(clean, source)
    }

    // 1. Hashtags.
    Regex("#([A-Za-z0-9_]+)").findAll(text).forEach { offer(it.groupValues[1], TagSource.HASHTAG) }

    // 2. Known people mentioned by name (case-insensitive, whole-word-ish).
    val lowerText = text.lowercase()
    for (person in knownPeople) {
        val name = person.trim()
        if (name.isBlank()) continue
        if (Regex("(^|[^a-z])" + Regex.escape(name.lowercase()) + "([^a-z]|$)").containsMatchIn(lowerText)) {
            offer(name, TagSource.PERSON)
        }
    }

    // 3. Frequent topic words.
    val frequencies = mutableMapOf<String, Int>()
    for (token in tokenize(text)) {
        if (token.length >= 4) frequencies[token] = (frequencies[token] ?: 0) + 1
    }
    frequencies.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(5)
        .forEach { offer(it.key, TagSource.TOPIC) }

    return suggestions.values.toList()
}

// ---------------------------------------------------------------------------
// Cloud-consent gate + voice timer formatting (supporting INTEGRATION.md)
// ---------------------------------------------------------------------------

enum class ContentKind { TEXT, AUDIO }

/**
 * The ONLY cloud decision this pack makes, and it defaults to OFF.
 * [textAllowed] and [audioAllowed] are separate toggles, stored per user,
 * default false, and must be set by an explicit user action in Settings —
 * never by installing, updating, signing in, or accepting a generic prompt.
 * Audio is never sent unless [audioAllowed] is true, even when text is allowed.
 * If the toggles do not exist yet in the app, treat both as false.
 */
data class CloudConsent(val textAllowed: Boolean = false, val audioAllowed: Boolean = false) {
    fun canSend(kind: ContentKind): Boolean = when (kind) {
        ContentKind.TEXT -> textAllowed
        ContentKind.AUDIO -> audioAllowed
    }
}

/** Formats a voice-timer duration as mm:ss ("02:05"). Negative -> "00:00". */
fun formatMmSs(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}
