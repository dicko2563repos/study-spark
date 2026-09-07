package com.studyspark.app.ai.planner

import com.studyspark.app.ai.llm.ChatMessage
import com.studyspark.app.ai.llm.GeneratedQuizDraft
import com.studyspark.app.ai.llm.LlmChatRequest
import com.studyspark.app.ai.llm.LlmRouter
import com.studyspark.app.ai.memory.MemoryFileStore
import com.studyspark.app.ai.verify.LightQuizVerifier
import com.studyspark.app.ai.verify.VerificationResult
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.entity.TopicSkillEntity
import com.studyspark.app.domain.TopicDifficulty
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class QuizPlanner(
    private val router: LlmRouter,
    private val memory: MemoryFileStore,
    private val verifier: LightQuizVerifier = LightQuizVerifier()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateDraft(
        topic: TopicSkillEntity,
        recentPrompts: List<String> = emptyList()
    ): Pair<GeneratedQuizDraft, VerificationResult> {
        val context = memory.packContext()
        val avoid = topic.scopeNotes.trim()
        val avoidBlock = if (avoid.isBlank()) {
            "No extra concept bans."
        } else {
            "Do NOT test these concepts (learner has not covered them / asked to skip): $avoid"
        }
        val recentBlock = if (recentPrompts.isEmpty()) {
            ""
        } else {
            "Do not repeat or paraphrase these existing questions:\n" +
                recentPrompts.take(8).joinToString("\n") { "- $it" }
        }
        val prompt = """
            Create ONE short multiple-choice quiz item as JSON with fields:
            topicId, format, skillBand (1-5), prompt, codeSnippet (null), choices (3-4 strings),
            correctIndex, explanation, conceptTags (array), whyThisQuestion, runnableLanguage (null).

            Rules for this phone app (important):
            - format MUST be "knowledge" or "purpose" only (NOT output or syntax).
            - Do NOT include runnable code that must be executed to verify the answer.
            - codeSnippet must be null, or a tiny illustrative snippet that is NOT required to compute the answer.
            - topicId MUST be exactly "${topic.topicId}".
            - Topic focus: ${topic.displayName}, stored skill level ~ ${"%.1f".format(topic.level)}.
            - ${TopicDifficulty.promptHint(topic.difficultyPref, topic.level)}
            - $avoidBlock
            - Ask a genuinely different question from typical intro drills.
            $recentBlock
            Return JSON only, no markdown fences.
        """.trimIndent()

        val response = router.chat(
            LlmChatRequest(
                messages = listOf(
                    ChatMessage("system", context),
                    ChatMessage("user", prompt)
                ),
                jsonMode = true,
                temperature = 0.55
            )
        )
        val draft = json.decodeFromString<GeneratedQuizDraft>(extractJsonObject(response.text))
        val band = when (TopicDifficulty.normalize(topic.difficultyPref)) {
            TopicDifficulty.GENTLE -> draft.skillBand.coerceIn(1, 2)
            TopicDifficulty.STRETCH -> draft.skillBand.coerceIn(
                minOf(5, maxOf(3, topic.level.toInt() + 1)),
                5
            )
            else -> draft.skillBand.coerceIn(1, 5)
        }
        val safe = draft.copy(
            topicId = topic.topicId,
            format = if (draft.format in setOf("knowledge", "purpose")) draft.format else "knowledge",
            skillBand = band,
            runnableLanguage = null,
            codeSnippet = draft.codeSnippet?.takeIf { it.length < 120 }
        )
        val result = verifier.validateStructure(safe)
        return safe to result
    }

    fun toEntity(draft: GeneratedQuizDraft, verified: Boolean, method: String): QuizItemEntity {
        val choicesJson = draft.choices.joinToString(prefix = "[", postfix = "]") {
            "\"" + it.replace("\"", "\\\"") + "\""
        }
        val tagsJson = draft.conceptTags.joinToString(prefix = "[", postfix = "]") {
            "\"" + it.replace("\"", "\\\"") + "\""
        }
        val hashInput = listOf(
            draft.topicId, draft.format, draft.prompt, draft.codeSnippet.orEmpty(),
            choicesJson, draft.correctIndex.toString()
        ).joinToString("|")
        return QuizItemEntity(
            id = "gen-" + sha256(hashInput).take(16),
            topicId = draft.topicId,
            format = draft.format,
            skillBand = draft.skillBand.coerceIn(1, 5),
            prompt = draft.prompt,
            codeSnippet = draft.codeSnippet,
            choicesJson = choicesJson,
            correctIndex = draft.correctIndex,
            explanation = draft.explanation,
            conceptTagsJson = tagsJson,
            verified = verified,
            verificationMethod = method,
            contentHash = sha256(hashInput),
            whyThisQuestion = draft.whyThisQuestion
        )
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val fenced = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        val candidate = fenced ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start >= 0 && end > start) return candidate.substring(start, end + 1)
        return candidate
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
