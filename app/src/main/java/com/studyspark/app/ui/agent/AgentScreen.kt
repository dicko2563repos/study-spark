package com.studyspark.app.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studyspark.app.data.entity.AgentMessageEntity

@Composable
fun AgentScreen(
    messages: List<AgentMessageEntity>,
    busy: Boolean,
    onSend: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Study agent", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Ask questions, or tell me how to present quizzes — I’ll remember.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val who = if (msg.role == "user") "You" else "Coach"
                Text("$who: ${msg.content}", style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Prefer more C pointer questions…") }
            )
            Button(
                enabled = !busy && draft.isNotBlank(),
                onClick = {
                    val text = draft.trim()
                    draft = ""
                    onSend(text)
                }
            ) { Text(if (busy) "…" else "Send") }
        }
    }
}
