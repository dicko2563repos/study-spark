# Current snapshot

Update this file whenever a version ships or the next phase changes. Agents should read it before substantial work.

## Status

- **Latest APK:** 0.3.4 (`dist/StudySpark-0.3.4-debug.apk`)
- **Supply:** Gemini + Groq failover; hourly prefetch; Settings can clear/restore the quiz bank
- **Selection (Phase A + C1):** prefer unseen and items near the topic skill band; cooldown 12 / 4 / 3 after correct / miss / skip; recycle only if no new AI was added; pick-reason chip; skip ready items matching per-topic avoid-list when alternatives exist; rolling accuracy can nudge the next pick easier/harder without changing Settings
- **Stages (0.3.3):** skillBand 1–5 means First contact / Core facts / Use it / Combine / Edge. Gentle / Standard / Stretch still clamps new items.
- **Concept memory (0.3.4):** answering writes normalized tags into `concept_mastery`; mistakes store a concept id when tags exist. Progress shows up to three shaky tags per topic — hints, not a full map.
- **Test my knowledge (C2, 0.3.4):** Home starts a 10-question session; pick biases toward weak tags when they exist; recap lists session strengths/gaps and says it is not a full map.
- **Learner controls (Phase B):** per-topic Gentle / Standard / Stretch; avoid-list; Agent harder/easier phrases update enabled topics
- **In-quiz actions (Phase B2):** Not familiar / Don’t ask again / I don’t know (unchanged)
- **Not built:** in-quiz free-text coach / Explain this; user-defined topics (D); Know/Learning/Not yet chips
- **Agent tab:** difficulty phrases write Settings; mistakes.json now exports recent misses

## Next product step

**Phase D design** — user-defined topics (not a Settings hack). Do not skip the design bar. Optional polish: QuizViewModel extract, add-topic UI after a plan.

## Pitfalls (do not relearn the hard way)

1. Home “N ready” includes recycled old items, not N new AI questions.
2. Groq `llama-3.3-70b-versatile` was shut down 2026-08-16 → 404. Current: `openai/gpt-oss-20b`.
3. Status used to say “add a Groq key” even when one was set.
4. Creating a new `LlmRouter` per call dropped session skip-Gemini; cache the router in `AppContainer`.
5. Uninstall/reinstall is a first-launch path (empty DB, no keys). In-place updates are not.
6. Exact prompt dupes are hashed without a timestamp; paraphrases can still look similar.
7. Room v3 `difficultyPref`/`scopeNotes`; v4 `retired`; v5 `quiz_attempts.sessionId`.
8. **I don’t know** already shows the explanation. Do not add a redundant “Explain this.”
9. Edge pick ranks the ready pool; Gentle caps bands 1–2. Empty pools fall back.
10. Seed items are mostly First contact / Core facts. Do not hardcode a subject syllabus.
11. Concept ids are normalized tag strings, not a taxonomy. Recap is session-scoped. First Test session may have few weak tags until answers accumulate.

## Key code

- Quiz pick / supply: `StudyRepository` / `EdgePick`
- Stages: `SkillStage` / `TopicDifficulty.requestedBandRange`
- Tags / recap: `ConceptTags` / `SessionRecap`
- Planner: `QuizPlanner` / `LlmRouter` / `GroqClient`
- Quiz UI: `QuizScreen` / `MainActivity`
