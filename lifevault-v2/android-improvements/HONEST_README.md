# LifeVault AI — Your Life. Your Memories. Forever. (v2, Android — Honest README)

> **Plain-language version, and an important status note.** This README
> describes the **target state after the Round 1 improvement pack is
> fully integrated and verified in Android Studio** — not what the
> unmodified v1 app does today, and not what merely copying
> `LocalIntelligence.kt` into a project achieves on its own. Until the
> INTEGRATION.md steps (including the cloud-consent gate, PIN handling,
> and the SQLCipher encryption work, which is not done yet) are
> implemented and their tests pass, v1 behaviour remains: a Gemini cloud
> path that is not gated, a plaintext PIN, and an unencrypted database.
> If a sentence here and a sentence in the app disagree, this README is
> the one to fix the app to match, not the other way round — and the
> app must not be described as fixed, encrypted, or fully offline
> until that integration is actually done.

## What LifeVault is

A private journal and memory vault on your Android phone: written entries,
voice memos with transcripts, and (in Kids mode) drawings and stories.
There is no account on a LifeVault server, because there is no LifeVault
server in this app. Your memories live on your phone.

## What is actually offline

- Writing, reading, searching, and deleting memories: **offline, on your
  phone, when cloud help is off (the default).** Search ranking, mood
  suggestions, summaries labelled "From your own words", On This Day
  flashbacks, streaks, and most-mentioned people/places/tags are all
  computed on your device from your own entries. No network call is made
  for any of them when cloud help is off.
- Voice recording and transcription: recordings are stored in the app's
  private `voice_vault/` folder, not in a shared folder other apps can
  browse. Transcription uses your phone's speech recognition, which on
  some devices may use a cloud service — check your device's speech
  settings, because this pack does not change that path, and do not
  describe transcription as guaranteed offline on every device.

## What uses the cloud — only if you opt in

- **Nothing, by default.** With a fresh install, the app sends nothing
  anywhere. That is the default, and updating the app does not change it.
- **Cloud help is opt-in, per entry, and split in two.** In Settings you
  can turn on, separately:
  1. "Allow cloud help for text I choose" — off by default.
  2. "Allow cloud help for audio I choose" — off by default.
- Even with a toggle on, the app asks you for **that specific entry**
  before sending it. Turning a toggle on is not permission to send
  everything. Audio is never sent on a text-only permission.
- If you never turn these on, the app still works fully: summaries come
  from your own words, and nothing is sent. If a cloud call fails, the app
  says so and shows your own-words summary instead — it never invents a
  fluent "result" to fill the gap.
- Previous versions of this app described themselves as having "zero cloud
  exposure" while including a cloud AI path. That wording was wrong and
  has been removed. This section replaces it.

## What is encrypted — honestly: nothing yet

- **Your memories are NOT encrypted yet.** They are stored in a database
  and files in the app's private storage on your phone. Other apps cannot
  normally read another app's private storage, but anyone who can unlock
  your phone and access its files — or who gets a full copy of your phone's
  storage — could read them.
- Your lock PIN/password in the app is an **app lock screen**, not
  encryption. It stops a casual viewer opening the app; it does not make
  the stored files unreadable.
- **Do not store anything in LifeVault yet that you would not want read
  if your phone were lost and unlocked.** Real encryption (an encrypted
  database with a key derived from your password) is planned for the next
  round of improvements, and this README will be updated the day it lands
  — not before.
- Backups: the app's own export is a file **you** choose to save, using
  your phone's file picker. That file is not encrypted either, so keep it
  somewhere safe and do not send it to anyone you do not trust with your
  memories. The app does not automatically back your vault up to any cloud
  account (see below).

## What photos and video actually do (Android)

- **Photos and video are not yet part of this Android pack.** The newer
  web version of LifeVault has a photo picker; the Android app in this pack
  does not. If you see a photo feature mentioned elsewhere for LifeVault,
  check which version it belongs to before expecting it on Android.
- Voice memos: recorded, transcribed, and played back on the device, and
  included in exports (see below). Drawings in Kids mode: saved as entries
  once the Round 1 save fix is applied; if a drawing screen ever lets you
  draw without a working Save button, treat that as a bug and report it —
  the app must never silently discard a child's drawing.

## Backups, export, and restore — what actually happens

- **Automatic cloud backup is off for the vault.** The app excludes its
  database, voice recordings, session data, and drafts from Android Auto
  Backup and device transfer, and the recommended setting disables Auto
  Backup for the app entirely. Your vault does not silently copy itself
  to a cloud account.
- **Your export is your responsibility, on purpose.** Tap Export, pick a
  file location, and the app writes one zip file containing your entries,
  a manifest, and your voice recordings. Nothing is copied to a clipboard
  (older versions exported to the clipboard — that is removed, because a
  clipboard can be read by other apps).
- **Restore tells you what is missing.** After a restore, the app reports
  how many memories were restored and how many audio files were missing,
  by name, instead of claiming success while audio is silently gone.
  Older (v1) backup files can still be imported, but they contain no
  audio — the app warns you about that before importing.
- There is no "restore from the cloud" because there is no cloud copy.
  If you lose both your phone and your export file, your memories are
  gone. Export regularly, and keep at least one copy somewhere other than
  the phone itself.

## Our privacy promise, in plain language

1. **Your memories stay on your phone unless you choose otherwise,
   entry by entry.** No silent uploads, no background sync, no account
   that copies your vault elsewhere.
2. **We do not sell, share, or analyse your memories for advertising.**
   There are no ads and no analytics in this app, and none are planned
   as part of this improvement pack.
3. **Nothing here is a diagnosis.** Mood suggestions are a word-list
   guess from what you wrote, shown with its uncertainty. They are not a
   medical or mental-health assessment, and the app never presents them
   as one.
4. **When the app does not know, it says so.** No memories from this day
   in past years? It says that, instead of showing you a random old entry
   as a "flashback". No mood words in an entry? It says it is not sure,
   instead of inventing a feeling.
5. **If this promise ever changes, the change will be in this README
   first, in plain language, before it is in the app.** A privacy promise
   that only exists in marketing copy is not a promise.

## What this version does not do (so you are not surprised)

- No cloud sync between phones, no shared family vault, no web account.
- No encryption yet (see above) — this is the biggest limitation, and it
  is being worked on next.
- No photo/video picker on Android yet (see above).
- No server-side recovery: if you forget your app PIN and cannot get into
  the app, there is no "email me a reset link", because there is no
  server that knows you. Keep your export file safe — it is your recovery.

## For developers

See `INTEGRATION.md` in the improvement pack for the step-by-step
integration instructions, and the "What was NOT verified" notice at its
top: this pack was not compiled or tested in the environment that wrote
it, and must be built and tested in Android Studio before release.
