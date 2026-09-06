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
import kotlinx.serialization.json.Json
import java.security.MessageDigest

class QuizPlanner(
    private val router: LlmRouter,
    private val memory: MemoryFileStore,
    private val verifier: LightQuizVerifier = LightQuizVerifier()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateDraft(topic: TopicSkillEntity): Pair<GeneratedQuizDraft, VerificationResult>? {
        val context = memory.packContext()
        val prompt = """
            Create ONE short quiz item as JSON with fields:
            topicId, format (output|syntax|knowledge|purpose), skillBand (1-5), prompt,
            codeSnippet (nullable), choices (array of 3-4 strings), correctIndex,
            explanation, conceptTags (array), whyThisQuestion, runnableLanguage (python|c|javascript|null).
            Topic: ${topic.topicId} (${topic.displayName}), learner level ~ ${topic.level}.
            Keep snippets short. Prefer factual, checkable items.
            Return JSON only.
        """.trimIndent()

        val response = router.chat(
            LlmChatRequest(
                messages = listOf(
                    ChatMessage("system", context),
                    ChatMessage("user", prompt)
                ),
                jsonMode = true,
                temperature = 0.3
            )
        )
        val draft = json.decodeFromString<GeneratedQuizDraft>(response.text)
        val result = verifier.validateStructure(draft)
        return draft to result
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
            skillBand = draft.skillBand,
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

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
