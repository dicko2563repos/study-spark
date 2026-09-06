package com.studyspark.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.studyspark.app.data.entity.AgentMemoryEntity
import com.studyspark.app.data.entity.AgentMessageEntity
import com.studyspark.app.data.entity.ConceptMasteryEntity
import com.studyspark.app.data.entity.CourseEntity
import com.studyspark.app.data.entity.MistakeEntity
import com.studyspark.app.data.entity.ModuleEntity
import com.studyspark.app.data.entity.QuizAttemptEntity
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.entity.QuizPreferenceEntity
import com.studyspark.app.data.entity.RateLimitLedgerEntity
import com.studyspark.app.data.entity.TaskEntity
import com.studyspark.app.data.entity.TopicSkillEntity
import com.studyspark.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface TopicSkillDao {
    @Query("SELECT * FROM topic_skills ORDER BY displayName")
    fun observeAll(): Flow<List<TopicSkillEntity>>

    @Query("SELECT * FROM topic_skills WHERE enabled = 1")
    suspend fun enabled(): List<TopicSkillEntity>

    @Query("SELECT * FROM topic_skills WHERE topicId = :topicId LIMIT 1")
    suspend fun byTopic(topicId: String): TopicSkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TopicSkillEntity>)

    @Update
    suspend fun update(item: TopicSkillEntity)
}

@Dao
interface ConceptMasteryDao {
    @Query("SELECT * FROM concept_mastery WHERE topicId = :topicId")
    suspend fun forTopic(topicId: String): List<ConceptMasteryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ConceptMasteryEntity)
}

@Dao
interface QuizItemDao {
    @Query(
        """
        SELECT * FROM quiz_items
        WHERE consumed = 0 AND verified = 1 AND topicId IN (:topicIds)
        ORDER BY RANDOM()
        LIMIT 1
        """
    )
    suspend fun nextAvailable(topicIds: List<String>): QuizItemEntity?

    @Query("SELECT * FROM quiz_items WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): QuizItemEntity?

    @Query("SELECT COUNT(*) FROM quiz_items WHERE consumed = 0 AND verified = 1")
    fun observeReadyCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM quiz_items WHERE consumed = 0 AND verified = 1")
    suspend fun readyCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<QuizItemEntity>)

    @Query("UPDATE quiz_items SET consumed = 1 WHERE id = :id")
    suspend fun markConsumed(id: String)

    /**
     * Reactivate a random sample of consumed quizzes for enabled topics.
     * Uses RANDOM() so the same oldest seeds are not always revived.
     */
    @Query(
        """
        UPDATE quiz_items SET consumed = 0
        WHERE id IN (
            SELECT id FROM quiz_items
            WHERE consumed = 1 AND verified = 1 AND topicId IN (:topicIds)
            ORDER BY RANDOM()
            LIMIT :limit
        )
        """
    )
    suspend fun recycleConsumedQuizzes(topicIds: List<String>, limit: Int): Int

    @Query("SELECT contentHash FROM quiz_items")
    suspend fun allHashes(): List<String>

    @Query("DELETE FROM quiz_items WHERE consumed = 1")
    suspend fun deleteConsumed(): Int

    @Query("DELETE FROM quiz_items")
    suspend fun deleteAll(): Int
}

@Dao
interface QuizAttemptDao {
    @Insert
    suspend fun insert(attempt: QuizAttemptEntity): Long

    @Query("SELECT * FROM quiz_attempts ORDER BY answeredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<QuizAttemptEntity>>

    @Query("SELECT COUNT(*) FROM quiz_attempts WHERE correct = 1")
    fun observeCorrectCount(): Flow<Int>
}

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE active = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<CourseEntity>>

    @Insert
    suspend fun insert(course: CourseEntity): Long

    @Update
    suspend fun update(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ModuleDao {
    @Query("SELECT * FROM modules WHERE courseId = :courseId ORDER BY position ASC")
    fun observeForCourse(courseId: Long): Flow<List<ModuleEntity>>

    @Query(
        """
        SELECT * FROM modules
        WHERE completed = 0
        ORDER BY CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END, dueAt ASC, position ASC
        LIMIT 1
        """
    )
    suspend fun nextIncomplete(): ModuleEntity?

    @Insert
    suspend fun insert(module: ModuleEntity): Long

    @Update
    suspend fun update(module: ModuleEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE moduleId = :moduleId ORDER BY priority DESC, id ASC")
    fun observeForModule(moduleId: Long): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)
}

@Dao
interface QuizPreferenceDao {
    @Query("SELECT * FROM quiz_preferences WHERE active = 1 ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<QuizPreferenceEntity>>

    @Query("SELECT * FROM quiz_preferences WHERE active = 1 ORDER BY createdAt DESC")
    suspend fun active(): List<QuizPreferenceEntity>

    @Insert
    suspend fun insert(pref: QuizPreferenceEntity): Long
}

@Dao
interface AgentMessageDao {
    @Query("SELECT * FROM agent_messages ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AgentMessageEntity>>

    @Insert
    suspend fun insert(message: AgentMessageEntity): Long

    @Query("DELETE FROM agent_messages")
    suspend fun clear()
}

@Dao
interface AgentMemoryDao {
    @Query("SELECT * FROM agent_memories ORDER BY importance DESC, updatedAt DESC")
    suspend fun all(): List<AgentMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AgentMemoryEntity): Long
}

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes WHERE resolved = 0 ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<MistakeEntity>>

    @Insert
    suspend fun insert(mistake: MistakeEntity): Long
}

@Dao
interface RateLimitDao {
    @Query("SELECT * FROM rate_limit_ledger WHERE dayKey = :dayKey LIMIT 1")
    suspend fun forDay(dayKey: String): RateLimitLedgerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RateLimitLedgerEntity)
}
