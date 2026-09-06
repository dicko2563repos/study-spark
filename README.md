# Study Spark

Personal Android study companion: short adaptive quizzes, skill tracking, course nudges, and a study agent with durable memory.

## Install the APK (sideload)

This is a **debug** build for personal use — not from the Play Store.

1. On your phone, allow installing apps from unknown sources / your browser/file manager.
2. Open the GitHub **Releases** page for this repo.
3. Download `StudySpark-0.1.0-debug.apk`.
4. Open the file and install.

Or with a USB cable and `adb`:

```bash
adb install -r StudySpark-0.1.0-debug.apk
```

## What's in this first build

- **Kotlin + Jetpack Compose** app (`app/`)
- **Room** database for skills, quizzes, courses, agent chat, preferences, mistakes
- **Seed quiz bank** (Python, C, JavaScript, HTML/CSS, Markdown) — verified hand-authored items
- **AI infra**: Gemini primary + Groq failover (`LlmRouter`), encrypted API keys, memory files under app storage `files/memory/`, quiz cache, rate-limit ledger
- **Gentle notifications** via WorkManager (customizable interval / heads-up style in Settings)
- **Study agent** chat that stores preference-like instructions for future quizzes
- **Courses** manual CRUD with `provider` / `externalId` / `syncMeta` reserved for later sync
- **`verify-service/`** placeholder for the local PC sandbox verifier (next step)

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (includes SDK + JDK guidance).
2. Open `/home/dicko/Projects/study-spark`.
3. Let Gradle sync; run on an emulator or device (API 26+).

Or from a terminal (after SDK packages are installed):

```bash
./scripts/setup-and-build.sh
```

A portable JDK 17 may already exist under `tools/jdk-17*` (gitignored). Android cmdline-tools may be under `tools/android-sdk` — complete package install via Android Studio or `sdkmanager` if the script reports missing platforms.

## Free AI keys

1. [Google AI Studio](https://aistudio.google.com/) → Gemini API key  
2. Optional failover: [Groq Console](https://console.groq.com/)  
3. Paste keys in **Settings** (stored with EncryptedSharedPreferences)

Without keys, quizzes still work from the seed bank; the agent runs in offline mode and still saves preferences/memory locally.

## Memory files

Exported under the app's private `files/memory/`:

- `profile.md`, `preferences.md`, `skills.json`, `courses.md`, `mistakes.json`, `agent-state.json`

Room remains source of truth; these files are packed into agent/planner context.

## Next

- Wire PC `verify-service` for executable code quizzes
- Prefetch verified AI quizzes into the cache
- Richer quiet-hours UI and module completion toggles
