package com.studyspark.app.data.seed

import com.studyspark.app.data.db.StudySparkDatabase
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.entity.TopicSkillEntity
import com.studyspark.app.data.entity.UserProfileEntity
import java.security.MessageDigest

object SeedData {
    val launchTopics = listOf(
        TopicSkillEntity(topicId = "python", displayName = "Python", enabled = true, level = 1.2f),
        TopicSkillEntity(topicId = "c", displayName = "C", enabled = true, level = 1.0f),
        TopicSkillEntity(topicId = "javascript", displayName = "JavaScript", enabled = true, level = 1.1f),
        TopicSkillEntity(topicId = "html_css", displayName = "HTML/CSS", enabled = true, level = 1.0f),
        TopicSkillEntity(topicId = "markdown", displayName = "Markdown", enabled = true, level = 1.0f)
    )

    suspend fun seed(db: StudySparkDatabase) {
        db.userProfileDao().upsert(UserProfileEntity())
        db.topicSkillDao().upsertAll(launchTopics)
        db.quizItemDao().upsertAll(seedQuizzes())
    }

    private fun seedQuizzes(): List<QuizItemEntity> = listOf(
        quiz(
            id = "py-print-1",
            topicId = "python",
            format = "output",
            skillBand = 1,
            prompt = "What does this Python snippet print?",
            code = "print(2 + 2 * 3)",
            choices = listOf("8", "12", "10", "6"),
            correct = 0,
            explanation = "Multiplication binds tighter than addition: 2 + (2 * 3) = 8.",
            tags = listOf("operators", "precedence"),
            why = "Builds confidence with basic Python expression evaluation."
        ),
        quiz(
            id = "py-list-1",
            topicId = "python",
            format = "output",
            skillBand = 2,
            prompt = "What is the value of x after this runs?",
            code = "x = [1, 2, 3]\nx.append(4)\nprint(len(x))",
            choices = listOf("3", "4", "5", "Error"),
            correct = 1,
            explanation = "append adds one element, so the list length becomes 4.",
            tags = listOf("lists", "methods"),
            why = "Lists are core to early Python work."
        ),
        quiz(
            id = "c-printf-1",
            topicId = "c",
            format = "output",
            skillBand = 1,
            prompt = "What does this C program print?",
            code = "#include <stdio.h>\nint main(void) {\n  printf(\"%d\", 3 + 4);\n  return 0;\n}",
            choices = listOf("34", "7", "3+4", "undefined"),
            correct = 1,
            explanation = "printf with %d prints the integer result of 3 + 4, which is 7.",
            tags = listOf("stdio", "arithmetic"),
            why = "Confirms basic C expression and printf formatting."
        ),
        quiz(
            id = "c-syntax-1",
            topicId = "c",
            format = "syntax",
            skillBand = 2,
            prompt = "Which change fixes the syntax error in this snippet?",
            code = "int main(void) {\n  int x = 5\n  return 0;\n}",
            choices = listOf(
                "Add a semicolon after 5",
                "Remove return 0",
                "Change int x to float x",
                "Add #include <math.h>"
            ),
            correct = 0,
            explanation = "In C, statements must end with a semicolon.",
            tags = listOf("syntax", "statements"),
            why = "Syntax discipline early prevents cascading compile errors."
        ),
        quiz(
            id = "js-typeof-1",
            topicId = "javascript",
            format = "output",
            skillBand = 1,
            prompt = "What does this log?",
            code = "console.log(typeof null)",
            choices = listOf("\"null\"", "\"object\"", "\"undefined\"", "Error"),
            correct = 1,
            explanation = "In JavaScript, typeof null historically returns \"object\".",
            tags = listOf("typeof", "quirks"),
            why = "A classic JS fact that often appears in interviews and debugging."
        ),
        quiz(
            id = "js-const-1",
            topicId = "javascript",
            format = "knowledge",
            skillBand = 1,
            prompt = "Which statement about const in JavaScript is true?",
            code = null,
            choices = listOf(
                "The binding cannot be reassigned",
                "The value is always deeply immutable",
                "It is function-scoped like var",
                "It must be initialized later"
            ),
            correct = 0,
            explanation = "const prevents reassignment of the binding; object contents can still change.",
            tags = listOf("const", "scope"),
            why = "Solid fundamentals for modern JS and webdev."
        ),
        quiz(
            id = "html-semantic-1",
            topicId = "html_css",
            format = "knowledge",
            skillBand = 1,
            prompt = "Which element is best for the main page heading?",
            code = null,
            choices = listOf("<h1>", "<header>", "<strong>", "<title>"),
            correct = 0,
            explanation = "<h1> marks the primary heading content; <title> is for the document title in the head.",
            tags = listOf("semantics", "headings"),
            why = "Semantic HTML is foundational for accessible web pages."
        ),
        quiz(
            id = "css-specificity-1",
            topicId = "html_css",
            format = "knowledge",
            skillBand = 2,
            prompt = "Which selector is generally more specific?",
            code = null,
            choices = listOf("#main", ".main", "div", "*"),
            correct = 0,
            explanation = "ID selectors outrank class and type selectors in CSS specificity.",
            tags = listOf("specificity", "selectors"),
            why = "Specificity confusion is a common source of CSS bugs."
        ),
        quiz(
            id = "md-link-1",
            topicId = "markdown",
            format = "knowledge",
            skillBand = 1,
            prompt = "Which is valid Markdown for a link?",
            code = null,
            choices = listOf(
                "[Cursor](https://cursor.com)",
                "(Cursor)[https://cursor.com]",
                "<a>Cursor</a>(https://cursor.com)",
                "link:Cursor->https://cursor.com"
            ),
            correct = 0,
            explanation = "Markdown links use [text](url).",
            tags = listOf("links", "syntax"),
            why = "Markdown shows up constantly in notes, READMEs, and course writeups."
        ),
        quiz(
            id = "md-code-1",
            topicId = "markdown",
            format = "syntax",
            skillBand = 1,
            prompt = "How do you start a fenced code block in Markdown?",
            code = null,
            choices = listOf("```", "'''", "{{{", "###"),
            correct = 0,
            explanation = "Fenced code blocks begin (and end) with three backticks.",
            tags = listOf("code-blocks"),
            why = "Useful when documenting code while you study."
        )
    )

    private fun quiz(
        id: String,
        topicId: String,
        format: String,
        skillBand: Int,
        prompt: String,
        code: String?,
        choices: List<String>,
        correct: Int,
        explanation: String,
        tags: List<String>,
        why: String
    ): QuizItemEntity {
        val choicesJson = choices.joinToString(prefix = "[", postfix = "]") { "\"${it.replace("\"", "\\\"")}\"" }
        val tagsJson = tags.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val hashInput = "$topicId|$format|$prompt|$code|$choicesJson|$correct"
        return QuizItemEntity(
            id = id,
            topicId = topicId,
            format = format,
            skillBand = skillBand,
            prompt = prompt,
            codeSnippet = code,
            choicesJson = choicesJson,
            correctIndex = correct,
            explanation = explanation,
            conceptTagsJson = tagsJson,
            verified = true,
            verificationMethod = "seed",
            contentHash = sha256(hashInput),
            whyThisQuestion = why,
            consumed = false
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
