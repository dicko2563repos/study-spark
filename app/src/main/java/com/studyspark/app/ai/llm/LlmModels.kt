package com.studyspark.app.ai.llm

import kotlinx.serialization.Serializable

enum class LlmProvider { GEMINI, GROQ }

data class ChatMessage(
    val role: String,
    val content: String
)

data class LlmChatRequest(
    val messages: List<ChatMessage>,
    val temperature: Double = 0.4,
    val jsonMode: Boolean = false
)

data class LlmChatResponse(
    val text: String,
    val provider: LlmProvider,
    val model: String
)

@Serializable
data class GeneratedQuizDraft(
    val topicId: String,
    val format: String,
    val skillBand: Int,
    val prompt: String,
    val codeSnippet: String? = null,
    val choices: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val conceptTags: List<String> = emptyList(),
    val whyThisQuestion: String? = null,
    val runnableLanguage: String? = null
)

interface LlmClient {
    val provider: LlmProvider
    suspend fun chat(request: LlmChatRequest): LlmChatResponse
}

class LlmException(message: String, val retryable: Boolean = false) : Exception(message)
