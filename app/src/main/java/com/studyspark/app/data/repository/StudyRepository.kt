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
    private val rateLimitTracker: RateLimitTracker
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
        val planner = plannerProvider() ?: return
        val topics = db.topicSkillDao().enabled()
        topics.take(2).forEach { topic ->
            runCatching {
                val pair = planner.generateDraft(topic) ?: return@forEach
                val (draft, result) = pair
                when (result) {
                    is VerificationResult.AcceptedLight -> {
                        quizCache.putVerified(
                            listOf(planner.toEntity(draft, verified = true, method = result.method))
                        )
                    }
                    is VerificationResult.NeedsSandbox -> {
                        // Keep for later PC verify service — do not show as verified
                    }
                    is VerificationResult.Rejected -> Unit
                }
            }
        }
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
}
