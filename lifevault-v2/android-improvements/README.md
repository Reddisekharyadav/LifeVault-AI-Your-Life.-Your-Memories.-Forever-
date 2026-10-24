# LifeVault AI v2 — Android Improvement Pack (Round 1)

Drop-in pack for Android Studio. NOT a rewrite, NOT a new app. The original
in `~/workspace/lifevault` was not modified.

## Contents

| File | What it is |
|---|---|
| `kotlin/com/lifevault/intelligence/LocalIntelligence.kt` | Pure Kotlin, no Android deps: BM25-like search, mood v2, extractive summary, true insights, tag suggestions, cloud-consent gate, mm:ss timer. Copy to `app/src/main/java/<your-package>/intelligence/` and fix the `package` line (see INTEGRATION.md step 0). |
| `test/com/lifevault/intelligence/LocalIntelligenceTest.kt` | Plain JUnit 4 tests (no Android). Copy to `app/src/test/java/<your-package>/intelligence/`. |
| `res/xml/backup_rules.xml` | Replaces the v1 template: excludes Room DB, `voice_vault/`, session + draft prefs from Auto Backup. |
| `res/xml/data_extraction_rules.xml` | Same exclusions for API 31+ cloud backup and device transfer. |
| `INTEGRATION.md` | Step-by-step, file-by-file integration guide (steps 0–15). Start here. |
| `HONEST_README.md` | Corrected README for v2: what is offline, what is cloud-only-if-opted-in, what is (not) encrypted, what photos/video do on Android. |

## Rules of this pack

- No API keys, secrets, or credentials are included in any file.
- No server account, cloud sync, analytics, or ads are added.
- Nothing here was compiled or tested in the writing environment (no JDK/SDK) — see the "What was NOT verified" notice at the top of `INTEGRATION.md`. Build and test in Android Studio before any release.
