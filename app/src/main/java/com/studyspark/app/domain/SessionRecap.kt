package com.studyspark.app.domain

data class SessionRecap(
    val answered: Int,
    val correct: Int,
    val incorrect: Int,
    val skipped: Int,
    val strengths: List<String>,
    val gaps: List<String>
)
