package com.studyspark.app.di

import android.content.Context
import com.studyspark.app.ai.cache.QuizCache
import com.studyspark.app.ai.cache.RateLimitTracker
import com.studyspark.app.ai.llm.GeminiClient
import com.studyspark.app.ai.llm.GroqClient
import com.studyspark.app.ai.llm.LlmRouter
import com.studyspark.app.ai.memory.MemoryFileStore
import com.studyspark.app.ai.planner.QuizPlanner
import com.studyspark.app.data.db.StudySparkDatabase
import com.studyspark.app.data.repository.SettingsRepository
import com.studyspark.app.data.repository.StudyRepository

class AppContainer(context: Context) {
    val db = StudySparkDatabase.get(context)
    val settingsRepository = SettingsRepository(context)
    val memoryFileStore = MemoryFileStore(context, db)
    val rateLimitTracker = RateLimitTracker(db.rateLimitDao())
    val quizCache = QuizCache(db.quizItemDao())

    private var cachedRouter: LlmRouter? = null
    private var cachedGeminiKey: String? = null
    private var cachedGroqKey: String? = null

    private fun routerOrNull(): LlmRouter? {
        val settings = settingsRepository.current()
        val geminiKey = settings.geminiApiKey.trim()
        val groqKey = settings.groqApiKey.trim()
        if (geminiKey.isBlank() && groqKey.isBlank()) {
            cachedRouter = null
            cachedGeminiKey = null
            cachedGroqKey = null
            return null
        }
        val existing = cachedRouter
        if (existing != null && cachedGeminiKey == geminiKey && cachedGroqKey == groqKey) {
            return existing
        }

        val primary = when {
            geminiKey.isNotBlank() -> GeminiClient(apiKeyProvider = { settingsRepository.current().geminiApiKey })
            else -> GroqClient(apiKeyProvider = { settingsRepository.current().groqApiKey })
        }
        val failover = when {
            geminiKey.isNotBlank() && groqKey.isNotBlank() ->
                GroqClient(apiKeyProvider = { settingsRepository.current().groqApiKey })
            else -> null
        }
        val router = LlmRouter(
            primary = primary,
            failover = failover,
            rateLimitTracker = rateLimitTracker
        )
        cachedRouter = router
        cachedGeminiKey = geminiKey
        cachedGroqKey = groqKey
        return router
    }

    private fun plannerOrNull(): QuizPlanner? {
        val router = routerOrNull() ?: return null
        return QuizPlanner(router, memoryFileStore)
    }

    val studyRepository = StudyRepository(
        db = db,
        quizCache = quizCache,
        memory = memoryFileStore,
        routerProvider = ::routerOrNull,
        plannerProvider = ::plannerOrNull,
        rateLimitTracker = rateLimitTracker,
        hasGroqKey = { settingsRepository.current().groqApiKey.trim().isNotBlank() }
    )
}
