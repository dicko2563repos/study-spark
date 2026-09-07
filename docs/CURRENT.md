# Current snapshot

Update this file whenever a version ships or the next phase changes. Agents should read it before substantial work.

## Status

- **Latest APK:** 0.3.0 (`dist/StudySpark-0.3.0-debug.apk`)
- **Supply:** Gemini + Groq failover; hourly prefetch; Settings can clear/restore the quiz bank
- **Selection (Phase A, thin):** prefer unseen; cooldown 12 / 4 / 3 after correct / miss / skip; recycle only if no new AI was added; pick-reason chip; skip ready items matching per-topic avoid-list when alternatives exist
- **Learner controls (Phase B, first slice):** per-topic Gentle / Standard / Stretch; comma-separated avoid-list; planner honors both and recent prompts; Agent phrases like “make it harder/easier” update enabled topics
- **Not built:** in-quiz coach line (B2); Test my knowledge (C); user-defined topics (D)
- **Agent tab:** those difficulty phrases now write Settings; cannot invent concept chips yet

## Next product step

**Phase B2** — in-quiz coach line (not familiar / don’t ask again / explain), without turning Quiz into a chat thread.

## Pitfalls (do not relearn the hard way)

1. Home “N ready” includes recycled old items, not N new AI questions.
2. Groq `llama-3.3-70b-versatile` was shut down 2026-08-16 → 404. Current: `openai/gpt-oss-20b`.
3. Status used to say “add a Groq key” even when one was set.
4. Creating a new `LlmRouter` per call dropped session skip-Gemini; cache the router in `AppContainer`.
5. Uninstall/reinstall is a first-launch path (empty DB, no keys). In-place updates are not.
6. Exact prompt dupes are hashed without a timestamp; paraphrases can still look similar.
7. Room v3 adds `difficultyPref` and `scopeNotes` on `topic_skills`.

## Key code

- Quiz pick / supply: `StudyRepository`
- Planner: `QuizPlanner` / `LlmRouter` / `GroqClient`
- Prefetch: `QuizPrefetchWorker` in `QuizScheduler.kt`
- Seeds: `SeedData`
- Difficulty helpers: `TopicDifficulty`
