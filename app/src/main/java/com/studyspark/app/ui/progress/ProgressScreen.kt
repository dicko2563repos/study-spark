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
import com.studyspark.app.data.entity.ConceptMasteryEntity
import com.studyspark.app.data.entity.TopicSkillEntity

@Composable
fun ProgressScreen(
    topics: List<TopicSkillEntity>,
    concepts: List<ConceptMasteryEntity> = emptyList()
) {
    val byTopic = concepts.groupBy { it.topicId }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Progress", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Topic levels move as you answer. Shaky tags appear after a few attempts — they are hints, not a full map.",
            style = MaterialTheme.typography.bodyMedium
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(topics, key = { it.id }) { topic ->
                val shaky = byTopic[topic.topicId].orEmpty()
                    .filter { it.timesSeen >= 1 }
                    .sortedBy { it.mastery }
                    .take(3)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${topic.displayName} · level ${"%.1f".format(topic.level)}")
                    LinearProgressIndicator(progress = { (topic.level / 5f).coerceIn(0f, 1f) })
                    Text(
                        "${topic.correct}/${topic.attempts} correct · accuracy ${"%.0f".format(topic.accuracy * 100)}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (shaky.isNotEmpty()) {
                        Text(
                            "Shaky: " + shaky.joinToString { it.label },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
