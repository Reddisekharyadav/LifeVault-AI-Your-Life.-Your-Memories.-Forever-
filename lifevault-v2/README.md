# LifeVault v2 — Your Life. Your Memories. Forever. (on this device)

A separate, honest rebuild of LifeVault AI, made after a 5-agent review of the original
Kotlin Android app (https://github.com/Reddisekharyadav/LifeVault-AI-Your-Life.-Your-Memories.-Forever-).

This is NOT the original Android code, and it is NOT a new APK built in this session
(no JDK / Android SDK was available here, so the Android code could not be compiled here).
It is a local-first web app / installable PWA that you can open on any phone or computer,
including Android via "Add to Home Screen" in Chrome.

## How to run it
1. Unzip this folder.
2. Open `index.html` in a browser, OR serve it: `python3 -m http.server 8902 --directory .`
   then open http://localhost:8902/index.html
3. No build step, no sign-in to a server, no API key, no internet needed after first load
   (the service worker caches the local files).

## What is better than v1
- One Capture (Write / Voice / Photo), not three overlapping paths. Drafts autosave.
  Entries can be edited (v1 edit created a duplicate) and deleted only after confirm + Undo.
- Search that works: field-weighted ranking, synonyms (coffee = espresso), mood / person /
  type / date filters, and it searches transcripts and locations too.
- Real insights only: true On This Day (same month/day, past years), 7-day and 30-day mood
  trends, writing streak, most-mentioned people/places/tags. No hardcoded mood sentence,
  no canned "AI summaries" — highlights are verbatim quotes from your own entries,
  labelled "Local extract - not generated".
- Mood suggestion handles negation ("not happy" is not Happy) and you can correct it.
- Tag suggestions are tap-to-accept, never silently applied.
- Voice: real pause/resume, correct mm:ss timer, transcripts append (not overwrite),
  editable before sealing. Speech is labelled honestly — browser speech may vary by device.
- Photos: picker, downscaled, stored in IndexedDB (not Base64 in the main data), GPS never read.
  Video is NOT included — the app says so plainly.
- Privacy, honestly: PINs and recovery keys are stored only as salted PBKDF2 hashes
  (100,000 iterations), never plaintext. Auto-lock on tab hide, 5 failed attempts = 30s lockout that is persisted across reloads (localStorage lockUntil),
  profile switching needs that profile's PIN, and there is no "forgot PIN" that auto-logs you in —
  a recovery key is shown once at setup and is NOT stored by the app. The PIN is a lock, not encryption — entries are stored in plaintext in browser storage, so anyone with device/browser access can read them. No PIN - anyone opening this app can open this profile.
- Backup that is real: export is a downloaded FILE (not clipboard), password-protected by default (unchecking it shows an explicit warning and asks for confirmation, because an unprotected file is readable by anyone with it)
  with AES-GCM + PBKDF2, with a SHA-256 corruption check (unencrypted files) / AES-GCM authentication (password-protected files) and a 50 MB cap. It includes voice/photo files
  and honestly reports missing files on restore. Old v1 Base64 exports are also readable.
- Onboarding, bottom navigation (Home | Entries | Timeline | More) + Capture button,
  month-grouped Timeline, Light / Dark / System themes, 48px touch targets, opt-in reminders
  (default OFF, generic notification text only — no entry text in notifications).
- No server account, no cloud sync, no analytics, no ads, no external CDNs. This app itself makes no cloud calls; browser live transcription may send audio to your browser's speech service.

## What is NOT done yet — read this before trusting it with precious memories
- This web v2 was NOT live-browser tested end-to-end in this session (the browser tool
  rejected localhost, and headless Chrome testing was incomplete). Static tests pass:
  `node tests/logic.test.js` = 29/29 after the Agent A retest + P0 fixes, `node --check app.js` and `sw.js` pass, manifest and
  backup XMLs parse. Please try it with a throwaway entry first, export a backup, and
  confirm import works on your device before relying on it.
- Android: `android-improvements/` contains drop-in Kotlin logic (search, mood v2,
  extractive summaries, On This Day, tag suggestions), a step-by-step INTEGRATION.md,
  an honest README, and real backup rules. That Kotlin code is NOT compiled or tested
  here (no JDK/SDK) — run it in Android Studio first. SQLCipher encryption for Android
  is NOT implemented yet; see `android-improvements/ROUND2_NOTES.md`.
- Video, multi-device sync, and cloud AI are deliberately NOT included.

## Files
- index.html, styles.css, app.js — the app
- manifest.json, sw.js — installable PWA / offline cache
- TESTS.md, tests/logic.test.js — tests and manual checklist
- android-improvements/ — Kotlin pack + integration guide for the original Android app
