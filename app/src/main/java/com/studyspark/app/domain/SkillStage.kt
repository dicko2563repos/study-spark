package com.studyspark.app.domain

/**
 * Typical-learning stages for a topic, mapped onto quiz item [skillBand] 1–5.
 * Not a Settings control and not a hardcoded syllabus.
 */
object SkillStage {
    fun clamp(band: Int): Int = band.coerceIn(1, 5)

    fun label(band: Int): String = when (clamp(band)) {
        1 -> "First contact"
        2 -> "Core facts"
        3 -> "Use it"
        4 -> "Combine"
        else -> "Edge"
    }

    fun plannerLegend(): String = """
        skillBand is a typical-learning STAGE for THIS topic (not a generic hardness score):
        1 First contact — vocabulary, recognition, the usual first encounter
        2 Core facts — the common first layer people learn after that
        3 Use it — a short realistic situation using that layer
        4 Combine — two ideas that often come next on a typical path through this topic
        5 Edge — one fair step past comfort, not trivia or unpublished detail
        Infer a fair next step from the topic name, learner level, avoid-list, and recent questions.
        Do not skip to advanced specialties they have not met. Do not write a fixed curriculum list.
    """.trimIndent()
}
