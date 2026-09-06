package com.studyspark.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    streakDays: Int,
    readyCount: Int,
    nudge: String?,
    onStartQuiz: () -> Unit,
    onOpenAgent: () -> Unit,
    onOpenCourses: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(24.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Text("Study Spark", style = MaterialTheme.typography.displayLarge)
            Text(
                "Short quizzes. Growing skills. A coach that remembers you.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text("Streak: $streakDays day${if (streakDays == 1) "" else "s"}", style = MaterialTheme.typography.titleLarge)
            Text("$readyCount verified quizzes ready", style = MaterialTheme.typography.bodyMedium)
            if (!nudge.isNullOrBlank()) {
                Text(nudge, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onStartQuiz, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Start a quiz")
            }
            OutlinedButton(onClick = onOpenAgent, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Ask the study agent")
            }
            OutlinedButton(onClick = onOpenCourses, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Courses & modules")
            }
        }
    }
}
