package com.harish.wordlookup.data.review

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSchedulerTest {

    private val fresh = ReviewScheduler.State(intervalDays = 0, ease = 2.5, reps = 0, lapses = 0)
    private val now = 1_000_000_000L
    private val dayMs = 24 * 60 * 60 * 1000L

    @Test
    fun `AGAIN on a fresh card resets reps, counts a lapse, and comes back in 10 minutes`() {
        val outcome = ReviewScheduler.grade(fresh, ReviewGrade.AGAIN, now)
        assertEquals(0, outcome.state.intervalDays)
        assertEquals(0, outcome.state.reps)
        assertEquals(1, outcome.state.lapses)
        assertEquals(2.3, outcome.state.ease, 0.0001)
        assertEquals(now + 10 * 60 * 1000L, outcome.dueAt)
    }

    @Test
    fun `AGAIN floors ease at 1_3 rather than letting it drop further`() {
        val lowEase = fresh.copy(ease = 1.35)
        val outcome = ReviewScheduler.grade(lowEase, ReviewGrade.AGAIN, now)
        assertEquals(1.3, outcome.state.ease, 0.0001)

        // A second AGAIN from exactly the floor must not push it lower still.
        val atFloor = outcome.state
        val secondOutcome = ReviewScheduler.grade(atFloor, ReviewGrade.AGAIN, now)
        assertEquals(1.3, secondOutcome.state.ease, 0.0001)
    }

    @Test
    fun `GOOD on a fresh card sets the first fixed step of 1 day`() {
        val outcome = ReviewScheduler.grade(fresh, ReviewGrade.GOOD, now)
        assertEquals(1, outcome.state.intervalDays)
        assertEquals(1, outcome.state.reps)
        assertEquals(2.5, outcome.state.ease, 0.0001) // GOOD never touches ease
        assertEquals(now + 1 * dayMs, outcome.dueAt)
    }

    @Test
    fun `GOOD on the second rep sets the fixed step of 3 days`() {
        val afterFirst = fresh.copy(intervalDays = 1, reps = 1)
        val outcome = ReviewScheduler.grade(afterFirst, ReviewGrade.GOOD, now)
        assertEquals(3, outcome.state.intervalDays)
        assertEquals(2, outcome.state.reps)
        assertEquals(now + 3 * dayMs, outcome.dueAt)
    }

    @Test
    fun `GOOD from the third rep onward multiplies interval by ease`() {
        val afterSecond = fresh.copy(intervalDays = 3, reps = 2, ease = 2.5)
        val outcome = ReviewScheduler.grade(afterSecond, ReviewGrade.GOOD, now)
        // round(3 * 2.5) = 8
        assertEquals(8, outcome.state.intervalDays)
        assertEquals(3, outcome.state.reps)
        assertEquals(now + 8 * dayMs, outcome.dueAt)
    }

    @Test
    fun `EASY on a fresh card jumps straight to the 3 day step and raises ease`() {
        val outcome = ReviewScheduler.grade(fresh, ReviewGrade.EASY, now)
        assertEquals(3, outcome.state.intervalDays)
        assertEquals(1, outcome.state.reps)
        assertEquals(2.65, outcome.state.ease, 0.0001)
        assertEquals(now + 3 * dayMs, outcome.dueAt)
    }

    @Test
    fun `EASY from the second rep onward multiplies interval by ease and 1_3`() {
        val afterFirst = fresh.copy(intervalDays = 3, reps = 1, ease = 2.5)
        val outcome = ReviewScheduler.grade(afterFirst, ReviewGrade.EASY, now)
        // round(3 * 2.5 * 1.3) = round(9.75) = 10
        assertEquals(10, outcome.state.intervalDays)
        assertEquals(2, outcome.state.reps)
        assertEquals(2.65, outcome.state.ease, 0.0001)
        assertEquals(now + 10 * dayMs, outcome.dueAt)
    }

    @Test
    fun `preview matches grade without needing to be called first`() {
        val previewed = ReviewScheduler.preview(fresh, ReviewGrade.GOOD, now)
        val graded = ReviewScheduler.grade(fresh, ReviewGrade.GOOD, now)
        assertEquals(graded, previewed)
    }

    @Test
    fun `a lapsed card graded AGAIN again keeps accumulating lapses`() {
        var state = fresh
        repeat(3) {
            state = ReviewScheduler.grade(state, ReviewGrade.AGAIN, now).state
        }
        assertEquals(3, state.lapses)
        assertEquals(0, state.reps)
    }
}
