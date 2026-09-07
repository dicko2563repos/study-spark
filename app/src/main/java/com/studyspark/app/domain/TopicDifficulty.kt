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

    fun mergeAvoidNotes(existing: String, extras: List<String>, maxLen: Int = 280): String {
        val parts = existing.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        val seen = parts.map { it.lowercase() }.toMutableSet()
        extras.forEach { raw ->
            val phrase = raw.trim()
            if (phrase.length >= 3 && phrase.lowercase() !in seen) {
                parts.add(phrase)
                seen.add(phrase.lowercase())
            }
        }
        return parts.joinToString(", ").take(maxLen)
    }

    fun oneStepEasier(pref: String): String = when (normalize(pref)) {
        STRETCH -> STANDARD
        else -> GENTLE
    }

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
