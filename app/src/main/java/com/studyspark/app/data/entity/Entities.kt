package com.studyspark.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String = "Learner",
    val goals: String = "",
    val tonePreference: String = "encouraging",
    val studyStreakDays: Int = 0,
    val lastStudyEpochDay: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topic_skills",
    indices = [Index(value = ["topicId"], unique = true)]
)
data class TopicSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: String,
    val displayName: String,
    val enabled: Boolean = true,
    val level: Float = 1f,
    val accuracy: Float = 0.5f,
    val attempts: Int = 0,
    val correct: Int = 0,
    val lastPracticedAt: Long? = null,
    /** gentle | standard | stretch */
    val difficultyPref: String = "standard",
    /** Comma-separated concepts to avoid for now. */
    val scopeNotes: String = ""
)

@Entity(
    tableName = "concept_mastery",
    indices = [Index(value = ["topicId", "conceptId"], unique = true)]
)
data class ConceptMasteryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: String,
    val conceptId: String,
    val label: String,
    val mastery: Float = 0.2f,
    val timesSeen: Int = 0,
    val timesCorrect: Int = 0,
    val nextReviewAt: Long? = null
)

@Entity(tableName = "quiz_items")
data class QuizItemEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val format: String,
    val skillBand: Int,
    val prompt: String,
    val codeSnippet: String? = null,
    val choicesJson: String,
    val correctIndex: Int,
    val explanation: String,
    val conceptTagsJson: String = "[]",
    val verified: Boolean = true,
    val verificationMethod: String = "seed",
    val contentHash: String,
    val whyThisQuestion: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val consumed: Boolean = false,
    /** Retired items are never recycled into the ready bank. */
    val retired: Boolean = false
)

@Entity(tableName = "quiz_attempts")
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizItemId: String,
    val topicId: String,
    val selectedIndex: Int,
    val correct: Boolean,
    /** correct | incorrect | unknown | unfamiliar | retired */
    val outcome: String = "incorrect",
    val latencyMs: Long,
    val answeredAt: Long = System.currentTimeMillis(),
    /** Groups a practice run or Test my knowledge session. Empty on pre-0.3.4 rows. */
    val sessionId: String = ""
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val provider: String? = null,
    val externalId: String? = null,
    val syncMetaJson: String? = null,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "modules")
data class ModuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String,
    val position: Int = 0,
    val completed: Boolean = false,
    val dueAt: Long? = null,
    val provider: String? = null,
    val externalId: String? = null,
    val syncMetaJson: String? = null
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleId: Long,
    val title: String,
    val completed: Boolean = false,
    val dueAt: Long? = null,
    val priority: Int = 0,
    val provider: String? = null,
    val externalId: String? = null,
    val syncMetaJson: String? = null
)

@Entity(tableName = "quiz_preferences")
data class QuizPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instruction: String,
    val active: Boolean = true,
    val source: String = "user",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_messages")
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_memories")
data class AgentMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val content: String,
    val importance: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mistakes")
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topicId: String,
    val conceptId: String?,
    val quizItemId: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false
)

@Entity(tableName = "rate_limit_ledger")
data class RateLimitLedgerEntity(
    @PrimaryKey val dayKey: String,
    val geminiCalls: Int = 0,
    val groqCalls: Int = 0,
    val cacheHits: Int = 0
)
