package com.studyspark.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studyspark.app.data.entity.TopicSkillEntity
import com.studyspark.app.data.repository.AppSettings
import com.studyspark.app.data.repository.InterruptStyle

@Composable
fun SettingsScreen(
    settings: AppSettings,
    topics: List<TopicSkillEntity>,
    bankStatus: String?,
    onSettingsChange: (AppSettings) -> Unit,
    onToggleTopic: (String, Boolean) -> Unit,
    onClearAnswered: () -> Unit,
    onClearAllQuizzes: () -> Unit,
    onRestoreSeeds: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Topics", style = MaterialTheme.typography.titleLarge)
        topics.forEach { topic ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(topic.displayName, modifier = Modifier.weight(1f))
                Switch(checked = topic.enabled, onCheckedChange = { onToggleTopic(topic.topicId, it) })
            }
        }
        Text("AI keys (stored encrypted on device)", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = settings.geminiApiKey,
            onValueChange = { onSettingsChange(settings.copy(geminiApiKey = it.trim())) },
            label = { Text("Gemini API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = settings.groqApiKey,
            onValueChange = { onSettingsChange(settings.copy(groqApiKey = it.trim())) },
            label = { Text("Groq API key (failover if Gemini hits 429)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text(
            "Tip: free Gemini tiers rate-limit quickly. With a Groq key set, after a Gemini 429 the app uses Groq (openai/gpt-oss-20b) for the rest of that session. The quiz bank also refills about once an hour when online.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Quiz bank", style = MaterialTheme.typography.titleLarge)
        Text(
            "Answered quizzes can be recycled into the ready count, which is why Home can show 20+ of the same items. Clear them to force new generation.",
            style = MaterialTheme.typography.bodyMedium
        )
        bankStatus?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onClearAnswered, modifier = Modifier.fillMaxWidth()) {
            Text("Remove answered quizzes")
        }
        Button(onClick = onClearAllQuizzes, modifier = Modifier.fillMaxWidth()) {
            Text("Clear entire quiz bank")
        }
        OutlinedButton(onClick = onRestoreSeeds, modifier = Modifier.fillMaxWidth()) {
            Text("Restore seed quizzes")
        }
        OutlinedTextField(
            value = settings.verifyServiceUrl,
            onValueChange = { onSettingsChange(settings.copy(verifyServiceUrl = it.trim())) },
            label = { Text("PC verify service URL (optional)") },
            placeholder = { Text("http://192.168.x.x:8080") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text("Notifications", style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Gentle quiz reminders", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(notificationsEnabled = it)) }
            )
        }
        OutlinedTextField(
            value = settings.quizIntervalMinutes.toString(),
            onValueChange = {
                val minutes = it.toIntOrNull() ?: return@OutlinedTextField
                onSettingsChange(settings.copy(quizIntervalMinutes = minutes.coerceIn(15, 1440)))
            },
            label = { Text("Interval (minutes)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text("Interrupt style")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.interruptStyle == InterruptStyle.GENTLE,
                onClick = { onSettingsChange(settings.copy(interruptStyle = InterruptStyle.GENTLE)) },
                label = { Text("Gentle") }
            )
            FilterChip(
                selected = settings.interruptStyle == InterruptStyle.HEADS_UP,
                onClick = { onSettingsChange(settings.copy(interruptStyle = InterruptStyle.HEADS_UP)) },
                label = { Text("Heads-up") }
            )
        }
        Text(
            "Quiet hours ${settings.quietHoursStart}:00–${settings.quietHoursEnd}:00 (editable later in a richer UI).",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
