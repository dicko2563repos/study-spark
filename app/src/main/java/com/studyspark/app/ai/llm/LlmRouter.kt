package com.studyspark.app.ai.llm

import com.studyspark.app.ai.cache.RateLimitTracker
import java.io.IOException

/**
 * Routes chat/generation to Gemini first, then Groq on retryable failures.
 */
class LlmRouter(
    private val primary: LlmClient,
    private val failover: LlmClient,
    private val rateLimitTracker: RateLimitTracker
) {
    suspend fun chat(request: LlmChatRequest): LlmChatResponse {
        return try {
            primary.chat(request).also {
                rateLimitTracker.recordCall(it.provider)
            }
        } catch (e: LlmException) {
            if (!e.retryable) {
                // Still try failover if primary key missing but secondary may exist
                return failover.chat(request).also {
                    rateLimitTracker.recordCall(it.provider)
                }
            }
            try {
                failover.chat(request).also {
                    rateLimitTracker.recordCall(it.provider)
                }
            } catch (secondary: Exception) {
                throw LlmException(
                    "Both providers failed. Primary: ${e.message}; Failover: ${secondary.message}",
                    retryable = false
                )
            }
        } catch (e: IOException) {
            failover.chat(request).also {
                rateLimitTracker.recordCall(it.provider)
            }
        }
    }
}
