package com.harish.wordlookup.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure Kotlin, no Android imports - same discipline as [ScriptValidator] and
 * [com.harish.wordlookup.data.review.ReviewScheduler]: [now] is a parameter,
 * not a hidden clock read, so DigestWeekTest can pin it exactly.
 */
object DigestWeek {

    /** Midnight, local time, of the most recent Monday on or before [now]. */
    fun startOfWeekMillis(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        // Calendar.DAY_OF_WEEK is SUNDAY=1..SATURDAY=7; days-since-Monday is
        // 0 for Monday itself, 6 for the following Sunday.
        val daysSinceMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        cal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        return cal.timeInMillis
    }

    /** "Mon 31 Aug to Sun 6 Sep" - no dash, per the app's no-em-dash rule. */
    fun rangeLabel(weekStartMillis: Long): String {
        val format = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
        val start = Date(weekStartMillis)
        val end = Date(weekStartMillis + 6 * DAY_MS)
        return "${format.format(start)} to ${format.format(end)}"
    }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
}
