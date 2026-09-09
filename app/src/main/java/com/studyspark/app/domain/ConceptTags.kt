package com.studyspark.app.domain

/**
 * Normalize model-invented tags into stable concept ids.
 * Not a taxonomy — just enough consistency for mastery and recap.
 */
object ConceptTags {
    data class Ref(val id: String, val label: String)

    fun normalizeAll(raw: List<String>, limit: Int = 3): List<Ref> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Ref>()
        raw.forEach { value ->
            val ref = normalize(value) ?: return@forEach
            if (seen.add(ref.id)) out.add(ref)
        }
        return out.take(limit)
    }

    fun normalize(raw: String): Ref? {
        val cleaned = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.length < 3) return null
        val label = cleaned.take(40)
        val id = label.replace(' ', '-')
        return Ref(id = id, label = label)
    }
}
