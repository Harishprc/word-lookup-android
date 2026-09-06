package com.harish.wordlookup.data

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class DigestWeekTest {

    private fun millisFor(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month, day, hour, 0, 0)
        return cal.timeInMillis
    }

    @Test
    fun `a Wednesday resolves to that week's Monday at midnight`() {
        val wednesday = millisFor(2026, Calendar.SEPTEMBER, 2, hour = 15)
        val expectedMonday = millisFor(2026, Calendar.AUGUST, 31, hour = 0)
        assertEquals(expectedMonday, DigestWeek.startOfWeekMillis(wednesday))
    }

    @Test
    fun `a Monday resolves to itself at midnight, not the prior week`() {
        val mondayAfternoon = millisFor(2026, Calendar.AUGUST, 31, hour = 18)
        val expectedMonday = millisFor(2026, Calendar.AUGUST, 31, hour = 0)
        assertEquals(expectedMonday, DigestWeek.startOfWeekMillis(mondayAfternoon))
    }

    @Test
    fun `a Sunday resolves to the Monday six days earlier`() {
        val sunday = millisFor(2026, Calendar.SEPTEMBER, 6, hour = 9)
        val expectedMonday = millisFor(2026, Calendar.AUGUST, 31, hour = 0)
        assertEquals(expectedMonday, DigestWeek.startOfWeekMillis(sunday))
    }

    @Test
    fun `rangeLabel spans Monday to Sunday with no dash`() {
        val weekStart = millisFor(2026, Calendar.AUGUST, 31, hour = 0)
        val label = DigestWeek.rangeLabel(weekStart)
        assertEquals("Mon 31 Aug to Sun 6 Sep", label)
        assertEquals(false, label.contains("—"))
        assertEquals(false, label.contains("-"))
    }
}
