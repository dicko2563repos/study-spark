package com.studyspark.app.ui.quiz

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.domain.QuizAnswerOutcome

@Composable
fun QuizScreen(
    item: QuizItemEntity?,
    choices: List<String>,
    selected: Int?,
    revealed: Boolean,
    outcome: QuizAnswerOutcome?,
    generating: Boolean,
    statusMessage: String?,
    onSelect: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onGenerateMore: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Quiz", style = MaterialTheme.typography.headlineMedium)
        if (item == null) {
            if (generating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Generating new quizzes…")
                }
            } else {
                Text(
                    statusMessage
                        ?: "No quizzes ready. Generate more with your Gemini key, or recycle seed questions."
                )
                Button(onClick = onGenerateMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Generate more quizzes")
                }
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
            return
        }

        AssistChip(onClick = {}, label = { Text(item.topicId) })
        item.whyThisQuestion?.let {
            Text("Why this question: $it", style = MaterialTheme.typography.bodyMedium)
        }
        Text(item.prompt, style = MaterialTheme.typography.titleLarge)
        item.codeSnippet?.let { code ->
            Text(
                code,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            )
        }
        choices.forEachIndexed { index, choice ->
            val label = "${'A' + index}. $choice"
            OutlinedButton(
                onClick = { if (!revealed) onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !revealed
            ) {
                Text(if (selected == index) "✓ $label" else label)
            }
        }
        if (!revealed) {
            OutlinedButton(
                onClick = onDontKnow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I don't know")
            }
            Button(onClick = onSubmit, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                Text("Check answer")
            }
        } else {
            Text(
                when (outcome) {
                    QuizAnswerOutcome.CORRECT -> "Correct — nice work."
                    QuizAnswerOutcome.UNKNOWN -> "No worries — here's how this works."
                    QuizAnswerOutcome.INCORRECT, null -> "Not quite — here's the idea."
                },
                style = MaterialTheme.typography.titleLarge
            )
            Text(item.explanation, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next question") }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done for now") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(item.format) })
            AssistChip(onClick = {}, label = { Text(if (item.verified) "verified" else "unverified") })
        }
        statusMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
