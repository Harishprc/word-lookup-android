package com.harish.wordlookup.data

import java.util.Calendar

/** One candidate the widget could show - deliberately smaller than [RegisterEntry]/`ReviewCard`, since this needs neither's scheduling or timestamp fields, just enough to render. */
data class WordCandidate(val result: LookupResult, val language: String)

/**
 * Round 11: picks the home-screen widget's word deterministically for a
 * given calendar day, so a widget isn't showing a different word on every
 * one of Android's own ~30-minute refreshes. Pure Kotlin, `now` as a
 * parameter - same testability discipline as [DigestWeek]/
 * [com.harish.wordlookup.data.review.ReviewScheduler].
 */
object WordOfDay {
    /**
     * [overdue] (the register's currently-struggling words, if any) is
     * preferred over [all] (the full register) - a word-of-the-day drawn
     * from what the user is actually still learning is more useful than a
     * uniformly random pick, and costs nothing extra: the overdue query
     * already exists for the quiz ([com.harish.wordlookup.data.LookupRepository.loadDueBatch]).
     */
    fun pick(all: List<WordCandidate>, overdue: List<WordCandidate>, now: Long = System.currentTimeMillis()): WordCandidate? {
        val pool = overdue.ifEmpty { all }
        if (pool.isEmpty()) return null
        return pool[dayIndex(now).mod(pool.size)]
    }

    /**
     * The raw per-calendar-day index, exposed separately so a widget's
     * "tap to cycle" offset can be added on top of today's baseline
     * (`(dayIndex(now) + tapOffset).mod(pool.size)`) rather than always
     * starting a cycle from position 0 regardless of what day it is.
     */
    fun dayIndex(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        return cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)
    }
}
