package com.studyspark.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.studyspark.app.MainActivity
import com.studyspark.app.R
import com.studyspark.app.StudySparkApp
import com.studyspark.app.data.repository.AppSettings
import com.studyspark.app.data.repository.InterruptStyle
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object QuizScheduler {
    private const val NUDGE_WORK = "study_spark_quiz_nudge"
    private const val PREFETCH_WORK = "study_spark_quiz_prefetch"
    private const val PREFETCH_HOURS = 1L

    fun ensureScheduled(context: Context, settings: AppSettings) {
        ensureNudgeScheduled(context, settings)
        ensurePrefetchScheduled(context)
    }

    fun ensureNudgeScheduled(context: Context, settings: AppSettings) {
        if (!settings.notificationsEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(NUDGE_WORK)
            return
        }
        val minutes = settings.quizIntervalMinutes.coerceIn(15, 24 * 60)
        val request = PeriodicWorkRequestBuilder<QuizNudgeWorker>(
            minutes.toLong(),
            TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NUDGE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Hourly drip-fill of the quiz bank while the app may be closed. */
    fun ensurePrefetchScheduled(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<QuizPrefetchWorker>(
            PREFETCH_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PREFETCH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class QuizPrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as StudySparkApp
            app.container.studyRepository.prefetchQuizBankInBackground()
            Result.success()
        } catch (_: Exception) {
            // Next hourly window will try again; avoid tight retry loops on 429.
            Result.success()
        }
    }
}

class QuizNudgeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StudySparkApp
        val settings = app.container.settingsRepository.current()
        if (!settings.notificationsEnabled) return Result.success()
        if (inQuietHours(settings)) return Result.success()

        // Opportunistic top-up before reminding the user a quiz is ready
        runCatching { app.container.studyRepository.prefetchQuizBankInBackground() }

        val nudge = app.container.studyRepository.nextStudyNudge()
        val text = nudge ?: "Got 60 seconds? A short quiz is ready."

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_QUIZ, true)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = when (settings.interruptStyle) {
            InterruptStyle.GENTLE -> NotificationCompat.PRIORITY_DEFAULT
            InterruptStyle.HEADS_UP -> NotificationCompat.PRIORITY_HIGH
        }

        val notification = NotificationCompat.Builder(applicationContext, StudySparkApp.QUIZ_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Study Spark")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(priority)
            .build()

        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        }
        return Result.success()
    }

    private fun inQuietHours(settings: AppSettings): Boolean {
        val now = LocalTime.now().hour
        val start = settings.quietHoursStart
        val end = settings.quietHoursEnd
        return if (start == end) false
        else if (start < end) now in start until end
        else now >= start || now < end
    }
}
