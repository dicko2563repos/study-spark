package com.studyspark.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
    version = 4,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE quiz_attempts ADD COLUMN outcome TEXT NOT NULL DEFAULT 'incorrect'"
                )
                db.execSQL(
                    "UPDATE quiz_attempts SET outcome = 'correct' WHERE correct = 1"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE topic_skills ADD COLUMN difficultyPref TEXT NOT NULL DEFAULT 'standard'"
                )
                db.execSQL(
                    "ALTER TABLE topic_skills ADD COLUMN scopeNotes TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE quiz_items ADD COLUMN retired INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): StudySparkDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudySparkDatabase::class.java,
                    "study_spark.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
