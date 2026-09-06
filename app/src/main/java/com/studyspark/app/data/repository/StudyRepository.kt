package com.studyspark.app.data.repository

import com.studyspark.app.ai.cache.QuizCache
import com.studyspark.app.ai.cache.RateLimitTracker
import com.studyspark.app.ai.llm.ChatMessage
import com.studyspark.app.ai.llm.LlmChatRequest
import com.studyspark.app.ai.llm.LlmRouter
import com.studyspark.app.ai.memory.MemoryFileStore
import com.studyspark.app.ai.planner.QuizPlanner
import com.studyspark.app.ai.verify.VerificationResult
import com.studyspark.app.domain.QuizAnswerOutcome
import com.studyspark.app.data.db.StudySparkDatabase
import com.studyspark.app.data.entity.AgentMessageEntity
import com.studyspark.app.data.entity.CourseEntity
import com.studyspark.app.data.entity.MistakeEntity
import com.studyspark.app.data.entity.ModuleEntity
import com.studyspark.app.data.entity.QuizAttemptEntity
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.entity.TopicSkillEntity
import com.studyspark.app.data.entity.UserProfileEntity
import com.studyspark.app.data.seed.SeedData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

class StudyRepository(
    private val db: StudySparkDatabase,
    private val quizCache: QuizCache,
    private val memory: MemoryFileStore,
    private val routerProvider: () -> LlmRouter?,
    private val plannerProvider: () -> QuizPlanner?,
    private val rateLimitTracker: RateLimitTracker,
    private val hasGroqKey: () -> Boolean = { false }
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeProfile(): Flow<UserProfileEntity?> = db.userProfileDao().observe()
    fun observeTopics(): Flow<List<TopicSkillEntity>> = db.topicSkillDao().observeAll()
    fun observeReadyCount(): Flow<Int> = quizCache.observeReadyCount()
    fun observeCourses(): Flow<List<CourseEntity>> = db.courseDao().observeActive()
    fun observeModules(courseId: Long): Flow<List<ModuleEntity>> = db.moduleDao().observeForCourse(courseId)
    fun observeAgentMessages(): Flow<List<AgentMessageEntity>> = db.agentMessageDao().observeAll()
    fun observePreferences() = db.quizPreferenceDao().observeActive()
    fun observeRecentAttempts() = db.quizAttemptDao().observeRecent()

    suspend fun nextQuiz(): QuizItemEntity? {
        val enabled = db.topicSkillDao().enabled().map { it.topicId }
        if (enabled.isEmpty()) return null
        val item = quizCache.next(enabled)
        if (item != null) rateLimitTracker.recordCacheHit()
        return item
    }

    suspend fun answerQuiz(
        item: QuizItemEntity,
        selectedIndex: Int,
        latencyMs: Long,
        unknown: Boolean = false
    ): QuizAnswerOutcome {
        val outcome = when {
            unknown -> QuizAnswerOutcome.UNKNOWN
            selectedIndex == item.correctIndex -> QuizAnswerOutcome.CORRECT
            else -> QuizAnswerOutcome.INCORRECT
        }
        db.quizAttemptDao().insert(
            QuizAttemptEntity(
                quizItemId = item.id,
                topicId = item.topicId,
                selectedIndex = if (unknown) -1 else selectedIndex,
                correct = outcome == QuizAnswerOutcome.CORRECT,
                outcome = when (outcome) {
                    QuizAnswerOutcome.CORRECT -> "correct"
                    QuizAnswerOutcome.INCORRECT -> "incorrect"
                    QuizAnswerOutcome.UNKNOWN -> "unknown"
                },
                latencyMs = latencyMs
            )
        )
        db.quizItemDao().markConsumed(item.id)
        when (outcome) {
            QuizAnswerOutcome.CORRECT -> updateSkill(item.topicId, correct = true)
            QuizAnswerOutcome.INCORRECT -> {
                updateSkill(item.topicId, correct = false)
                db.mistakeDao().insert(
                    MistakeEntity(
                        topicId = item.topicId,
                        conceptId = null,
                        quizItemId = item.id,
                        note = item.prompt
                    )
                )
            }
            QuizAnswerOutcome.UNKNOWN -> Unit // no skill penalty, no mistake entry
        }
        bumpStreak()
        memory.exportAll()
        return outcome
    }

    suspend fun setTopicEnabled(topicId: String, enabled: Boolean) {
        val topic = db.topicSkillDao().byTopic(topicId) ?: return
        db.topicSkillDao().update(topic.copy(enabled = enabled))
        memory.exportAll()
    }

    suspend fun addCourse(title: String, firstModule: String?) {
        val id = db.courseDao().insert(CourseEntity(title = title))
        if (!firstModule.isNullOrBlank()) {
            db.moduleDao().insert(ModuleEntity(courseId = id, title = firstModule, position = 0))
        }
        memory.exportAll()
    }

    suspend fun toggleModuleComplete(module: ModuleEntity) {
        db.moduleDao().update(module.copy(completed = !module.completed))
        memory.exportAll()
    }

    suspend fun nextStudyNudge(): String? {
        val module = db.moduleDao().nextIncomplete() ?: return null
        return "Keep going: ${module.title} is waiting."
    }

    suspend fun sendAgentMessage(userText: String): String {
        db.agentMessageDao().insert(AgentMessageEntity(role = "user", content = userText))
        maybeCapturePreference(userText)

        val router = routerProvider()
        val reply = if (router == null) {
            offlineAgentReply(userText)
        } else {
            val context = memory.packContext(userText)
            try {
                router.chat(
                    LlmChatRequest(
                        messages = listOf(
                            ChatMessage("system", context),
                            ChatMessage("user", userText)
                        )
                    )
                ).text
            } catch (e: Exception) {
                "I hit a provider issue (${e.message}). Your notes are saved locally — try again after checking API keys in Settings."
            }
        }
        db.agentMessageDao().insert(AgentMessageEntity(role = "assistant", content = reply))
        memory.exportAll()
        return reply
    }

    suspend fun topUpKnowledgeQuizzesIfPossible() {
        ensureQuizSupply(minReady = DEFAULT_READY_TARGET)
    }

    /**
     * Background-friendly drip fill: add a few new AI quizzes when below [PREFETCH_TARGET].
     * Skips recycle unless the bank is empty (recycle is for interactive play, not prefetch).
     */
    suspend fun prefetchQuizBankInBackground(): String {
        val ready = db.quizItemDao().readyCount()
        if (ready >= PREFETCH_TARGET) {
            return "Prefetch skipped — bank already at $ready (target $PREFETCH_TARGET)."
        }
        return ensureQuizSupply(
            minReady = min(ready + MAX_AI_BACKGROUND, PREFETCH_TARGET),
            maxAi = MAX_AI_BACKGROUND,
            allowRecycle = ready == 0,
            interCallDelayMs = 800L
        )
    }

    /**
     * Generate phone-safe knowledge quizzes until [minReady] are available.
     * Prefers a small batch of new AI items (to stay under free-tier rate limits),
     * then optionally recycles consumed quizzes to fill the rest.
     */
    suspend fun ensureQuizSupply(
        minReady: Int = DEFAULT_READY_TARGET,
        maxAi: Int = MAX_AI_PER_TOPUP,
        allowRecycle: Boolean = true,
        interCallDelayMs: Long = 450L
    ): String {
        val topics = db.topicSkillDao().enabled()
        if (topics.isEmpty()) return "Enable at least one topic in Settings."
        val topicIds = topics.map { it.topicId }

        val existing = db.quizItemDao().readyCount()
        if (existing >= minReady) return "Ready: $existing quizzes in bank."

        val needed = (minReady - existing).coerceAtLeast(1)
        val planner = plannerProvider()
        var added = 0
        var lastError: String? = null
        var rateLimited = false

        if (planner != null) {
            var attempts = 0
            val aiBudget = min(needed, maxAi.coerceAtLeast(1))
            val maxAttempts = aiBudget * 2
            while (
                added < aiBudget &&
                db.quizItemDao().readyCount() < minReady &&
                attempts < maxAttempts &&
                !rateLimited
            ) {
                attempts++
                val topic = topics[attempts % topics.size]
                try {
                    val (draft, result) = planner.generateDraft(topic)
                    when (result) {
                        is VerificationResult.AcceptedLight -> {
                            val before = db.quizItemDao().readyCount()
                            quizCache.putVerified(
                                listOf(planner.toEntity(draft, verified = true, method = "light"))
                            )
                            if (db.quizItemDao().readyCount() > before) {
                                added++
                                if (added < aiBudget) delay(interCallDelayMs)
                            } else {
                                lastError = "Generated quiz was a duplicate"
                            }
                        }
                        is VerificationResult.NeedsSandbox -> {
                            lastError = "Skipped code quiz (needs PC verifier)"
                        }
                        is VerificationResult.Rejected -> {
                            lastError = result.reason
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: e::class.java.simpleName
                    if (isRateLimitError(lastError)) {
                        rateLimited = true
                        if (!hasGroqKey()) break
                    } else if (attempts >= 2) {
                        break
                    }
                }
            }
        } else {
            lastError = "No API key set"
        }

        var ready = db.quizItemDao().readyCount()
        var recycled = 0
        if (allowRecycle && ready < minReady) {
            val stillNeeded = (minReady - ready).coerceAtLeast(1)
            val recycleLimit = max(stillNeeded, min(minReady, 12))
            recycled = db.quizItemDao().recycleConsumedQuizzes(topicIds, recycleLimit)
            ready = db.quizItemDao().readyCount()
        }

        return buildSupplyStatus(
            added = added,
            recycled = recycled,
            ready = ready,
            hadPlanner = planner != null,
            hasGroqKey = hasGroqKey(),
            rateLimited = rateLimited,
            lastError = lastError
        )
    }

    suspend fun clearAnsweredQuizzes(): String {
        val removed = db.quizItemDao().deleteConsumed()
        val ready = db.quizItemDao().readyCount()
        return "Removed $removed answered quiz${if (removed == 1) "" else "zes"}. $ready still ready (unanswered)."
    }

    /**
     * Wipe the quiz bank so recycle cannot revive stale items. Does not reset skills or attempts.
     */
    suspend fun clearAllQuizzes(): String {
        db.quizItemDao().deleteAll()
        return "Quiz bank cleared. Use Generate more on Quiz, or Restore seed quizzes below."
    }

    suspend fun restoreSeedQuizzes(): String {
        db.quizItemDao().upsertAll(SeedData.seedQuizzes())
        val ready = db.quizItemDao().readyCount()
        return "Restored seed quizzes. Bank: $ready ready."
    }

    private fun isRateLimitError(message: String?): Boolean {
        val msg = message.orEmpty().lowercase()
        return "429" in msg || "rate" in msg || "resource_exhausted" in msg || "quota" in msg
    }

    private fun buildSupplyStatus(
        added: Int,
        recycled: Int,
        ready: Int,
        hadPlanner: Boolean,
        hasGroqKey: Boolean,
        rateLimited: Boolean,
        lastError: String?
    ): String {
        val parts = mutableListOf<String>()
        if (added > 0) parts += "Added $added new AI quiz${if (added == 1) "" else "zes"}"
        if (rateLimited) {
            parts += if (hasGroqKey) {
                "Gemini rate-limited (429); Groq failover is on — if no new quizzes appeared, Groq also failed${lastError?.let { " ($it)" } ?: ""}"
            } else {
                "Gemini rate-limited (429) — add a Groq key in Settings for failover, or wait a bit"
            }
        }
        if (recycled > 0) {
            parts += when {
                rateLimited -> "Recycled $recycled prior quiz${if (recycled == 1) "" else "zes"} so you can keep studying"
                hadPlanner && added == 0 ->
                    "AI top-up failed (${lastError ?: "unknown"}) — recycled $recycled prior quiz${if (recycled == 1) "" else "zes"}"
                !hadPlanner ->
                    "Recycled $recycled quiz${if (recycled == 1) "" else "zes"} (add a Gemini key for new AI questions)"
                else ->
                    "Recycled $recycled prior quiz${if (recycled == 1) "" else "zes"} to fill the bank"
            }
        }
        if (parts.isEmpty()) {
            return when {
                ready > 0 -> "Ready: $ready quizzes${lastError?.let { " (note: $it)" } ?: ""}."
                lastError != null -> "Could not refill quizzes: $lastError"
                else -> "No quizzes available. Enable topics or add a Gemini key."
            }
        }
        return parts.joinToString(". ") + ". Bank: $ready ready."
    }

    fun parseChoices(item: QuizItemEntity): List<String> =
        json.parseToJsonElement(item.choicesJson).jsonArray.map { it.jsonPrimitive.content }

    private suspend fun updateSkill(topicId: String, correct: Boolean) {
        val skill = db.topicSkillDao().byTopic(topicId) ?: return
        val attempts = skill.attempts + 1
        val correctCount = skill.correct + if (correct) 1 else 0
        val accuracy = correctCount.toFloat() / attempts.toFloat()
        val delta = if (correct) 0.08f else -0.05f
        val level = min(5f, max(0.5f, skill.level + delta))
        db.topicSkillDao().update(
            skill.copy(
                attempts = attempts,
                correct = correctCount,
                accuracy = accuracy,
                level = level,
                lastPracticedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun bumpStreak() {
        val profile = db.userProfileDao().get() ?: UserProfileEntity()
        val today = LocalDate.now().toEpochDay()
        val last = profile.lastStudyEpochDay
        val streak = when {
            last == null -> 1
            last == today -> profile.studyStreakDays
            last == today - 1 -> profile.studyStreakDays + 1
            else -> 1
        }
        db.userProfileDao().upsert(
            profile.copy(studyStreakDays = streak, lastStudyEpochDay = today, updatedAt = System.currentTimeMillis())
        )
    }

    private suspend fun maybeCapturePreference(userText: String) {
        val lowered = userText.lowercase()
        val looksLikePref = listOf("prefer", "always", "don't", "do not", "more questions", "less", "include", "remember")
            .any { it in lowered }
        if (looksLikePref && userText.length in 8..280) {
            memory.rememberPreference(userText.trim())
        }
    }

    private fun offlineAgentReply(userText: String): String {
        val prefHint = if (listOf("prefer", "always", "remember").any { it in userText.lowercase() }) {
            " I saved that as a quiz preference for future sessions."
        } else ""
        return "I'm in offline/local mode (no API keys yet). Your progress, courses, and memory files are still tracked.$prefHint Add a free Gemini or Groq key in Settings to unlock live coaching."
    }

    companion object {
        /** Interactive session target — keep at least this many ready when possible. */
        const val DEFAULT_READY_TARGET = 20
        /** Start background-style top-up in the UI when ready falls below this. */
        const val LOW_WATER_MARK = 10
        /** Long-term bank size the hourly prefetch worker aims for. */
        const val PREFETCH_TARGET = 40
        /** Max new AI quizzes per foreground top-up (avoids free-tier 429 bursts). */
        const val MAX_AI_PER_TOPUP = 3
        /** Slightly larger drip when the app is closed / WorkManager runs. */
        const val MAX_AI_BACKGROUND = 5
    }
}
