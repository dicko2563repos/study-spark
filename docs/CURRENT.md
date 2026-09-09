# Current snapshot

Update this file whenever a version ships or the next phase changes. Agents should read it before substantial work.

## Status

- **Latest APK:** 0.3.3 (`dist/StudySpark-0.3.3-debug.apk`)
- **Supply:** Gemini + Groq failover; hourly prefetch; Settings can clear/restore the quiz bank
- **Selection (Phase A + C1):** prefer unseen and items near the topic skill band; cooldown 12 / 4 / 3 after correct / miss / skip; recycle only if no new AI was added; pick-reason chip; skip ready items matching per-topic avoid-list when alternatives exist; rolling accuracy can nudge the next pick easier/harder without changing Settings
- **Stages (0.3.3):** skillBand 1–5 means First contact / Core facts / Use it / Combine / Edge — a typical next step for that topic, not a 10-level Settings slider. Gentle / Standard / Stretch still clamps how brave new items may be. New AI items are coerced into the requested stage range.
- **Learner controls (Phase B, first slice):** per-topic Gentle / Standard / Stretch; comma-separated avoid-list; planner honors both and recent prompts; Agent phrases like “make it harder/easier” update enabled topics
- **In-quiz actions (Phase B2, slim):** **Not familiar with this** retires the item, appends its concept tags to that topic’s avoid-list, and eases the topic one step — does **not** show the answer. **Don’t ask this again** retires only that item. **I don’t know** still reveals the stored explanation and consumes without a skill penalty
- **Not built:** Test my knowledge session (C2); in-quiz free-text coach / Explain this; user-defined topics (D); concept mastery map
- **Agent tab:** those difficulty phrases now write Settings; cannot invent concept chips yet

## Next product step

**Phase C2** — optional “Test my knowledge” session (bounded review + strengths/gaps recap). Do not skip D’s design bar later. Do not imply a concept mastery map; `concept_mastery` is still unwired.

## Pitfalls (do not relearn the hard way)

1. Home “N ready” includes recycled old items, not N new AI questions.
2. Groq `llama-3.3-70b-versatile` was shut down 2026-08-16 → 404. Current: `openai/gpt-oss-20b`.
3. Status used to say “add a Groq key” even when one was set.
4. Creating a new `LlmRouter` per call dropped session skip-Gemini; cache the router in `AppContainer`.
5. Uninstall/reinstall is a first-launch path (empty DB, no keys). In-place updates are not.
6. Exact prompt dupes are hashed without a timestamp; paraphrases can still look similar.
7. Room v3 adds `difficultyPref` and `scopeNotes` on `topic_skills`. Room v4 adds `retired` on `quiz_items`.
8. **I don’t know** already shows the explanation. Do not add a redundant “Explain this.” Not familiar / Don’t ask again must not reveal the answer.
9. Edge pick ranks the **existing ready pool** (skillBand vs topic level + last outcome). It is not a concept graph. Gentle still caps pick band at 1–2. Empty/thin pools fall back to whatever is left.
10. Seed items are mostly First contact / Core facts. Stage variety shows up after Generate more / prefetch. Do not hardcode a subject syllabus into the planner.

## Key code

- Quiz pick / supply: `StudyRepository` / `EdgePick`
- Stages: `SkillStage` / `TopicDifficulty.requestedBandRange`
- Planner: `QuizPlanner` / `LlmRouter` / `GroqClient`
- Prefetch: `QuizPrefetchWorker` in `QuizScheduler.kt`
- Seeds: `SeedData`
- Quiz UI: `QuizScreen` / `MainActivity`
