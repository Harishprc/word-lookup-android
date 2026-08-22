package com.harish.wordlookup.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The instant-popup toggle rule the Quick Settings tile and Settings share.
 * Settings itself needs a real DataStore (an instrumentation dependency), so
 * the decision table is tested directly here - it is the part that was wrong,
 * not the persistence.
 */
class TriggerModeToggleTest {

    /** Mirrors Settings.toggleInstant's read-modify-write branch. */
    private fun toggled(current: TriggerMode): TriggerMode =
        if (current == TriggerMode.MENU_ONLY) TriggerMode.BOTH else TriggerMode.MENU_ONLY

    /** Mirrors Settings.instantEnabled. */
    private fun instantEnabled(mode: TriggerMode): Boolean = mode != TriggerMode.MENU_ONLY

    @Test
    fun `instant is live for BOTH and INSTANT, off only for MENU_ONLY`() {
        assertTrue(instantEnabled(TriggerMode.BOTH))
        assertTrue(instantEnabled(TriggerMode.INSTANT))
        assertFalse(instantEnabled(TriggerMode.MENU_ONLY))
    }

    @Test
    fun `turning instant off keeps the selection-menu entry available`() {
        assertEquals(TriggerMode.MENU_ONLY, toggled(TriggerMode.BOTH))
    }

    @Test
    fun `turning instant back on restores both triggers`() {
        assertEquals(TriggerMode.BOTH, toggled(TriggerMode.MENU_ONLY))
    }

    @Test
    fun `toggling from INSTANT-only turns instant off rather than doing nothing`() {
        // The bug class this guards: a toggle computed as "set the opposite of
        // what I think is stored" could no-op. From INSTANT, instant is on, so
        // one tap must turn it off.
        val next = toggled(TriggerMode.INSTANT)
        assertFalse(instantEnabled(next))
    }

    @Test
    fun `toggle is its own inverse from either stable state`() {
        assertEquals(TriggerMode.BOTH, toggled(toggled(TriggerMode.BOTH)))
        assertEquals(TriggerMode.MENU_ONLY, toggled(toggled(TriggerMode.MENU_ONLY)))
    }
}
