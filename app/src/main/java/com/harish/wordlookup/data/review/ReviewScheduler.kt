package com.harish.wordlookup.data.review

import kotlin.math.max
import kotlin.math.roundToLong

/** The three grades offered in ui/ReviewScreen.kt after a card is revealed. */
enum class ReviewGrade { AGAIN, GOOD, EASY }

/**
 * The scheduling half of six columns on [com.harish.wordlookup.data.cache.LookupEntity]
 * (`dueAt`/`intervalDays`/`ease`/`reps`/`lapses`/`lastReviewedAt`). Pure
 * Kotlin, no Android imports - the same discipline as
 * [com.harish.wordlookup.data.ScriptValidator] and
 * [com.harish.wordlookup.service.SelectionExtractor]: extracting the logic
 * that actually needs testing away from anything that can't run on a plain
 * JVM, so ReviewSchedulerTest covers it with no device, no Room, no clock.
 *
 * SM-2-lite: three grades instead of SM-2's five, no fixed learning-step
 * ladder beyond the first two reps, and no per-card lapse limit. The three
 * grades and their effects are documented in the plan this shipped from
 * (CLAUDE.md's "Round 8" section carries the summary table); this is that
 * table as code.
 */
object ReviewScheduler {
    private const val MIN_EASE = 1.3
    private const val EASE_PENALTY = 0.2
    private const val EASE_BONUS = 0.15
    private const val EASY_BONUS = 1.3
    private const val AGAIN_DELAY_MS = 10 * 60 * 1000L
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    data class State(
        val intervalDays: Int,
        val ease: Double,
        val reps: Int,
        val lapses: Int,
    )

    data class Outcome(
        val state: State,
        val dueAt: Long,
    )

    /**
     * [now] is a parameter, not System.currentTimeMillis() read internally -
     * the whole reason this object has no Android import is so a test can
     * pin time exactly; a hidden clock read would quietly undo that.
     */
    fun grade(current: State, grade: ReviewGrade, now: Long): Outcome = when (grade) {
        ReviewGrade.AGAIN -> Outcome(
            state = current.copy(
                intervalDays = 0,
                ease = max(MIN_EASE, current.ease - EASE_PENALTY),
                reps = 0,
                lapses = current.lapses + 1,
            ),
            dueAt = now + AGAIN_DELAY_MS,
        )

        ReviewGrade.GOOD -> {
            val nextInterval = nextGoodInterval(current)
            Outcome(
                state = current.copy(intervalDays = nextInterval, reps = current.reps + 1),
                dueAt = now + nextInterval * DAY_MS,
            )
        }

        ReviewGrade.EASY -> {
            val nextInterval = nextEasyInterval(current)
            val nextEase = current.ease + EASE_BONUS
            Outcome(
                state = current.copy(intervalDays = nextInterval, ease = nextEase, reps = current.reps + 1),
                dueAt = now + nextInterval * DAY_MS,
            )
        }
    }

    /** What [grade] would produce, without committing it - what the grade buttons' own interval labels show. */
    fun preview(current: State, grade: ReviewGrade, now: Long): Outcome = grade(current, grade, now)

    private fun nextGoodInterval(current: State): Int = when (current.reps) {
        0 -> 1
        1 -> 3
        else -> (current.intervalDays * current.ease).roundToLong().toInt().coerceAtLeast(1)
    }

    private fun nextEasyInterval(current: State): Int = when (current.reps) {
        0 -> 3
        else -> (current.intervalDays * current.ease * EASY_BONUS).roundToLong().toInt().coerceAtLeast(1)
    }
}
