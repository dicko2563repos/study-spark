package com.studyspark.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studyspark.app.data.entity.TopicSkillEntity

@Composable
fun ProgressScreen(topics: List<TopicSkillEntity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        Text("Skill levels grow as you answer correctly and review weak spots.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(topics, key = { it.id }) { topic ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${topic.displayName} · level ${"%.1f".format(topic.level)}")
                    LinearProgressIndicator(progress = { (topic.level / 5f).coerceIn(0f, 1f) })
                    Text(
                        "${topic.correct}/${topic.attempts} correct · accuracy ${"%.0f".format(topic.accuracy * 100)}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
