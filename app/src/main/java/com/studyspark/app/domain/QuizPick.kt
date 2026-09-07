package com.studyspark.app.domain

import com.studyspark.app.data.entity.QuizItemEntity

data class QuizPick(
    val item: QuizItemEntity,
    val reason: String? = null
)
