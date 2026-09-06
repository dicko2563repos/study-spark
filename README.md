# Study Spark

Personal Android study companion: short adaptive quizzes, skill tracking, course nudges, and a study agent with durable memory.

## Install the APK (sideload)

This is a **debug** build for personal use — not from the Play Store.

1. On your phone, allow installing apps from unknown sources / your browser/file manager.
2. Open: https://github.com/dicko2563repos/study-spark/blob/main/dist/StudySpark-0.2.6-debug.apk
3. Click **Download raw file**.
4. Open the file and install.

Or with a USB cable and `adb`:

```bash
adb install -r StudySpark-0.2.6-debug.apk
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

Without keys, quizzes still work from the seed bank (and recycle when empty); the agent runs in offline mode and still saves preferences/memory locally. With a Gemini key, the quiz screen auto-refills phone-safe knowledge questions when the bank runs low, and a background worker drip-fills toward a larger bank (~40) about once an hour when the device is online.

## Memory files

Exported under the app's private `files/memory/`:

- `profile.md`, `preferences.md`, `skills.json`, `courses.md`, `mistakes.json`, `agent-state.json`

Room remains source of truth; these files are packed into agent/planner context.

## Roadmap

See [ROADMAP.md](ROADMAP.md) for north-star values and phased plans (selection memory, learner controls, in-quiz coach line, edge-of-knowledge / Test my knowledge, open topics, platform work).
