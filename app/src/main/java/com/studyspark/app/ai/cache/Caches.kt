package com.studyspark.app.ai.cache

import com.studyspark.app.ai.llm.LlmProvider
import com.studyspark.app.data.dao.QuizItemDao
import com.studyspark.app.data.dao.RateLimitDao
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.entity.RateLimitLedgerEntity
import java.time.LocalDate

class QuizCache(
    private val quizItemDao: QuizItemDao
) {
    suspend fun putVerified(items: List<QuizItemEntity>) {
        val existing = quizItemDao.allHashes().toSet()
        val fresh = items.filter { it.verified && it.contentHash !in existing }
        if (fresh.isNotEmpty()) quizItemDao.upsertAll(fresh)
    }

    suspend fun next(topicIds: List<String>): QuizItemEntity? =
        quizItemDao.nextAvailable(topicIds)

    fun observeReadyCount() = quizItemDao.observeReadyCount()
}

class RateLimitTracker(
    private val rateLimitDao: RateLimitDao
) {
    private fun today(): String = LocalDate.now().toString()

    suspend fun recordCall(provider: LlmProvider) {
        val key = today()
        val current = rateLimitDao.forDay(key) ?: RateLimitLedgerEntity(dayKey = key)
        val updated = when (provider) {
            LlmProvider.GEMINI -> current.copy(geminiCalls = current.geminiCalls + 1)
            LlmProvider.GROQ -> current.copy(groqCalls = current.groqCalls + 1)
        }
        rateLimitDao.upsert(updated)
    }

    suspend fun recordCacheHit() {
        val key = today()
        val current = rateLimitDao.forDay(key) ?: RateLimitLedgerEntity(dayKey = key)
        rateLimitDao.upsert(current.copy(cacheHits = current.cacheHits + 1))
    }

    suspend fun snapshot(): RateLimitLedgerEntity =
        rateLimitDao.forDay(today()) ?: RateLimitLedgerEntity(dayKey = today())
}
