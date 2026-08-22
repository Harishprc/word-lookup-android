package com.harish.wordlookup.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case here is a real failure the desktop app recorded against the live API. */
class ScriptValidatorTest {

    @Test
    fun `pure Devanagari rejected as a Kannada answer`() {
        // "suppression" -> "दमन" (Devanagari, correct word, wrong alphabet)
        assertFalse(ScriptValidator.usesExpectedScript("दमन", "Kannada"))
    }

    @Test
    fun `mostly-foreign-script with one real char is still rejected`() {
        // "gratitude" -> "कृतज्ञತೆ": 4 Devanagari chars + 1 real Kannada syllable.
        // An "at least one own char" rule alone would wrongly pass this.
        assertFalse(ScriptValidator.usesExpectedScript("कृतज्ञತೆ", "Kannada"))
    }

    @Test
    fun `a foreign letter from an unsupported script is caught by category, not by name`() {
        // "fragile" -> "θಳಿಗೆದು": a Greek theta spliced into Kannada. Greek has
        // no SCRIPT_RANGES entry, so this only works via the letter-category check.
        assertFalse(ScriptValidator.usesExpectedScript("θಳಿಗೆದು", "Kannada"))
    }

    @Test
    fun `a correct Kannada word passes`() {
        assertTrue(ScriptValidator.usesExpectedScript("ಆಕಾಶ", "Kannada"))
    }

    @Test
    fun `digits and punctuation never count as foreign`() {
        assertTrue(ScriptValidator.usesExpectedScript("ಆಕಾಶ 123!", "Kannada"))
    }

    @Test
    fun `a Latin acronym mixed into a correct answer is not flagged`() {
        assertTrue(ScriptValidator.usesExpectedScript("ಆಕಾಶ (NASA)", "Kannada"))
    }

    @Test
    fun `Hindi and Marathi share a Unicode block and never flag each other`() {
        val devanagariWord = "आकाश"
        assertTrue(ScriptValidator.usesExpectedScript(devanagariWord, "Hindi"))
        assertTrue(ScriptValidator.usesExpectedScript(devanagariWord, "Marathi"))
    }

    @Test
    fun `Latin-script target languages have nothing to check and always pass`() {
        assertTrue(ScriptValidator.usesExpectedScript("garbage text of any kind", "Swedish"))
        assertTrue(ScriptValidator.usesExpectedScript("", "German"))
    }

    @Test
    fun `an unknown language name is treated as not checkable`() {
        assertTrue(ScriptValidator.usesExpectedScript("anything", "Klingon"))
    }

    @Test
    fun `empty text has no own-script character so it is rejected for a checkable language`() {
        assertFalse(ScriptValidator.usesExpectedScript("", "Kannada"))
    }

    @Test
    fun `isCheckable reflects whether a script table entry exists`() {
        assertTrue(ScriptValidator.isCheckable("Kannada"))
        assertFalse(ScriptValidator.isCheckable("Swedish"))
    }
}
