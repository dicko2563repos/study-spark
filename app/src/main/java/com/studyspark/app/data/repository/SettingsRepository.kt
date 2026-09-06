package com.studyspark.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InterruptStyle { GENTLE, HEADS_UP }

data class AppSettings(
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val verifyServiceUrl: String = "",
    val quizIntervalMinutes: Int = 120,
    val quietHoursStart: Int = 22,
    val quietHoursEnd: Int = 7,
    val interruptStyle: InterruptStyle = InterruptStyle.GENTLE,
    val notificationsEnabled: Boolean = true
)

class SettingsRepository(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "study_spark_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _settings = MutableStateFlow(read())
    val settings: Flow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(current())
        prefs.edit()
            .putString(KEY_GEMINI, next.geminiApiKey)
            .putString(KEY_GROQ, next.groqApiKey)
            .putString(KEY_VERIFY_URL, next.verifyServiceUrl)
            .putInt(KEY_INTERVAL, next.quizIntervalMinutes)
            .putInt(KEY_QUIET_START, next.quietHoursStart)
            .putInt(KEY_QUIET_END, next.quietHoursEnd)
            .putString(KEY_INTERRUPT, next.interruptStyle.name)
            .putBoolean(KEY_NOTIFS, next.notificationsEnabled)
            .apply()
        _settings.value = next
    }

    private fun read(): AppSettings = AppSettings(
        geminiApiKey = prefs.getString(KEY_GEMINI, "").orEmpty(),
        groqApiKey = prefs.getString(KEY_GROQ, "").orEmpty(),
        verifyServiceUrl = prefs.getString(KEY_VERIFY_URL, "").orEmpty(),
        quizIntervalMinutes = prefs.getInt(KEY_INTERVAL, 120),
        quietHoursStart = prefs.getInt(KEY_QUIET_START, 22),
        quietHoursEnd = prefs.getInt(KEY_QUIET_END, 7),
        interruptStyle = runCatching {
            InterruptStyle.valueOf(prefs.getString(KEY_INTERRUPT, InterruptStyle.GENTLE.name)!!)
        }.getOrDefault(InterruptStyle.GENTLE),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFS, true)
    )

    companion object {
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_GROQ = "groq_api_key"
        private const val KEY_VERIFY_URL = "verify_service_url"
        private const val KEY_INTERVAL = "quiz_interval_minutes"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        private const val KEY_INTERRUPT = "interrupt_style"
        private const val KEY_NOTIFS = "notifications_enabled"
    }
}
