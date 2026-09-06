package com.studyspark.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studyspark.app.data.dao.AgentMemoryDao
import com.studyspark.app.data.dao.AgentMessageDao
import com.studyspark.app.data.dao.ConceptMasteryDao
import com.studyspark.app.data.dao.CourseDao
import com.studyspark.app.data.dao.MistakeDao
import com.studyspark.app.data.dao.ModuleDao
import com.studyspark.app.data.dao.QuizAttemptDao
import com.studyspark.app.data.dao.QuizItemDao
import com.studyspark.app.data.dao.QuizPreferenceDao
import com.studyspark.app.data.dao.RateLimitDao
import com.studyspark.app.data.dao.TaskDao
import com.studyspark.app.data.dao.TopicSkillDao
import com.studyspark.app.data.dao.UserProfileDao
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
import com.studyspark.app.data.seed.SeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfileEntity::class,
        TopicSkillEntity::class,
        ConceptMasteryEntity::class,
        QuizItemEntity::class,
        QuizAttemptEntity::class,
        CourseEntity::class,
        ModuleEntity::class,
        TaskEntity::class,
        QuizPreferenceEntity::class,
        AgentMessageEntity::class,
        AgentMemoryEntity::class,
        MistakeEntity::class,
        RateLimitLedgerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StudySparkDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun topicSkillDao(): TopicSkillDao
    abstract fun conceptMasteryDao(): ConceptMasteryDao
    abstract fun quizItemDao(): QuizItemDao
    abstract fun quizAttemptDao(): QuizAttemptDao
    abstract fun courseDao(): CourseDao
    abstract fun moduleDao(): ModuleDao
    abstract fun taskDao(): TaskDao
    abstract fun quizPreferenceDao(): QuizPreferenceDao
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun agentMemoryDao(): AgentMemoryDao
    abstract fun mistakeDao(): MistakeDao
    abstract fun rateLimitDao(): RateLimitDao

    companion object {
        @Volatile
        private var instance: StudySparkDatabase? = null

        fun get(context: Context): StudySparkDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudySparkDatabase::class.java,
                    "study_spark.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                get(context).let { database ->
                                    SeedData.seed(database)
                                }
                            }
                        }
                    })
                    .build()
                    .also { instance = it }
            }
        }
    }
}
