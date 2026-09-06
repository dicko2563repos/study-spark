package com.studyspark.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.studyspark.app.di.AppContainer
import com.studyspark.app.notify.QuizScheduler

class StudySparkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        QuizScheduler.ensureScheduled(this, container.settingsRepository.current())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                QUIZ_CHANNEL_ID,
                "Study quizzes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders to answer a short study quiz"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val QUIZ_CHANNEL_ID = "study_quizzes"
    }
}
