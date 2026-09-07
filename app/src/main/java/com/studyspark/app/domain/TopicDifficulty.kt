package com.studyspark.app.domain

object TopicDifficulty {
    const val GENTLE = "gentle"
    const val STANDARD = "standard"
    const val STRETCH = "stretch"

    fun normalize(raw: String?): String = when (raw?.lowercase()) {
        GENTLE, STRETCH -> raw.lowercase()
        else -> STANDARD
    }

    fun avoidPhrases(scopeNotes: String): List<String> =
        scopeNotes.split(',', ';', '\n')
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 }

    fun promptHint(pref: String, level: Float): String {
        val band = level.coerceIn(0.5f, 5f)
        return when (normalize(pref)) {
            GENTLE ->
                "Difficulty: GENTLE. skillBand MUST be 1 or 2. Simple wording, common facts, no trick options."
            STRETCH ->
                "Difficulty: STRETCH. skillBand ${minOf(5, (band + 1f).toInt())}–5. Harder than comfort but one fair right answer. Do not require unpublished trivia."
            else ->
                "Difficulty: STANDARD. skillBand near learner level ${"%.1f".format(band)} (about ${(band.toInt()).coerceIn(1, 5)})."
        }
    }
}
