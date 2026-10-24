# LifeVault Android Improvement Pack — Round 2 Notes

## What Round 2 fixed

- **Mood-set mismatch (the Round 2 task):** `LocalIntelligence.kt`
  `Mood` is now the same 8 moods as web v2 — HAPPY, CALM, GRATEFUL,
  EXCITED, TIRED, SAD, REFLECTIVE, ANXIOUS.
  - GRATEFUL words (`grateful`, `thankful`, `blessed`, `appreciate`,
    `gratitude`) were moved out of HAPPY, so "grateful" no longer
    classifies as HAPPY.
  - REFLECTIVE (`reflect`, `thinking`, `remember`, `nostalgia`,
    `ponder`, `reminisc`) and ANXIOUS (`anxious`, `worried`, `nervous`,
    `stress`, `afraid`, `panic`, `anxiety`) were added.
  - ANGRY was **removed deliberately**, not kept as a 9th mood: web v2
    has no Angry label to display for new entries. Anger words in NEW
    text now fall through to "unknown" (CALM with confidence 0.0)
    rather than being forced into a label the web app cannot show.
    That rule is for new classification only. STORED legacy ANGRY
    values have an explicit, separate mapping — see INTEGRATION.md
    Step 4a and `legacyMoodDisplay()` in `LocalIntelligence.kt`:
    display "Angry (legacy)", count them in a legacy bucket, never
    silently re-label them as ANXIOUS, SAD, or CALM, and never silently
    drop them. The earlier "show as no mood suggested" wording for
    stored values is superseded by Step 4a.
  - Caveat: web v2 also matches the two-word phrase "looking back";
    this single-token classifier cannot, so that phrase alone will not
    classify as REFLECTIVE here. That gap is noted in the code.
- **Tests for the new moods:** `LocalIntelligenceTest.kt` gained
  `mood_newMoodsMatchWebV2` and `mood_gratefulIsNotHappy`.
- **Coherence check:** all 7 pack files exist and are coherent —
  `INTEGRATION.md`, `HONEST_README.md`, `README.md`,
  `LocalIntelligence.kt`, `LocalIntelligenceTest.kt`,
  `backup_rules.xml`, `data_extraction_rules.xml`. A Round 2 note was
  added to the top of `INTEGRATION.md` pointing at this file. No
  content was changed in `HONEST_README.md`/`README.md` beyond that,
  because their claims (no keys, no cloud by default, no encryption
  yet) are still true.
- **Secret / endpoint scan (whole v2 folder):** grepped for API-key
  patterns, `sk-`/`ghp_`/`AKIA` values, and external URLs. Found
  **none**. The only `https://` reference is a
  `developer.android.com` documentation URL inside an XML comment in
  the backup-rules files (not an endpoint the app calls), and the
  words "secret"/"key" in docs refer to env-var *names* and to the
  "never commit a key" rule, not to values. The `fetch()` in `sw.js`
  is the service-worker's local-cache fallback, not a cloud call.
  Nothing was removed because there was nothing to remove.

## What Round 2 did NOT do (honestly)

- **Kotlin is still uncompiled and its tests are still unrun.** This
  environment has no JDK and no Android SDK (same as Round 1), so
  `LocalIntelligence.kt` / `LocalIntelligenceTest.kt` have NOT been
  compiled here. The mood expansion is a careful text edit, not a
  verified build. Run in Android Studio before release:
  `./gradlew testDebugUnitTest --tests "com.lifevault.intelligence.*"`
  and fix any failure there; do not ship with failing tests.
- **No live-browser test of the web app** was done in this round either
  (see `../TESTS.md` for the manual checklist that must be run).
- **SQLCipher encryption is NOT done.** It is planned below, not
  claimed. The HONEST_README statement "Your memories are NOT encrypted
  yet" remains true for Android.

## Next steps for SQLCipher encryption (NOT done — do not claim otherwise)

1. **Add the dependency** in `app/build.gradle.kts` (e.g.
   `net.zetetic:android-database-sqlcipher`) and confirm it builds
   with the project's Room version; if Room's bundled SQLite conflicts,
   resolve it explicitly rather than shipping two SQLite copies.
2. **Key derivation:** derive the database key from the user's app
   password/PIN with PBKDF2 (or Argon2 if the project already uses
   it) with a per-install random salt stored in app-private storage.
   Never store the password/PIN itself, never hard-code a key, and
   never put the key in a backup (the backup-rules files already
   exclude the database and session prefs — re-check them after this
   change).
3. **Open path:** open the Room database through SQLCipher's
   `SupportFactory` with the derived key; on a wrong password the open
   must fail with a clear "wrong password" message, not a crash or a
   silent empty vault.
4. **Migration plan (the hard part):** decide before release how an
   existing unencrypted v1 database migrates — options are (a) encrypt
   in place on first unlock after the user sets/confirms a password,
   with a verified backup first, or (b) clean-install break with an
   export-first warning. Document the choice in HONEST_README *before*
   shipping it, and test the migration on a real v1 database, not an
   empty one.
5. **Voice files:** SQLCipher covers the database only. Decide
   separately whether `voice_vault/` files are encrypted (e.g. with a
   file-level cipher using the same derived key) or explicitly left
   unencrypted with that limitation stated in HONEST_README. Do not let
   "the database is encrypted" imply the voice files are.
6. **Tests:** add a unit/instrumented test that (i) opens with the
   right key, (ii) fails with the wrong key, (iii) re-opens after a
   process restart, and (iv) proves a raw copy of the database file is
   not readable as plaintext SQLite. Only then update HONEST_README's
   "NOT encrypted yet" section — and update it the day it lands, not
   before.

## Retest (Agent B) + Agent C fixes — applied to this pack

Scope: Android pack only (`android-improvements/`). Web app files were
not touched. Nothing in this retest was pushed to GitHub by the retester.

### Real bugs found and fixed in code

1. **Mood words compared unstemmed to stemmed tokens.** `classifyMood()`
   stems input tokens, but `moodOfToken()` compared them to unstemmed
   mood-list words. Real failures, verified by simulating the Kotlin
   logic: "thinking" (stem "think") did not match list entry "thinking";
   "blessed" ("bles") did not match "blessed"; "still" ("stil"),
   "miss"/"missing" ("mis"), "loss" ("los"), "stress" ("stres"),
   "thrill" ("thril"), "amazing" ("amaz"), "anxious" ("anxiou"),
   "nervous" ("nervou"), "tears" ("tear") all failed their own list
   entries. Fix: `moodOfToken()` now stems each mood word with the same
   `stemLite()` before comparing. Existing tests passed before only
   because they used forms that happened to survive stemming
   ("remembering", "worried", "grateful", "thankful").
2. **Search synonyms broke after stemming.** `tokenize()` stemmed
   before canonicalising, so "delighted" became "delight" (not in the
   synonym map), "exhausted" became "exhaust", "relaxed" became
   "relax", "furious" became "furiou", and so on — none matched their
   canonical term. Fix: `tokenize()` now canonicalises before
   stemming, then canonicalises again. Both query and document go
   through the same transform, so e.g. "excited" and "thrilled" both
   land on "excit" and match. Note: the canonical name is not
   preserved for every term ("excited" becomes "excit") — that is
   harmless for ranking, because query and document are transformed
   identically, but do not display the internal token as a search term.
3. **Targeted mood inflections.** Added "smil" and "lov" stems to
   HAPPY so "smiling" (stem "smil") and "loving" (starts with "lov")
   classify, matching the existing stem style ("excit", "celebrat",
   "appreciat", "reminisc", "worri"). This is a lexicon heuristic,
   not a model — see the classifier comment.

### Agent C fixes (honesty + legacy mapping)

4. **Pack is not presented as a fixed app.** A prominent banner was
   added to the top of INTEGRATION.md and README.md, and the
   HONEST_README.md introduction was rewritten: the pack is uncompiled
   helper code plus integration steps; HONEST_README describes the
   target state after full integration and verification, not v1 today.
   V1's Gemini cloud path, plaintext PIN, and unencrypted Room DB are
   only fixed when Step 5 (consent gate), PIN handling, and SQLCipher
   are actually implemented and tested in Android Studio.
5. **Explicit legacy ANGRY mapping.** INTEGRATION.md Step 4a now
   contains the full table (display "Angry (legacy)", separate legacy
   analytics bucket, no new-entry choice, no auto-conversion on edit,
   search still finds legacy entries), and `LocalIntelligence.kt`
   gained `legacyMoodDisplay()` implementing it. The old "show as no
   mood suggested" wording for stored values is superseded.
6. **Claim alignment.** Mood set is the web 8-mood set in code, tests,
   and docs. No encryption is claimed anywhere in the pack except as
   "not encrypted yet" / future SQLCipher work. The HONEST_README
   voice-transcription line was softened: transcription uses the
   device's speech service, which on some devices may be cloud-based,
   and this pack does not change that path.

### New / changed tests

- `mood_stemmedFormsStillClassify` (thinking, missing, still, blessed)
- `mood_newTextAngerIsUnknownNotForcedIntoAnotherMood`
- `legacyMoodDisplay_preservesAngryAsLegacyNeverConvertsIt`
- `search_synonymSurvivesStemming` (tired/exhausted, calm/relaxed,
  happy/delighted)

All four were checked by simulating the Kotlin tokeniser, stemmer,
and mood classifier in Python (same rules, same word lists) — see
"Still unverified" below for why that is not a compile.

### What was checked and found OK (no change needed)

- Mood 8-set parity with web v2 (Happy, Calm, Grateful, Excited,
  Tired, Sad, Reflective, Anxious) in `LocalIntelligence.kt` and tests.
- Negation window: `NEGATION_WINDOW = 3` with `subList(i-3, i)` does
  include "not" in "not at all happy" (indices 0..2 before token 3) —
  verified, not a bug. Existing negation tests cover "not happy" /
  "not sad".
- Extractive summary: returns verbatim original sentences only, in
  original order, empty input -> empty list, no invented text.
- On This Day: same month/day, different year only, today excluded,
  sorted by yearsAgo ascending; existing test covers this.
- Streaks: current counts back from yesterday if today is empty,
  longest is a simple consecutive-day run; existing tests cover both.
- Tag suggestions: returned only, never auto-applied (code comment,
  Step 14 checklist, and test comment all agree); existing test
  checks existing tags are not re-suggested and people are matched.
- Cloud consent: `CloudConsent` defaults both OFF, text/audio
  separate, `canSend()` never lets audio ride on text consent;
  existing test covers this.
- mm:ss: `formatMmSs` clamps negatives to 00:00; existing test covers
  0, 45, 125, -5.
- INTEGRATION.md steps 0–15 are in order, cross-reference the right
  steps (indices -> Step 2 migration, consent -> Step 5, backup ->
  Step 10, blank-email -> Step 11), and do not contradict the code
  on the points checked. Step numbering in the text (Step 4a added
  between Step 4 and Step 5) does not renumber later steps.
- Backup XMLs: both parse, and both exclude database (named files
  plus "." safety net), `voice_vault`, session prefs
  (`lifevault_local_session.xml`), and draft prefs
  (`lifevault_drafts.xml`) — in cloud-backup and, for the API 31+
  file, device-transfer too. The `voice_vault` folder exclusion is
  written as a folder path; its actual effect must still be verified
  on a device/emulator (INTEGRATION.md Step 10 already requires this),
  because XML parsing alone cannot prove the system's backup
  behaviour.

### Still unverified — do NOT present otherwise

- **Kotlin is still uncompiled and its tests are still unrun.** No
  JDK, no Android SDK, no Gradle wrapper in this environment
  (`which java kotlinc gradle` returns nothing). The fixes above are
  careful code edits plus a Python simulation of the same rules —
  that is a strong logic check, not a compile, not a JUnit run.
  Run `./gradlew testDebugUnitTest --tests
  "com.lifevault.intelligence.*"` in Android Studio / CI and fix any
  failure there before release.
- **SQLCipher encryption is NOT done** (see ROUND2_NOTES steps above).
- **No live Android / AI Studio test** was done by this retester
  (generic subagents cannot operate a live browser or sign in; any
  AI Studio / Gemini-agent verification must be delegated by the
  parent to an eligible browser agent, and must not be claimed here).
- **No GitHub push** was made by this retester, per the task.
