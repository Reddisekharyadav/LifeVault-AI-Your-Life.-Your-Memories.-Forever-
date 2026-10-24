# LifeVault v2 — Tests (Round 2)

## Automated checks (run, no live browser)

Run from `lifevault-v2/`:

```sh
node --check app.js
node --check sw.js
node tests/logic.test.js
python3 -m json.tool manifest.json > /dev/null
python3 -c "import xml.etree.ElementTree as ET; [ET.parse(f) for f in ['android-improvements/res/xml/backup_rules.xml','android-improvements/res/xml/data_extraction_rules.xml']]"
```

`tests/logic.test.js` (18 checks, passing at Round 2) extracts the pure-logic
block (MOODS, tokens, synonyms, suggestMood) and `fmtTime` from the real
`app.js` and runs them in a Node `vm` sandbox — it is not a hand copy, so
it fails if the real code regresses. It also statically checks the real
files: PBKDF2 + 100,000 iterations + `pinSalt`, no browser `prompt()` call,
no stored recovery key, no `fetch`/XHR/external URL in `app.js`, the
in-page password dialog, reminders default OFF with the generic
notification text only, the Round 2 HTML ids, manifest 192 icon, and the
Kotlin 8-mood set.

**What this does NOT prove:** nothing here runs the app in a browser.
DOM rendering, IndexedDB, WebCrypto PBKDF2, Notification permission, and
the service worker are NOT exercised by these checks. Do not claim a
live-browser test. Use the manual checklist below in a real browser.

## Manual browser checklist (do before any release)

Serve the folder over HTTP (not `file:` — the service worker only
registers on http/https) and work through each item, ticking only what
you actually saw:

- [ ] **Fresh start / onboarding:** create a profile with a PIN, write
      down the recovery key shown, finish onboarding, reload. The
      recovery key is NOT in `localStorage` afterwards: in DevTools,
      `localStorage.getItem('lifevault_v2')` must contain no recovery-key
      string, only `recoveryHash`/`recoverySalt`/`pinHash`/`pinSalt`.
- [ ] **PIN migration (Round 1 profile):** load a Round 1 store that has
      a bare SHA-256 `pinHash` and no `pinSalt` (see Round 1 backup if
      available). Unlock with the old PIN: it must succeed once, and
      afterwards the stored profile must have `pinSalt` and no bare
      legacy hash path left (reload and unlock again with the same PIN).
- [ ] **Wrong PIN / lockout:** 5 wrong PINs lock for 30 seconds; the
      lock screen clears the PIN field after each attempt.
- [ ] **Recovery:** lock the vault, use the written-down recovery key
      with a new PIN. It must reset the PIN; a wrong key must not log in.
      After reset, the recovery key is still not stored.
- [ ] **Profile switch:** switching to a PIN-protected profile opens an
      in-page dialog (never a browser `prompt()`), and a wrong PIN keeps
      you on the current profile.
- [ ] **Export (encrypted):** tick password-protect, export, confirm the
      dialog asks for password + confirm, with show/hide, and that the
      dialog fields are empty afterwards. Import that file: an in-page
      dialog (never `prompt()`) asks for the password; a wrong password
      is rejected with a message, not a silent failure.
- [ ] **Import counts:** import a file containing a duplicate entry id
      and confirm the message counts it as "duplicates skipped", not
      "restored". Import a file whose media is missing and confirm the
      missing count is reported honestly.
- [ ] **Import cap:** a file over 50 MB is rejected before import.
- [ ] **Reminders (default OFF):** on a fresh profile, More → Reminders
      shows both toggles OFF and no capsule/reflection card appears on
      Home. Turn on "list time capsules": create an entry with a capsule
      date of today or earlier, reload, and confirm only its title (not
      its body) is listed on Home. Turn on daily prompt: confirm that
      with notifications denied/unavailable the prompt appears in-app on
      open, and that a granted notification (if you allow it) reads only
      "A LifeVault reflection prompt is waiting" — never entry text.
- [ ] **Delete + Undo:** delete an entry, tap Undo within 8 seconds,
      confirm the entry (and its photo/voice, if any) comes back. Delete
      another entry and wait past 8 seconds: Undo must no longer restore
      it, and its media must not linger as a restorable file.
- [ ] **Delete vault:** deleting a profile's vault removes its entries
      AND its IndexedDB photos/voice (check in DevTools → IndexedDB).
- [ ] **Edit with new photo/voice:** editing an entry and attaching a
      new photo/voice replaces the old file in IndexedDB (no orphaned
      old file left behind).
- [ ] **Voice timer pause:** record, pause for ~5 seconds, resume:
      the timer must not count the paused seconds.
- [ ] **Set PIN with blank field:** leaving "New PIN" blank and tapping
      Save PIN must NOT remove or change the existing PIN.
- [ ] **Offline:** with the service worker registered, reload with the
      network disabled; the app shell must still load from cache.

## Android (not run here)

`LocalIntelligenceTest.kt` (JUnit 4, plain JVM) was NOT run in this
environment — there is no JDK/SDK here. Run it in Android Studio:
`./gradlew testDebugUnitTest --tests "com.lifevault.intelligence.*"`.
See `android-improvements/ROUND2_NOTES.md` for what is still unverified
and the SQLCipher next steps.
