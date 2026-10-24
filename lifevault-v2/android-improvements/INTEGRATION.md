# LifeVault AI v2 — Android Improvement Pack, Round 1: Integration Guide

**For:** a developer opening the v1 Android project in Android Studio.
**Scope:** a drop-in improvement pack, NOT a rewrite. The v1 `MainActivity.kt`
(3,366 lines) stays where it is; this pack fixes the worst correctness,
privacy, and data-loss bugs around it and adds one new, fully offline,
unit-testable module. Do not try to split `MainActivity.kt` in this round —
that is Round 2 work, and a half-split monolith is worse than a known one.

**What this pack does NOT add (deliberately):** no server account, no cloud
sync, no analytics, no ads, no new network dependency. If a future request
asks for any of those, it is a new decision with its own privacy review, not
something to slip in while "improving" a vault app.

**What was NOT compiled or tested — read this first:**
This pack was written in an environment with **no JDK, no Android SDK, and
no Gradle wrapper** (verified: `which java kotlinc gradle` returns nothing,
and the v1 repo has no `gradlew` file). Therefore:
- `LocalIntelligence.kt` and `LocalIntelligenceTest.kt` have **not been
  compiled and the tests have not been run**. They are written as plain
  Kotlin + JUnit 4 with no Android imports, specifically so they *can* be
  run, but you must run them in Android Studio before release.
- Every Room / Compose / Gradle snippet below is **unverified against the
  v1 build**. Paste one step at a time, build after each step, and do not
  batch all steps and then debug the pile.
- File paths, class names, and line references below were checked against
  the v1 source in `~/workspace/lifevault` (DAO, database, entity,
  repository, ViewModel, manifest, build file). Line numbers will drift as
  you edit; use the identifiers, not just the numbers.

**Round 2 update (mood set + status):** `LocalIntelligence.kt` now uses
the same 8 moods as web v2 (HAPPY, CALM, GRATEFUL, EXCITED, TIRED, SAD,
REFLECTIVE, ANXIOUS); the Round-1 ANGRY value was removed deliberately
because web v2 has no Angry label — see
`ROUND2_NOTES.md` for the mapping guidance and for what is still
unverified (no JDK/SDK here, so Kotlin is still uncompiled) and the
SQLCipher next steps (not done, not claimed).

**Before you start:** work on a copy/branch of `~/workspace/lifevault`.
Never edit the original in place without a way back, and never commit a
keystore, a `.env` file, or any API key. This pack contains no keys and
needs none — the Gemini path it gates is opt-in and keyless until the user
supplies their own key through the app's existing Secrets flow, if ever.

---

## Step 0 — Give the app a real product identity (prototype names must go)

**Why:** v1 ships as package `com.example`, Gradle project name
`"My Application"`, and applicationId `com.aistudio.lifevault.mzjkrw`.
That is a template identity. It confuses Play listing, backup, and data
ownership, and `com.example` must never ship.

**Do:**
1. In Android Studio: **Refactor → Rename** the package `com.example` to
   the real identity, e.g. `com.lifevault.app` (pick the real one and use
   it everywhere; this guide uses `com.lifevault.app` as the example).
   Update every `package` line, import, and the `namespace` in
   `app/build.gradle.kts`.
2. In `settings.gradle.kts`: `rootProject.name = "LifeVault"` (not
   `"My Application"`).
3. In `app/build.gradle.kts` `defaultConfig`: set `applicationId` to the
   real id (e.g. `com.lifevault.app`). **Warning:** changing
   `applicationId` on an already-installed app makes Android treat it as a
   different app — existing v1 installs will not upgrade in place and their
   local vault will not migrate automatically. Decide before release:
   either keep the old id for continuity and rename only the package /
   project name, or accept a clean-install break and tell users to export
   first (Step 9's format). Do not change the id silently.
4. Update `android:label` / `app_name` in `res/values/strings.xml` if it
   still says anything generic, and the manifest's activity name if the
   refactor did not move it.
5. Copy `LocalIntelligence.kt` into
   `app/src/main/java/com/lifevault/app/intelligence/` (adjust to your
   chosen package) and change its `package` line to match. Copy
   `LocalIntelligenceTest.kt` into the matching
   `app/src/test/java/.../intelligence/` folder. The pack's folders use
   `com/lifevault/intelligence/` as a neutral placeholder — the package
   line, not the folder, is what the compiler checks, but keep them in sync.

**Verify:** clean build passes, app launches, existing vault still loads
(if you kept the applicationId) before touching anything else.

---

## Step 1 — DAO: add `@Update`, a real edit path, and indices

**Files:** `data/db/MemoryDao.kt`, `data/model/LocalMemory.kt`,
`data/repository/MemoryRepository.kt`, `ui/viewmodel/MemoryViewModel.kt`
**Why:** v1 has no `@Update`. Editing a memory today can only be done by
delete + re-insert, which changes its id, breaks "On This Day" year math,
and loses the pinned / capsule association in between. There are also no
indices on the columns every query filters by.

**Do:**
1. In `MemoryDao.kt`, add (next to the existing `@Insert` / `@Delete`):
```kotlin
@Update
suspend fun updateMemory(memory: LocalMemory)

@Query("SELECT * FROM memories WHERE userEmail = :userEmail AND " +
    "(title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' " +
    "OR voiceTranscript LIKE '%' || :query || '%' OR tagsAsString LIKE '%' || :query || '%' " +
    "OR peopleAsString LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%') " +
    "ORDER BY timestamp DESC")
fun searchMemoriesForUserFlow(userEmail: String, query: String): Flow<List<LocalMemory>>
```
2. In `LocalMemory.kt`, add indices to the `@Entity` annotation:
```kotlin
@Entity(
    tableName = "memories",
    indices = [Index(value = ["userEmail", "timestamp"]), Index(value = ["userEmail", "type"])]
)
```
   This changes the schema, so it **must** ship with the Step 2 migration
   (version bump). Do not add the indices without the migration.
3. In `MemoryRepository.kt`, add:
```kotlin
suspend fun updateMemory(memory: LocalMemory) = memoryDao.updateMemory(memory)
fun searchMemoriesForUser(userEmail: String, query: String) =
    memoryDao.searchMemoriesForUserFlow(userEmail, query)
```
4. In `MemoryViewModel.kt`, add an edit function that preserves the id:
```kotlin
fun updateMemory(edited: LocalMemory) {
    viewModelScope.launch { repository.updateMemory(edited) }
}
```
   Wire every edit screen to this. Delete the delete + re-insert edit path
   wherever it exists; if you find one you are unsure about, keep it
   visibly marked and fix it before release — a silent id change is a data
   bug, not a cosmetic one.

**Verify:** edit a memory's title, close and reopen the app, and confirm
the id, timestamp, pinned state, and capsule unlock time are unchanged.

---

## Step 2 — Replace `fallbackToDestructiveMigration` with a real migration + export prompt

**Files:** `data/db/MemoryDatabase.kt`
**Why:** v1 calls `.fallbackToDestructiveMigration()`. Any schema change —
including Step 1's indices — **wipes every user's vault without warning**.
That is the single most dangerous line in v1 for a memory app.

**Do:**
1. Bump `@Database` version from `2` to `3`.
2. Replace `.fallbackToDestructiveMigration()` with a real migration:
```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_userEmail_timestamp` " +
            "ON `memories` (`userEmail`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_userEmail_type` " +
            "ON `memories` (`userEmail`, `type`)")
    }
}
// In getDatabase(): .addMigrations(MIGRATION_2_3)   // and NOTHING destructive
```
   (Import `androidx.room.migration.Migration` and
   `androidx.sqlite.db.SupportSQLiteDatabase`.)
3. **Never** re-add a destructive fallback "temporarily". If a future
   migration is missing, the correct behaviour is: crash loudly in testing
   and, in production, show the user an export prompt *before* any
   destructive action — never wipe first and explain later.
4. Before shipping the migration, add a first-run-after-upgrade step in
   the UI: "Your vault format is being updated. Export a backup first?"
   with the Step 9 SAF export as the button. The export must succeed
   before the migration runs on the user's real data in your own manual
   test (install v1, add memories, install the new build, confirm nothing
   is lost).

**Verify:** install v1, create memories, upgrade to the new build, confirm
all memories survive with ids intact. Then test a fresh install separately.

---

## Step 3 — Replace substring-only search with ranked local search

**Files:** `MainActivity.kt` (search UI), `MemoryViewModel.kt`
**Why:** v1 search is substring matching. "beach" and "beaches" do not
match each other, a title match counts the same as a passing mention in a
long entry, and there is no ranking at all.

**Do:**
1. Add a ViewModel search that uses the Step 1 DAO query as a coarse
   candidate fetch, then ranks with `LocalIntelligence.searchMemories`
   on `Dispatchers.Default` (never the main thread):
```kotlin
// Map LocalMemory -> SearchableMemory (title, content, voiceTranscript,
// tags, people, location, timestamp), then:
val ranked = withContext(Dispatchers.Default) {
    searchMemories(query, candidates.map { it.toSearchable() })
}
```
2. Show results in score order, with the matched fields (title / tags /
   people / location / content / transcript) available for a small
   "Matched: title, tags" hint. Do not show raw scores to users — they are
   ranking internals, not meaningful numbers.
3. Keep the DAO LIKE query as the pre-filter only. Do not present it as
   the search: if a user searches "beach" and a memory says "seaside",
   the synonym map in `LocalIntelligence` can rank it, but only if the
   candidate fetch found it — for synonym terms, also fetch candidates
   for the canonical term's synonyms, or accept that synonym recall is
   limited to fetched candidates and say so in release notes. Do not
   claim "smart search finds everything" until this is measured.

**Verify:** the unit test `search_titleBeatsBody` passes, and in the app a
title match outranks a body-only match for the same word.

---

## Step 4 — Replace fake insights with true, computed insights

**Files:** `MainActivity.kt` (the "Flashbacks: On This Day" block around
line 1405, and the "Mood Analytics & Insights" card)
**Why:** v1's On This Day is
`memory.id % 2L == 0L || memory.tagsAsString.contains("Cosmic")` — the
code itself comments that it is "simulating" an anniversary comparison.
It shows arbitrary entries. The mood card counts stored mood labels, which
is fine as a count, but any "insight" text around it that is not computed
from the user's data is fabrication and must go.

**Do:**
1. **Delete** the `id % 2` / `"Cosmic"` filter entirely. Replace it with:
```kotlin
val flashbacks = onThisDay(memories.map { it.toSearchable() }, System.currentTimeMillis())
```
   and render each entry with its `yearsAgo` ("1 year ago", "3 years ago").
   If the list is empty, show "No memories from this day in past years yet"
   — never fill the gap with a random memory.
2. Replace any hardcoded insight sentence with computed values only:
   `computeStreaks(...)`, `mostMentioned(...)`, `monthlyCounts(...)`,
   and the stored mood counts. Example of an honest insight line:
   "You wrote on 3 days in a row. Most mentioned: Ama (12), Goa (8)."
   If there is no data, say there is no data yet.
3. Mood for new entries: use `classifyMood(text)` as a *suggestion* shown
   next to the entry ("Suggested mood: Happy — why: 'wonderful', 'laugh'"),
   with the user able to change it. Never overwrite a mood the user set
   themselves, and never present a confidence below ~0.5 as a finding —
   show "Not sure" instead. Remember the classifier is a word list, not a
   diagnosis.

**Verify:** create a memory dated (by editing its timestamp in a test
build, or by waiting) to the same month/day last year and confirm it — and
only it — appears in On This Day; confirm an `id % 2 == 0` memory from a
different date does not.

---

## Step 5 — Cloud-consent gate: canned Gemini fallback OUT, extractive summary IN

**Files:** `data/api/GeminiClient.kt`, `data/repository/MemoryRepository.kt`,
`MainActivity.kt` (anywhere a summary / story / diary conversion is shown),
Settings screen (new toggles)
**Why:** v1 claims "Zero cloud exposure" (MainActivity line ~2620) while
`GeminiClient` sends content to a cloud model, and when that call fails the
UI shows canned fallback text as if it were a result. Both halves are
wrong: the claim contradicts the behaviour, and the fallback misleads.

**Do:**
1. **Default path (no consent):** every summary / story / diary-conversion
   surface shows the local extractive summary and labels it exactly:
   "From your own words". Implementation:
```kotlin
val summary = summarizeExtractiveText(entry.content.ifBlank { entry.voiceTranscript })
```
   If the text is blank, show nothing — never a canned sentence.
2. **Delete every canned fallback string** that pretends to be a generated
   result. A failed cloud call must surface as "Cloud help is off / failed —
   here is a summary from your own words instead", not as fluent invented
   text.
3. **Consent gate (opt-in only, per-entry, text vs audio separate):**
   add two Settings toggles, both default OFF, stored per user
   (e.g. in the session prefs or a new `lifevault_cloud_consent` prefs file,
   **not** in the vault database, and excluded from backup — add the file
   to both backup rules if you name it differently):
   - "Allow cloud help for text I choose" (off by default)
   - "Allow cloud help for audio I choose" (off by default, separate)
   Gate every call:
```kotlin
if (!consent.canSend(ContentKind.TEXT)) { /* local summary only */ return }
if (kind == ContentKind.AUDIO && !consent.canSend(ContentKind.AUDIO)) { /* refuse */ return }
```
   Consent is **per entry**: even with a toggle on, the UI asks "Send this
   entry to cloud help?" for that entry, and the user can say no. A toggle
   is not a blank cheque. Audio is **never** sent on a text-only consent.
4. Until the toggles exist and are tested, treat all consent as OFF. That
   means the app is offline-only in this round, and HONEST_README.md says
   so.
5. See Step 13 for the dead Firebase dependency: removing it does not
   remove `GeminiClient`'s network calls — check what `GeminiClient`
   actually uses (Retrofit/OkHttp) and gate or remove that path too. Do
   not assume removing one dependency removes the cloud path.

**Verify:** with both toggles off, turn on airplane mode and confirm every
summary still works; with toggles off, confirm no network request is made
(logcat / a proxy check in testing, not a guess). With text on and audio
off, confirm an audio conversion is refused, not silently downgraded.

---

## Step 6 — Fix the voice timer, pause, and live-transcript overwrite

**Files:** `ui/viewmodel/MemoryViewModel.kt` (timer, pause, transcript),
`MainActivity.kt` (timer display)
**Why:** (a) The timer display must be `mm:ss`, not a raw second count.
(b) Pause today cancels the timer job, so "pause" is really "stop and
lose the count" — the fake pause from the review. (c) Live transcript
results overwrite (`liveTranscript.value = text`) instead of appending,
so earlier speech is lost.

**Do:**
1. Display: `formatMmSs(recordingDurationSeconds)` everywhere the timer
   is shown. `recordingDuration` must be a *seconds* value — if v1's
   value is milliseconds, divide by 1000 first; check the timer code
   rather than assuming.
2. Real pause: keep two values — `accumulatedSeconds` (frozen on pause)
   and a running job that only ticks while `isRecording && !isPaused`.
   Sketch:
```kotlin
private var accumulatedSeconds = 0L
private var tickStartMillis = 0L
fun pauseRecording() {
    accumulatedSeconds += (System.currentTimeMillis() - tickStartMillis) / 1000
    recordingJob?.cancel()
    isRecordingPaused.value = true
}
fun resumeRecording() {
    tickStartMillis = System.currentTimeMillis()
    isRecordingPaused.value = false
    startTimer() // ticks from accumulatedSeconds, never resets it
}
```
   And `startRecording()` (fresh recording) is the only place that resets
   `accumulatedSeconds` to 0.
3. Transcript append: keep `finalSegments: List<String>` and
   `interimText: String`. On a *final* recognition result, append it to
   `finalSegments`; on an *interim* result, replace only `interimText`.
   Display = `finalSegments.joinToString(" ") + interimText`. Never assign
   a new interim result over the accumulated finals. The existing
   `updateLiveTranscript(text)` helper must be changed to the same rule or
   deleted — a helper that overwrites is the bug in a convenient form.
4. Saving a voice memo must save the accumulated transcript, not just the
   last interim fragment.

**Verify:** record, pause for 10 seconds, resume, confirm the timer did
not advance during the pause and did not reset; speak two separate
sentences and confirm both survive in the saved transcript.

---

## Step 7 — Delete confirmation + Undo, and draft autosave

**Files:** `MainActivity.kt` (delete actions), `MemoryViewModel.kt`
**Why:** deleting a memory today is immediate and irreversible in the UI.
A memory app must not destroy the thing it exists to preserve on one
accidental tap. Separately, losing a half-written entry to a rotation /
process death is the same class of loss, smaller.

**Do:**
1. Delete flow: tapping delete shows a confirmation dialog
   ("Delete 'title'? This cannot be undone from the cloud — there is no
   cloud copy.") Only on confirm, delete — and immediately show a
   Snackbar with **Undo** that re-inserts the exact deleted object
   (keep a copy before deleting; re-insert with its original id via
   `@Insert(onConflict = REPLACE)` — confirm Room allows re-inserting the
   same id, and test it). Undo window: until the Snackbar dismisses or
   the user navigates away; say so honestly in the UI, do not promise
   "undo anytime".
2. Draft autosave: on every text change in the entry editor (debounced,
   e.g. 1 second, on a background dispatcher — not on every keystroke on
   the main thread), save the draft to a `lifevault_drafts` SharedPreferences
   file keyed by user email + entry id (or `"new"` for a new entry).
   On opening the editor, restore the draft if present and show a small
   "Restored draft" note; on successful save, delete that draft key.
   Drafts are unfinished memories: they are excluded from backup in the
   pack's rules files (Step 10) — keep the prefs filename exactly
   `lifevault_drafts` or update both rules files to match.

**Verify:** delete → Undo restores the entry with the same id; delete →
dismiss Snackbar → entry stays deleted; type a draft, kill the app, reopen
→ draft is back; save → draft is gone.

---

## Step 8 — Signup must only report success AFTER the database write

**Files:** `ui/viewmodel/MemoryViewModel.kt` (`signUp`), `MainActivity.kt`
(signup button)
**Why:** v1's `signUp` sets `currentUser`, `isUserLoggedIn = true`, writes
session prefs, and returns `Pair(true, "Account created successfully!")`
*before* launching `repository.insertUser(newUser)` in a fire-and-forget
coroutine. If the app is killed or the write fails, the user believes they
have an account that does not exist, and their first memories can be saved
under an email with no user row.

**Do:**
1. Make `signUp` a `suspend` function (or take a callback invoked only
   after completion — suspend is cleaner):
```kotlin
suspend fun signUp(...): Pair<Boolean, String> {
    // ... validation as today ...
    return try {
        repository.insertUser(newUser)
        // ONLY NOW update in-memory state + session prefs + logged-in flags:
        currentUser.value = newUser
        localUserEmail.value = trimmedEmail
        isUserLoggedIn.value = true
        sessionPrefs.edit()...apply()
        Pair(true, "Account created successfully!")
    } catch (e: Exception) {
        Pair(false, "Account could not be created. Nothing was signed in — please try again.")
    }
}
```
2. Update the signup button to call it from a coroutine scope and only
   clear the form / close the form on `success == true`. The existing
   button code (`MainActivity` ~line 862) already branches on `success`,
   so the fix is in the ViewModel ordering, not the button — but verify
   the button does not navigate on failure.
3. Apply the same rule to login state changes wherever they set
   "logged in" before a read/write completes. If you find another
   fire-and-forget write that the UI reports as done, fix it in the same
   pass and note it in the commit message.

**Verify:** with the database write forced to fail (test build), signup
reports failure and the app is not left in a logged-in state.

---

## Step 9 — Replace clipboard export with SAF file export + a real backup format

**Files:** `MainActivity.kt` (Export / Restore UI), `MemoryViewModel.kt`
(export/import), `data/storage/CompressedStorageManager.kt` (keep v1 reader)
**Why (three separate bugs):**
- (a) Export today copies a Base64 GZIP blob to the **clipboard**, where
  any app can read it and where it is one accidental copy away from being
  lost. A vault must never be exported via clipboard.
- (b) Restore today drops audio: export is JSON only, so a restored vault
  has entries pointing at `voice_vault/` files that were never exported.
- (c) The old format must stay readable — users already have v1 exports.

**Do:**
1. **SAF export:** replace every clipboard export button with
   `ActivityResultContracts.CreateDocument("application/zip")`. The user
   picks a filename and location; write the zip with
   `contentResolver.openOutputStream(uri)`. Delete the clipboard code
   paths in `MainActivity` (~lines 579, 1724, 2704) and
   `PersonalDiaryScreen` (~line 977) for export — clipboard may remain
   for copying a single short text snippet if the UI needs it, but never
   for a vault archive. Show a confirmation with the filename and size,
   not the archive contents.
2. **New format (v2 zip), written on a background dispatcher:**
```
lifevault-backup-v2.zip
├── manifest.json          { formatVersion: 2, exportedAt, appVersion,
│                            memoryCount, relationshipCount,
│                            files: [{ path, sha256, bytes }] }
├── memories.json
├── relationships.json
└── voice_vault/<file>     one entry per audio file referenced by audioPath
```
   Compute a SHA-256 per audio file at export; store only the file's
   basename in the zip (never an absolute device path — paths leak the
   device layout and break on restore to a different phone).
3. **Restore (v2):** unzip, verify each file's SHA-256, copy audio into
   the app's `voice_vault/` (new names if a collision would overwrite an
   existing file — never silently overwrite a newer recording), insert
   memories/relationships with the Step 1 dedupe rule (title + timestamp),
   then report exactly: `"N memories restored, K audio files missing: <names>"`
   where K counts referenced audio files absent from the zip or failing
   checksum. A restore that silently drops audio and reports success is a
   bug; a restore that reports the gap is a feature.
4. **Keep v1 import readable:** keep `CompressedStorageManager.importCompressedVault`
   (Base64 GZIP / raw JSON) as the legacy path, labelled in the UI as
   "Import an older (v1) archive". A v1 import must warn: "Older format —
   audio files are not included and cannot be restored from this file."
   Do not silently "upgrade" a v1 archive into a v2 one without that warning.
5. **Whole-vault GZIP / telemetry off the main thread:** the existing
   export + storage-telemetry computation runs where the UI calls it.
   Move both to `Dispatchers.IO` (export) / `Dispatchers.Default`
   (telemetry math), and show a progress indicator. A large vault must not
   freeze the UI, and a frozen UI during export is how users kill the app
   mid-write and corrupt the archive.

**Verify:** export a vault with voice memos via SAF, delete the app data,
restore on a fresh install, and confirm entries + playable audio return
and the "N restored, K missing" line is correct; separately import a v1
archive and confirm the audio warning appears.

---

## Step 10 — Backup rules + manifest: the vault stays on the device

**Files:** `app/src/main/res/xml/backup_rules.xml`,
`app/src/main/res/xml/data_extraction_rules.xml`, `AndroidManifest.xml`
**Why:** v1's rules files are the Android Studio templates — everything
commented out, nothing excluded — while the manifest sets
`allowBackup="true"`. A vault that auto-backs-up to a cloud account the
user never approved is a privacy failure, not a convenience.

**Do:**
1. Copy the pack's `res/xml/backup_rules.xml` and
   `res/xml/data_extraction_rules.xml` over the v1 templates. They exclude
   the Room database (+ journal/WAL sidecars), `voice_vault/`, the session
   prefs (`lifevault_local_session`), and draft prefs (`lifevault_drafts`)
   from both cloud backup and device transfer.
2. **Recommended, stronger:** in `AndroidManifest.xml`, set
   `android:allowBackup="false"`. Keep the rules files anyway (see their
   header comments for why: device transfer is separate, and a future
   accidental flip back to `true` must not silently upload the vault).
   If product decides `allowBackup` must stay `true`, the exclusion rules
   are the minimum — document that decision and who made it; do not leave
   it as an accident.
3. If you rename the database file, the prefs files, or the voice folder
   at any point, update both rules files in the same commit. Test on an
   API 31+ device/emulator (data extraction rules) and a pre-31 one
   (backup rules) if you can — the two mechanisms are not the same file.

**Verify:** trigger Auto Backup on a test device/emulator (or inspect with
`adb` backup tooling in testing) and confirm no database, voice, or
session file is included.

---

## Step 11 — Close the blank-email leak: remove every `OR ''` clause

**Files:** `data/db/MemoryDao.kt` (all user-scoped queries)
**Why:** every v1 user query contains
`OR userEmail = '' OR :userEmail = ''`. With a blank email — a logged-out
state, a fresh install, a bug — the query returns **every user's
memories**. On a shared device, that is a cross-user leak, not a filter.

**Do:**
1. In every DAO query, replace the pattern:
```kotlin
// BEFORE (leaks):
WHERE LOWER(TRIM(userEmail)) = LOWER(TRIM(:userEmail)) OR userEmail = '' OR :userEmail = ''
// AFTER (no leak):
WHERE LOWER(TRIM(userEmail)) = LOWER(TRIM(:userEmail))
```
   Apply to `getMemoriesForUserFlow`, `getPinnedMemoriesForUserFlow`,
   `getMemoriesByTypeForUserFlow`, `getRelationshipsForUserFlow`, and the
   Step 1 search query.
2. Add a guard in the ViewModel / repository: if the normalized email is
   blank, return an empty flow / empty list immediately and never call the
   DAO. A blank email is "not signed in", never "show everything".
3. Check `getAllMemoriesFlow` / `getAllRelationshipsFlow` callers: if any
   UI screen uses the "all" query for a signed-in user, switch it to the
   user-scoped query. The "all" queries should exist only for admin /
   migration tooling, if at all — if nothing needs them, delete them
   rather than leaving a leak-shaped helper lying around.

**Verify:** sign in as user A, create a memory, sign out (blank email),
confirm the vault screen shows nothing; sign in as user B and confirm A's
memories are not visible.

---

## Step 12 — `compactLocalDatabase` must be real or gone; Kids drawing must be saved

**Files:** `ui/viewmodel/MemoryViewModel.kt`, `MainActivity.kt`
(Kids / drawing UI)

**A. Compact is currently a no-op that lies.**
`compactLocalDatabase()` returns "Database compacted & indexed! …" while
computing nothing and changing nothing (it only reads telemetry). Either:
- **Implement it for real:** run `VACUUM` on a background thread
  (it must never run on the main thread and never while a write is in
  flight — schedule it when the vault is idle, e.g. from a WorkManager /
  coroutine on `Dispatchers.IO` against the Room database's
  `openHelper.writableDatabase`):
```kotlin
// In a repository / database helper, on Dispatchers.IO ONLY:
db.openHelper.writableDatabase.execSQL("VACUUM")
```
  Then report the before/after size from real measurements, or
- **Delete the button.** A button that lies about maintaining the user's
  vault is worse than no button. If in doubt, delete it this round and
  implement VACUUM properly in Round 2. Do not ship the current text.

**B. Kids drawing is never saved.**
Kids-mode content is filtered as `type == "DRAWING"` / `isKidsMode`
(MainActivity ~line 3217) but no code path in the ViewModel saves a
drawing as a memory. A child who draws and closes the app loses the work,
and the UI implies it was kept.
- Either wire a real Save: on the drawing screen's save action, insert a
  memory with `type = "DRAWING"`, `isKidsMode = true`, and the drawing
  stored in `mediaPath` (file in app-private storage, not a giant Base64
  string in the database if it can be avoided — but match whatever
  `mediaPath` already means for photos in v1 and document it), then
  confirm it appears in the Kids list after relaunch;
- Or remove/hide the drawing entry point until saving exists, with a
  clear "coming soon" label — never a canvas that discards work silently.
Do not ship a third option (a toast saying "saved" when nothing was written).

**Verify:** draw, save, kill the app, reopen, confirm the drawing is in the
Kids list; run compact (if implemented) on a large test vault off the main
thread and confirm the size report matches reality.

---

## Step 13 — Build hygiene: wrapper, R8/minify, signing, dead Firebase

**Files:** `app/build.gradle.kts`, `gradle/` (new), `.github/workflows/`
(new), `app/proguard-rules.pro`

1. **Gradle wrapper (missing in v1 — there is no `gradlew`):**
   In Android Studio, run the wrapper task for the Gradle version matching
   the project's AGP (check the AGP version first; do not guess), then
   commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
   and `gradle/wrapper/gradle-wrapper.properties`. Add CI:
```yaml
# .github/workflows/android.yml (new file)
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - run: ./gradlew testDebugUnitTest assembleDebug
```
   Adjust the Java version to the project's toolchain — verify it in
   Android Studio, do not copy the `17` blindly. The CI job must run the
   new `LocalIntelligenceTest` (it is a plain unit test, so
   `testDebugUnitTest` covers it).
2. **Release minify:** v1 sets `isMinifyEnabled = false`. For release, set
   `isMinifyEnabled = true` and `isShrinkResources = true`, keeping the
   existing `proguard-rules.pro`. Add and verify keep rules for Room,
   Moshi, and the entities (Room/Moshi use reflection — a release build
   that strips them will crash in ways debug never shows). Test a release
   build on a device before shipping it; a debug-only test proves nothing
   about R8.
3. **Release signing (env-gated, keep it that way, but fail loudly):**
   v1 reads `KEYSTORE_PATH` / `STORE_PASSWORD` / `KEY_PASSWORD` from the
   environment and falls back to a repo-relative `my-upload-key.jks`.
   Keep secrets out of the repo — never commit a keystore or a password.
   Change the fallback so a missing keystore fails the build with a clear
   message ("Release keystore not configured — set KEYSTORE_PATH …")
   instead of failing obscurely or, worse, signing with a debug key.
   In CI, provide these via repository secrets, never as plaintext in the
   workflow file.
4. **Dead Firebase dependency — remove it (recommended):**
   `app/build.gradle.kts` includes the Firebase BOM and `firebase.ai`,
   but nothing in v1's Kotlin code imports Firebase AI. Remove both lines
   (and the BOM if nothing else uses Firebase — check first) and rebuild.
   If a future feature genuinely needs Firebase AI, re-add it deliberately
   behind the Step 5 consent gate, with its own privacy note. Do not keep
   a dead cloud dependency in a vault app "just in case" — it widens the
   dependency surface and confuses every future privacy audit.
   Note: removing Firebase does **not** by itself remove Gemini network
   calls — Step 5's gate on `GeminiClient` is still required.
5. **Do not fix the PIN by hashing alone in this round.** v1 stores the
   PIN/password in plaintext (`LocalUser.pinOrPassword`, session state).
   Hashing the PIN without encrypting the database still leaves every
   memory readable to anyone with the device files. HONEST_README.md says
   plainly that nothing is encrypted yet. Real encryption (SQLCipher for
   Room + a properly derived key, not a hardcoded one) is Round 2 work —
   see "Round 2" below. Do not claim "PIN protected = encrypted" anywhere.

**Verify:** `./gradlew testDebugUnitTest assembleDebug` passes in CI;
a release build launches and opens the vault (R8 keep rules correct);
`./gradlew dependencies` no longer lists Firebase AI after removal.

---

## Step 14 — Wire `LocalIntelligence` into the UI (checklist, not a rewrite)

- [ ] Search screen → Step 3.
- [ ] Entry editor → tag suggestion chips from `suggestTags(...)`, applied
      **only on tap**, never silently. Show the source
      (hashtag / person / topic) in small text so suggestions do not look
      like the app "knowing" something it guessed.
- [ ] New-entry mood row → `classifyMood(...)` suggestion + confidence
      handling from Step 4.
- [ ] Summary / insights cards → `summarizeExtractiveText(...)`,
      `onThisDay(...)`, `computeStreaks(...)`, `mostMentioned(...)`,
      `monthlyCounts(...)`. Every card that has no data says so.
- [ ] Voice screen → `formatMmSs(...)` for the timer (Step 6).
- [ ] Settings → the two Step 5 consent toggles, default OFF, with plain
      text explaining what each one sends and that audio is separate.
- [ ] Remove or rewrite every UI string that contradicts reality:
      "Zero cloud exposure" (until Step 5 is verified end-to-end),
      "Database compacted" (Step 12), "saved" toasts for unsaved drawings
      (Step 12). HONEST_README.md is the source of truth for wording —
      copy its phrasing, do not improvise new promises.

---

## Step 15 — Verification checklist before ANY release

- [ ] All steps above built one at a time; no step skipped because "it
      looked fine".
- [ ] `LocalIntelligenceTest` green in Android Studio / CI.
- [ ] Upgrade test: v1 install → new build, vault intact (Step 2).
- [ ] Blank-email test: signed out shows nothing (Step 11).
- [ ] Airplane-mode test: all local features work with consent OFF (Step 5).
- [ ] SAF export → fresh-install restore test, including voice + the
      "N restored, K missing" report (Step 9).
- [ ] Delete → Undo, draft restore, voice pause/resume, Kids drawing save.
- [ ] Release (R8) build smoke test on a real device or emulator.
- [ ] Backup exclusion check (Step 10) on API 31+ and pre-31 if available.
- [ ] No keystore, `.env`, API key, or credential in the commit
      (`git status` + a secrets scan, not a visual skim).
- [ ] README shipped is HONEST_README.md's wording, not v1's claims.

---

## What Round 2 should fix next (not in this pack)

1. **Real encryption:** SQLCipher for Room + a key derived from the user's
   PIN/password (never hardcoded, never stored next to the database), plus
   migration of existing plaintext vaults with an explicit user prompt.
   Until then, the honest statement is "not encrypted yet".
2. **Split `MainActivity.kt`:** extract the vault list, voice screen, and
   insights into separate composables / ViewModels, one screen at a time,
   with tests — not a big-bang rewrite.
3. **Real VACUUM scheduling** (if Step 12 chose deletion), background
   export progress, and export integrity verification on restore.
4. **Photo/video support in Android** (the web v2 photo picker is not in
   this Android pack — see HONEST_README.md) with the same SAF +
   backup-exclusion treatment as voice.
5. **Accessibility and large-vault performance:** paging for the memory
   list, and measuring search on a vault of 1,000+ entries before claiming
   it is fast.
6. **A proper release process:** versioning, changelog, signed release
   checklist, and a tested rollback / export-first upgrade path.

---

*Pack contents: `kotlin/com/lifevault/intelligence/LocalIntelligence.kt`,
`test/com/lifevault/intelligence/LocalIntelligenceTest.kt`,
`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`,
`INTEGRATION.md` (this file), `HONEST_README.md`. No API keys, secrets,
or credentials are included in any of them.*
