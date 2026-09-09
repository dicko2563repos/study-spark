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
import com.studyspark.app.domain.SessionRecap
import com.studyspark.app.domain.SkillStage

@Composable
fun QuizScreen(
    item: QuizItemEntity?,
    choices: List<String>,
    selected: Int?,
    revealed: Boolean,
    outcome: QuizAnswerOutcome?,
    generating: Boolean,
    statusMessage: String?,
    pickReason: String?,
    onSelect: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onNotFamiliar: () -> Unit,
    onDontAskAgain: () -> Unit,
    onGenerateMore: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    recap: SessionRecap? = null,
    sessionLabel: String? = null,
    nextLabel: String = "Next question",
    onSeeResults: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (recap != null) "Test recap" else "Quiz", style = MaterialTheme.typography.headlineMedium)
        sessionLabel?.takeIf { it.isNotBlank() }?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }

        if (recap != null) {
            Text(
                "This is from this session only — not a full map of what you know.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "${recap.correct} correct · ${recap.incorrect} missed · ${recap.skipped} skipped · ${recap.answered} answered",
                style = MaterialTheme.typography.titleLarge
            )
            if (recap.strengths.isNotEmpty()) {
                Text("Felt stronger", style = MaterialTheme.typography.titleMedium)
                recap.strengths.forEach { Text("• $it") }
            }
            if (recap.gaps.isNotEmpty()) {
                Text("Worth another look", style = MaterialTheme.typography.titleMedium)
                recap.gaps.forEach { Text("• $it") }
            }
            if (recap.strengths.isEmpty() && recap.gaps.isEmpty()) {
                Text(
                    "Not enough tagged concepts yet to split strengths and gaps. Keep answering — tags fill in as you go.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            return
        }

        statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            val isWarning = message.contains("failed", ignoreCase = true) ||
                message.contains("Could not", ignoreCase = true) ||
                message.contains("No API", ignoreCase = true) ||
                message.contains("rate-limited", ignoreCase = true) ||
                message.contains("429")
            val isInfo = message.contains("recycled", ignoreCase = true) && !isWarning
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isWarning -> MaterialTheme.colorScheme.error
                    isInfo -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

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
                if (statusMessage.isNullOrBlank()) {
                    Text("No quizzes ready. Generate more with your Gemini key, or recycle prior questions.")
                }
                Button(onClick = onGenerateMore, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
                    Text("Generate more quizzes")
                }
                if (onSeeResults != null) {
                    Button(onClick = onSeeResults, modifier = Modifier.fillMaxWidth()) {
                        Text("See results so far")
                    }
                }
            }
            OutlinedButton(onClick = onBack) { Text("Back") }
            return
        }

        AssistChip(onClick = {}, label = { Text(item.topicId) })
        pickReason?.takeIf { it.isNotBlank() }?.let {
            AssistChip(onClick = {}, label = { Text(it) })
        }
        AssistChip(onClick = {}, label = { Text(SkillStage.label(item.skillBand)) })
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
                onClick = onNotFamiliar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Not familiar with this")
            }
            OutlinedButton(
                onClick = onDontAskAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Don't ask this again")
            }
            OutlinedButton(
                onClick = onDontKnow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I don't know")
            }
            Button(onClick = onSubmit, enabled = selected != null, modifier = Modifier.fillMaxWidth()) {
                Text("Check answer")
            }
            OutlinedButton(
                onClick = onGenerateMore,
                enabled = !generating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (generating) "Generating…" else "Generate more quizzes")
            }
        } else {
            val showExplanation = outcome == QuizAnswerOutcome.CORRECT ||
                outcome == QuizAnswerOutcome.INCORRECT ||
                outcome == QuizAnswerOutcome.UNKNOWN
            Text(
                when (outcome) {
                    QuizAnswerOutcome.CORRECT -> "Correct — nice work."
                    QuizAnswerOutcome.UNKNOWN -> "No worries — here's how this works."
                    QuizAnswerOutcome.UNFAMILIAR ->
                        "We'll skip this idea for now. Similar questions will be avoided, and this topic goes a step easier if it wasn't already gentle."
                    QuizAnswerOutcome.RETIRED -> "This question won't come up again."
                    QuizAnswerOutcome.INCORRECT, null -> "Not quite — here's the idea."
                },
                style = MaterialTheme.typography.titleLarge
            )
            if (showExplanation) {
                Text(item.explanation, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text(nextLabel) }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Done for now") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text(item.format) })
            AssistChip(onClick = {}, label = { Text(if (item.verified) "verified" else "unverified") })
            AssistChip(onClick = {}, label = { Text(item.verificationMethod) })
        }
    }
}
