package com.studyspark.app.ai.memory

import android.content.Context
import com.studyspark.app.data.db.StudySparkDatabase
import com.studyspark.app.data.entity.AgentMemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Room is source of truth; markdown/json memory files are exported views for the agent context packer.
 */
class MemoryFileStore(
    private val context: Context,
    private val db: StudySparkDatabase
) {
    private val root: File
        get() = File(context.filesDir, "memory").also { it.mkdirs() }

    suspend fun exportAll() = withContext(Dispatchers.IO) {
        exportProfile()
        exportSkills()
        exportPreferences()
        exportCourses()
        exportMistakes()
        exportAgentState()
    }

    suspend fun packContext(extraUserMessage: String? = null): String = withContext(Dispatchers.IO) {
        exportAll()
        buildString {
            appendLine("=== SYSTEM RULES ===")
            appendLine("Be accurate. Never invent quiz answers that were not verified.")
            appendLine("Honor user quiz preferences. Be encouraging and concrete.")
            appendLine()
            appendSection("profile.md")
            appendSection("preferences.md")
            appendSection("skills.json")
            appendSection("courses.md")
            appendSection("mistakes.json")
            appendSection("agent-state.json")
            if (!extraUserMessage.isNullOrBlank()) {
                appendLine("=== CURRENT MESSAGE ===")
                appendLine(extraUserMessage)
            }
        }
    }

    suspend fun rememberPreference(instruction: String) = withContext(Dispatchers.IO) {
        db.quizPreferenceDao().insert(
            com.studyspark.app.data.entity.QuizPreferenceEntity(instruction = instruction)
        )
        db.agentMemoryDao().upsert(
            AgentMemoryEntity(kind = "preference", content = instruction, importance = 5)
        )
        exportPreferences()
    }

    private suspend fun exportProfile() {
        val profile = db.userProfileDao().get()
        write(
            "profile.md",
            """
            # Profile
            - Name: ${profile?.displayName ?: "Learner"}
            - Tone: ${profile?.tonePreference ?: "encouraging"}
            - Goals: ${profile?.goals?.ifBlank { "(not set)" }}
            - Streak days: ${profile?.studyStreakDays ?: 0}
            """.trimIndent()
        )
    }

    private suspend fun exportSkills() {
        val skills = db.topicSkillDao().enabled()
        val body = skills.joinToString(",\n") { skill ->
            """  {"topicId":"${skill.topicId}","name":"${skill.displayName}","level":${skill.level},"accuracy":${skill.accuracy},"attempts":${skill.attempts}}"""
        }
        write("skills.json", "[\n$body\n]\n")
    }

    private suspend fun exportPreferences() {
        val prefs = db.quizPreferenceDao().active()
        val lines = if (prefs.isEmpty()) {
            listOf("- (none yet)")
        } else {
            prefs.map { "- ${it.instruction}" }
        }
        write("preferences.md", "# Quiz & teaching preferences\n" + lines.joinToString("\n") + "\n")
    }

    private suspend fun exportCourses() {
        val next = db.moduleDao().nextIncomplete()
        write(
            "courses.md",
            buildString {
                appendLine("# Courses")
                appendLine("- Next incomplete module: ${next?.title ?: "(none)"} (id=${next?.id ?: "-"})")
                appendLine("- Provider/externalId/syncMeta fields are reserved for future course-provider sync.")
            }
        )
    }

    private suspend fun exportMistakes() {
        // Keep a compact placeholder file; detailed mistakes stream via Room UI
        write("mistakes.json", "[]\n")
    }

    private suspend fun exportAgentState() {
        val memories = db.agentMemoryDao().all().take(20)
        val body = memories.joinToString(",\n") {
            """  {"kind":"${it.kind}","content":${jsonString(it.content)},"importance":${it.importance}}"""
        }
        write("agent-state.json", "[\n$body\n]\n")
    }

    private fun write(name: String, content: String) {
        File(root, name).writeText(content)
    }

    private fun StringBuilder.appendSection(name: String) {
        appendLine("=== $name ===")
        appendLine(File(root, name).takeIf { it.exists() }?.readText().orEmpty().ifBlank { "(empty)" })
        appendLine()
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun memoryRoot(): File = root
}
