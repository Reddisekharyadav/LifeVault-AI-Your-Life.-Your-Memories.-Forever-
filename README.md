<div align="center">
<img width="1200" height="475" alt="LifeVault AI Banner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />

# 🌌 LifeVault AI
### *100% Offline, Zero-Cloud, Encrypted AI Memory Vault & Compressed Storage Engine for Android*
</div>

---

## 📖 What is LifeVault AI?

**LifeVault AI** is a local-first, privacy-guaranteed personal memory assistant and encrypted journal vault built with **Kotlin, Jetpack Compose, Room Database, and Material Design 3**. 

Designed with a **100% Offline Architecture**, LifeVault AI processes all journaling, emotion heuristics, smart tagging, and voice note transcriptions directly on-device without sending personal thoughts, photos, or voice notes to any cloud servers.

---

## ✨ Core Features & Navigation Hub

### 1. 🏠 Dashboard
- **Visual Memory Art**: Hero banner and curated memory highlights.
- **Natural Language Offline Search**: Search through all your memories, tags, locations, and transcripts instantly.
- **Voice-First Recording Studio**: Press **"Start Recording"** to narrate your day. The on-device engine transcribes, detects mood, extracts topics, and generates a structured entry with live audio wave animations.
- **Daily Inspiration & Highlights**: Quick-action cards to record gratitude, milestone achievements, or reflections.

### 2. ✍️ Journal Lab (Creator Screen)
- **Multi-Type Journaling**: Choose between *Gratitude*, *Milestone*, *Academic*, *Travel*, *Family*, or *Personal Reflection*.
- **Offline Emotion & Topic Extraction**: Automatically analyzes your text locally to suggest relevant tags, title summaries, and mood states (*Peaceful, Happy, Excited, Nostalgic, Grateful, Triumphant*).
- **Audio & Location Tagging**: Attach recorded voice clips, geolocations, and pin status to any memory.

### 3. 📅 Timeline Hub
- **Chronological Memory Stream**: Beautifully styled cards displaying your journey over time.
- **Dynamic Mood & Category Filtering**: Filter memories by specific moods or categories with a single tap.
- **Memory Pinning**: Pin your most cherished memories to the top or secure them in the Locker.
- **Interactive Slideshow / Highlight Film**: Watch an animated recap of your journal milestones.

### 4. 🎓 Academic & Travel Hub
- **Academic Milestones**: Track degrees, exams, research publications, and study streaks.
- **Travel Journals**: Log journeys, destinations, visited cities, and scenic memories with dedicated location tags.

### 5. 👨‍👩‍👧‍👦 Family Vault & Relationship Tree
- **Family & Friend Directory**: Add loved ones with customizable avatar emblems and color accents.
- **Relationship Notes**: Keep track of meaningful notes, birthdays, and shared milestones.
- **Shared Memories Link**: View all journal entries associated with specific family members.

### 6. 🔒 Locker (Security & Privacy Console)
- **Master Account Binding**: Bound locally to your master email (`maruganireddisekhar5@gmail.com`) in private storage.
- **4-Digit Master PIN Lock**: Secure the entire app or sensitive tabs with biometric/PIN authentication.
- **Zero-Cloud Guarantee**: Visible indicator confirming all data remains strictly in on-device SQLite storage.
- **Secured Vault Items**: View only entries flagged with high security PIN protection.

### 7. 🗜️ High-Density Compressed Storage Engine
- **GZIP Stream Compression**: Lossless GZIP compression algorithm reducing database footprint by up to 60-80%.
- **Live Storage Telemetry**: Real-time dashboard showing raw SQLite byte size, compressed GZIP footprint, and space saved.
- **One-Click GZIP Export**: Tap **"Export GZIP"** to copy a portable Base64 encrypted archive directly to your clipboard for air-gapped backup.
- **Decompress & Restore**: Paste any exported archive into the restore box to rebuild your entire memory vault.
- **Database Compaction**: Run `VACUUM` and `PRAGMA` index optimization routines to compact storage and maximize app performance.

### 8. 🎭 Multi-Profile Offline Vaults
- **Multiple Vault Personas**: Create separate vaults (e.g., Personal, Work, Creative, Family) with individual emojis, custom theme colors, and independent PIN locks.
- **Quick Switching**: Switch between vaults from the top profile bar in one tap.

### 9. 🧸 Kids Mode
- **Kid-Friendly Interface**: Simplified, colorful mode designed for children to record bedtime stories, school adventures, and daily doodles safely.

---

## 🕹️ Step-by-Step: How to Use LifeVault AI

| Action | How to do it in the App |
| :--- | :--- |
| **Record a Voice Memory** | Go to **Dashboard**, press the circular **Microphone / Record** button. Speak your memory, then press **Stop**. The app creates a transcribed entry with mood and tags automatically. |
| **Write a Custom Journal Entry** | Tap the **Journal Lab** tab in the top navigation bar. Select a category (e.g., *Travel* or *Gratitude*), enter your title and story, and tap **"Seal Memory in Vault"**. |
| **Search Memories** | Tap the **Search bar** on the Dashboard or Timeline and type keywords (e.g. `Kyoto`, `Coffee`, `Exam`). Results filter instantly. |
| **Filter by Mood** | In the **Timeline** tab, tap any mood chip (*Happy, Grateful, Peaceful, etc.*) to display matching entries. |
| **Set a Security PIN** | Tap the **Profile / Avatar** at the top or switch to the **Locker** tab. Enter a 4-digit PIN and tap **"Set Security PIN"**. |
| **Export a Compressed Backup** | Tap the **Profile Header** or **Locker** tab $\rightarrow$ Tap **"Export GZIP"**. Your encrypted Base64 archive is copied to your clipboard. |
| **Restore from Backup** | Open the **Profile & Storage Console** $\rightarrow$ Tap **"Restore / Import GZIP Archive"** $\rightarrow$ Paste your archive text $\rightarrow$ Tap **"Decompress & Restore Vault"**. |
| **Compact Database** | Open the **Profile & Storage Console** $\rightarrow$ Tap **"Compact DB"** to optimize storage and index performance. |
| **Create a New Profile** | Open the Profile dialog $\rightarrow$ Switch to **Vault Profiles** tab $\rightarrow$ Tap **"Create New Account"** $\rightarrow$ Pick name, emoji, color, and optional PIN $\rightarrow$ Tap **"Seal Account"**. |
| **Toggle Kids Mode** | Tap the **Teddy Bear / Kids Mode** icon in the top header bar to switch between standard and kid-friendly UI. |

---

## 🧪 Automated Tests & Quality Assurance

LifeVault AI includes a comprehensive automated test suite powered by **Robolectric** and **Compose UI Test Framework**:

- **System Integration Tests (`LifeVaultFullSystemTest.kt`)**:
  - Tests offline master email authentication & binding.
  - Tests 4-digit PIN locking, unlock attempts, and wrong PIN rejection.
  - Tests GZIP stream compression, byte calculation, and lossless decompression/restore.
  - Tests memory insertion, natural language search query filtering, and category sorting.
  - Tests voice recording state machine (*Start, Pause, Resume, Stop*).
  - Tests Kids Mode and Privacy mode state transitions.
  - Tests database compaction telemetry.

- **Compose UI Interaction Tests (`LifeVaultComposeUiTest.kt`)**:
  - Simulates button clicks across all 6 navigation tabs (*Dashboard, Journal Lab, Timeline, Academic & Travel, Family Vault, Locker*).
  - Simulates text input in the AI Search field.
  - Tests opening the Profile & Storage Console, clicking **"Export GZIP"**, **"Compact DB"**, and creating new accounts with avatar selections.
  - Verifies screen rendering and component test tags.

### Running Tests Locally:
```bash
# Run all unit and Compose UI interaction tests
gradle :app:testDebugUnitTest

# Verify Roborazzi screenshot baselines
gradle :app:verifyRoborazziDebug
```

---

## 🛠️ Local Development & Running in Android Studio

### Prerequisites
- [Android Studio Ladybug / Koala](https://developer.android.com/studio) or newer
- JDK 17+
- Android SDK 36 (compileSdk 36, minSdk 26)

### Setup Steps
1. Open Android Studio.
2. Select **Open** and select the root directory of this project.
3. Allow Gradle to sync dependencies (Room, Jetpack Compose, Moshi, Kotlin Coroutines).
4. Run the application on an emulator or physical device using the **Run ▶** button in Android Studio.
5. To build a standalone debug APK, run:
   ```bash
   gradle :app:assembleDebug
   ```
   The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🌐 Web APK Download Landing Page & Custom Domain Hosting

This repository includes a pre-built static web landing page in the [`/docs`](./docs) folder with the compiled `lifevault-ai.apk`, ready to be hosted on **GitHub Pages** or your **Custom Domain**.

### 🌟 How to Host the APK Download Site on Your Custom Domain:

1. **Push to GitHub**: Use the AI Studio **Export / GitHub Sync** button to push your project to GitHub.
2. **Enable GitHub Pages**:
   - Go to your repository's **Settings** $\rightarrow$ **Pages**.
   - Under **Build and deployment**, select **Deploy from a branch**.
   - Choose your branch (e.g. `main`) and set the directory to **`/docs`**.
   - Click **Save**.
3. **Connect Your Custom Domain**:
   - Under **Custom domain**, enter your domain (e.g. `vault.yourdomain.com`).
   - In your DNS provider (Cloudflare, GoDaddy, Namecheap, etc.), add a `CNAME` record pointing to `<your-github-username>.github.io` (or `A` records for apex domains).
   - Enable **Enforce HTTPS**.
4. **Share with Friends**: Anyone visiting your custom domain can directly click **"Download Android APK"** and follow the included Android installation guide.

See [`docs/README.md`](./docs/README.md) for detailed DNS configurations and step-by-step instructions.

---

## 🛡️ Privacy & Security Architecture

```
┌────────────────────────────────────────────────────────┐
│                   LifeVault AI App                     │
│  (100% Local Jetpack Compose + MVVM State Machine)     │
└──────────────────────────┬─────────────────────────────┘
                           │
         ┌─────────────────┴─────────────────┐
         ▼                                   ▼
┌──────────────────┐               ┌──────────────────┐
│ SQLite Room DB   │               │ Encrypted Auth   │
│ (Local Device)   │               │ (Private Prefs)  │
└────────┬─────────┘               └──────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────┐
│        GZIP High-Density Compression Engine            │
│  • Byte-level Deflate Stream Compression               │
│  • RFC 4648 Portable Base64 Archive Serialization      │
│  • Zero Network Sockets / Zero Cloud Telemetry         │
└────────────────────────────────────────────────────────┘
```

- **Zero Cloud Network Calls**: No data is uploaded or synchronized to remote servers.
- **Local SQLite Room Storage**: All memories, tags, relationships, and metadata are persisted exclusively on your local device.
- **Air-Gapped Portability**: Exported GZIP archives can be safely saved to external storage, USB drives, or physical backups.

