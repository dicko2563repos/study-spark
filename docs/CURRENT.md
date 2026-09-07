# Current snapshot

Update this file whenever a version ships or the next phase changes. Agents should read it before substantial work.

## Status

- **Latest APK:** 0.2.9 (`dist/StudySpark-0.2.9-debug.apk`)
- **Supply:** Gemini + Groq failover; hourly prefetch; Settings can clear/restore the quiz bank
- **Selection (Phase A, thin):** prefer unseen; cooldown 12 / 4 / 3 quizzes after correct / miss / skip; recycle only if no new AI was added; pick-reason chip
- **Not built:** learner difficulty/scope UI (Phase B); in-quiz coach line (B2); Test my knowledge (C); user-defined topics (D)
- **Agent tab:** keyword-captures some preferences into markdown; cannot change difficulty; existing ready quizzes are unchanged by chat

## Next product step

**Phase B** — per-topic difficulty / complexity / concept include-exclude, honored by planner and filtering. Optionally a small planner pass to reduce semantic near-duplicate questions (generation quality, not selection).

## Pitfalls (do not relearn the hard way)

1. Home “N ready” includes recycled old items, not N new AI questions.
2. Groq `llama-3.3-70b-versatile` was shut down 2026-08-16 → 404. Current: `openai/gpt-oss-20b`.
3. Status used to say “add a Groq key” even when one was set.
4. Creating a new `LlmRouter` per call dropped session skip-Gemini; cache the router in `AppContainer`.
5. Uninstall/reinstall is a first-launch path (empty DB, no keys). In-place updates are not.
6. Semantic lookalikes can still appear; cooldown only keys off quiz item id.

## Key code

- Quiz pick / supply: `StudyRepository`
- Planner: `QuizPlanner` / `LlmRouter` / `GroqClient`
- Prefetch: `QuizPrefetchWorker` in `QuizScheduler.kt`
- Seeds: `SeedData`
