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

    fun requestedBandRange(pref: String, level: Float): IntRange {
        val target = kotlin.math.round(level.coerceIn(0.5f, 5f)).toInt().coerceIn(1, 5)
        return when (normalize(pref)) {
            GENTLE -> 1..2
            STRETCH -> {
                val lo = minOf(5, maxOf(3, target + 1))
                lo..5
            }
            else -> {
                val lo = (target - 1).coerceAtLeast(1)
                val hi = (target + 1).coerceAtMost(5)
                lo..hi
            }
        }
    }

    fun promptHint(pref: String, level: Float): String {
        val range = requestedBandRange(pref, level)
        val stageHint = range.joinToString(" / ") { "${it} ${SkillStage.label(it)}" }
        return when (normalize(pref)) {
            GENTLE ->
                "Preference: GENTLE. skillBand MUST be ${range.first}–${range.last} ($stageHint). Simple wording, common facts, no trick options."
            STRETCH ->
                "Preference: STRETCH. skillBand MUST be ${range.first}–${range.last} ($stageHint). Harder than comfort but one fair right answer. Do not require unpublished trivia."
            else ->
                "Preference: STANDARD. skillBand MUST be ${range.first}–${range.last} ($stageHint), near learner level ${"%.1f".format(level.coerceIn(0.5f, 5f))}."
        }
    }
}
