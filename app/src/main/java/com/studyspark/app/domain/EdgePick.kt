package com.studyspark.app.domain

import com.studyspark.app.data.entity.QuizAttemptEntity
import com.studyspark.app.data.entity.QuizItemEntity
import com.studyspark.app.data.entity.TopicSkillEntity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rank a ready pool toward the edge of the learner's current level.
 * Does not change Settings difficulty. Falls back to random if the pool is empty.
 */
object EdgePick {
    const val CALIBRATE_WINDOW = 8
    const val CALIBRATE_MIN_SAMPLES = 4
    const val TOO_EASY = 0.85f
    const val TOO_HARD = 0.40f

    fun choose(
        pool: List<QuizItemEntity>,
        topics: Map<String, TopicSkillEntity>,
        lastByItem: Map<String, QuizAttemptEntity>,
        recent: List<QuizAttemptEntity>,
        testMode: Boolean = false,
        itemIsWeak: (QuizItemEntity) -> Boolean = { false }
    ): QuizPick? {
        if (pool.isEmpty()) return null
        val scored = pool.map { item ->
            val topic = topics[item.topicId]
            val last = lastByItem[item.id]
            val (accuracy, samples) = rollingAccuracy(item.topicId, recent)
            val base = baseTargetBand(topic)
            val target = calibratedTargetBand(topic, accuracy, samples)
            val weak = testMode && itemIsWeak(item)
            val score = score(item, target, last, testMode = testMode, weak = weak)
            Scored(item, last, base, target, accuracy, samples, score, weak)
        }
        val best = scored.minOf { it.score }
        val nearBest = scored.filter { it.score <= best + 2 }
        val pick = nearBest.random()
        return QuizPick(pick.item, reason(pick))
    }

    internal fun baseTargetBand(topic: TopicSkillEntity?): Int {
        val rounded = (topic?.level ?: 1f).roundToInt().coerceIn(1, 5)
        return when (TopicDifficulty.normalize(topic?.difficultyPref)) {
            TopicDifficulty.GENTLE -> minOf(2, rounded.coerceAtLeast(1))
            TopicDifficulty.STRETCH -> minOf(5, maxOf(rounded + 1, 3))
            else -> rounded
        }
    }

    internal fun calibratedTargetBand(
        topic: TopicSkillEntity?,
        accuracy: Float?,
        samples: Int
    ): Int {
        var target = baseTargetBand(topic)
        if (samples >= CALIBRATE_MIN_SAMPLES && accuracy != null) {
            target = when {
                accuracy >= TOO_EASY -> (target + 1).coerceAtMost(5)
                accuracy <= TOO_HARD -> (target - 1).coerceAtLeast(1)
                else -> target
            }
        }
        return when (TopicDifficulty.normalize(topic?.difficultyPref)) {
            TopicDifficulty.GENTLE -> target.coerceIn(1, 2)
            else -> target.coerceIn(1, 5)
        }
    }

    internal fun rollingAccuracy(
        topicId: String,
        recentNewestFirst: List<QuizAttemptEntity>
    ): Pair<Float?, Int> {
        val scored = recentNewestFirst.filter {
            it.topicId == topicId && (it.outcome == "correct" || it.outcome == "incorrect")
        }.take(CALIBRATE_WINDOW)
        if (scored.isEmpty()) return null to 0
        val hits = scored.count { it.correct }
        return hits.toFloat() / scored.size.toFloat() to scored.size
    }

    internal fun score(
        item: QuizItemEntity,
        target: Int,
        last: QuizAttemptEntity?,
        testMode: Boolean = false,
        weak: Boolean = false
    ): Int {
        val distance = abs(item.skillBand.coerceIn(1, 5) - target)
        var s = distance * 3
        if (last == null) {
            s -= 2
        } else {
            s += when (last.outcome) {
                "incorrect" -> -3
                "unknown" -> -1
                "correct" -> 2
                else -> 1
            }
        }
        if (testMode && weak) s -= 4
        if (testMode && last?.outcome == "incorrect") s -= 1
        return s
    }

    private fun reason(pick: Scored): String {
        val last = pick.last
        val bandGap = abs(pick.item.skillBand.coerceIn(1, 5) - pick.target)
        return when {
            pick.weak -> "Checking a weak spot"
            last?.outcome == "incorrect" -> "Reviewing a miss"
            last?.outcome == "unknown" -> "Reviewing something you skipped"
            last?.outcome == "unfamiliar" -> "Reviewing a concept you flagged"
            pick.samples >= CALIBRATE_MIN_SAMPLES &&
                pick.accuracy != null &&
                pick.accuracy <= TOO_HARD &&
                pick.item.skillBand < pick.base -> "A bit easier"
            pick.samples >= CALIBRATE_MIN_SAMPLES &&
                pick.accuracy != null &&
                pick.accuracy >= TOO_EASY &&
                pick.item.skillBand > pick.base -> "A bit more stretch"
            last == null && bandGap <= 1 -> "Near your level"
            last == null -> "New question"
            bandGap <= 1 -> "Near your level"
            else -> "Practice"
        }
    }

    private data class Scored(
        val item: QuizItemEntity,
        val last: QuizAttemptEntity?,
        val base: Int,
        val target: Int,
        val accuracy: Float?,
        val samples: Int,
        val score: Int,
        val weak: Boolean = false
    )
}
