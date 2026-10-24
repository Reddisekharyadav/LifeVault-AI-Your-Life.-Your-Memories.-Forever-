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
    has no Angry label to display. Anger words now fall through to
    "unknown" (CALM with confidence 0.0) rather than being forced into
    a label the web app cannot show. If the Android app's stored mood
    column still contains ANGRY values, map them at the UI layer (e.g.
    show "no mood suggested") — do not silently re-label them.
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
