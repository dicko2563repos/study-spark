package com.studyspark.app.ai.llm

import com.studyspark.app.ai.cache.RateLimitTracker
import java.io.IOException

/**
 * Routes chat/generation to Gemini first, then Groq only when a failover client is configured
 * and the primary failure is retryable (or primary is unavailable).
 */
class LlmRouter(
    private val primary: LlmClient,
    private val failover: LlmClient?,
    private val rateLimitTracker: RateLimitTracker
) {
    suspend fun chat(request: LlmChatRequest): LlmChatResponse {
        return try {
            primary.chat(request).also {
                rateLimitTracker.recordCall(it.provider)
            }
        } catch (primaryError: LlmException) {
            val backup = failover
                ?: throw primaryError
            if (!primaryError.retryable && !shouldAttemptFailover(primaryError)) {
                throw primaryError
            }
            try {
                backup.chat(request).also {
                    rateLimitTracker.recordCall(it.provider)
                }
            } catch (secondary: Exception) {
                throw LlmException(
                    "Gemini: ${primaryError.message}. Groq: ${secondary.message}",
                    retryable = false
                )
            }
        } catch (e: IOException) {
            val backup = failover
                ?: throw LlmException("Network error: ${e.message}", retryable = true)
            try {
                backup.chat(request).also {
                    rateLimitTracker.recordCall(it.provider)
                }
            } catch (secondary: Exception) {
                throw LlmException(
                    "Network/Gemini failed (${e.message}). Groq: ${secondary.message}",
                    retryable = false
                )
            }
        }
    }

    private fun shouldAttemptFailover(error: LlmException): Boolean {
        val msg = error.message.orEmpty().lowercase()
        // Missing primary key → try secondary if present. Auth/config errors on a set key should
        // still allow optional failover, but never hide the Gemini message if Groq isn't set.
        return error.retryable ||
            msg.contains("not set") ||
            msg.contains("unavailable") ||
            msg.contains("429")
    }
}
