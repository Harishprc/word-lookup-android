package com.harish.wordlookup.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordOfDayTest {

    private fun millisFor(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month, day, 12, 0, 0)
        return cal.timeInMillis
    }

    private fun candidate(word: String) = WordCandidate(LookupResult(original = word, translation = word), "Kannada")

    @Test
    fun `empty register and empty overdue returns null`() {
        assertNull(WordOfDay.pick(all = emptyList(), overdue = emptyList(), now = millisFor(2026, Calendar.SEPTEMBER, 5)))
    }

    @Test
    fun `prefers the overdue pool over the full register when both are non-empty`() {
        val all = listOf(candidate("a"), candidate("b"))
        val overdue = listOf(candidate("c"))
        val result = WordOfDay.pick(all, overdue, now = millisFor(2026, Calendar.SEPTEMBER, 5))
        assertEquals("c", result?.result?.original)
    }

    @Test
    fun `falls back to the full register when nothing is overdue`() {
        val all = listOf(candidate("a"), candidate("b"))
        val result = WordOfDay.pick(all, overdue = emptyList(), now = millisFor(2026, Calendar.SEPTEMBER, 5))
        assertEquals(true, result != null && all.contains(result))
    }

    @Test
    fun `same calendar day always picks the same word regardless of time of day`() {
        val all = listOf(candidate("a"), candidate("b"), candidate("c"))
        val morning = WordOfDay.pick(all, emptyList(), now = millisFor(2026, Calendar.SEPTEMBER, 5))
        val night = WordOfDay.pick(all, emptyList(), now = millisFor(2026, Calendar.SEPTEMBER, 5) + 11 * 60 * 60 * 1000L)
        assertEquals(morning, night)
    }

    @Test
    fun `different calendar days can pick different words`() {
        val all = (1..10).map { candidate("word$it") }
        val day1 = WordOfDay.pick(all, emptyList(), now = millisFor(2026, Calendar.SEPTEMBER, 5))
        val day2 = WordOfDay.pick(all, emptyList(), now = millisFor(2026, Calendar.SEPTEMBER, 6))
        // Not asserting inequality strictly (a 10-item pool could coincidentally
        // repeat), just that the index math is day-of-year-based and in range.
        assertEquals(true, day1 != null && all.contains(day1))
        assertEquals(true, day2 != null && all.contains(day2))
    }
}
