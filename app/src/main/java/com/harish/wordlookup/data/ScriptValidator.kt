package com.harish.wordlookup.data

/**
 * Rejects translations written in the wrong alphabet.
 *
 * Models really do answer in the wrong script - the desktop app recorded all
 * of these against the same API and prompt:
 *   - "दमन" for the Kannada of "suppression" - Devanagari, correct word,
 *     useless to a Kannada reader.
 *   - "कृतज्ञತೆ" for "gratitude" - four Devanagari characters followed by one
 *     real Kannada syllable, so an "at least one own character" rule passes it.
 *   - "θಳಿಗೆದು" for "fragile" - a Greek theta spliced into Kannada, and Greek
 *     is not a target language here, so a list-based check sees it as neither
 *     own nor foreign and lets it through.
 *
 * Catching this costs nothing (no model call) and the failure is unambiguous,
 * unlike "is this the best word", which is deliberately not judged here.
 * Critically, it runs *before* the cache write: the cache has no expiry, so a
 * bad answer stored once would be served forever.
 *
 * Framework-free so it is unit-testable offline.
 */
object ScriptValidator {

    /**
     * Unicode ranges a translation into this language must actually touch.
     * Defined only where the target script differs from English's - a Latin
     * target (Swedish, Spanish, German...) shares the ASCII range with the
     * input word, so there is nothing a range check could prove. Absent means
     * "not checkable", treated as valid.
     */
    private val SCRIPT_RANGES: Map<String, List<IntRange>> = mapOf(
        "Kannada" to listOf(0x0C80..0x0CFF),
        "Hindi" to listOf(0x0900..0x097F),
        "Marathi" to listOf(0x0900..0x097F),
        "Tamil" to listOf(0x0B80..0x0BFF),
        "Telugu" to listOf(0x0C00..0x0C7F),
        "Malayalam" to listOf(0x0D00..0x0D7F),
        "Bengali" to listOf(0x0980..0x09FF),
        "Gujarati" to listOf(0x0A80..0x0AFF),
        "Punjabi" to listOf(0x0A00..0x0A7F),
        "Odia" to listOf(0x0B00..0x0B7F),
        "Urdu" to listOf(0x0600..0x06FF, 0x0750..0x077F),
        "Arabic" to listOf(0x0600..0x06FF, 0x0750..0x077F),
        "Russian" to listOf(0x0400..0x04FF),
        "Thai" to listOf(0x0E00..0x0E7F),
        "Korean" to listOf(0xAC00..0xD7AF, 0x1100..0x11FF),
        // Japanese mixes three scripts in ordinary text and a single word may
        // legitimately be written in any one of them, so all three count.
        "Japanese" to listOf(0x3040..0x309F, 0x30A0..0x30FF, 0x4E00..0x9FFF),
        "Chinese (Simplified)" to listOf(0x4E00..0x9FFF, 0x3400..0x4DBF),
    )

    /**
     * Latin and its extensions (Basic Latin through Latin Extended-B plus IPA)
     * are never counted as foreign: a translation may legitimately carry an
     * English acronym or loanword, and rejecting those would fail valid answers.
     */
    private const val LATIN_MAX = 0x02AF

    /**
     * True when [text] contains at least one character of [language]'s own
     * script AND none from any other checkable script - or when [language] has
     * no script entry, in which case there is nothing to reject.
     *
     * "At least one", not "every character": real translations mix in digits,
     * punctuation and sometimes a Latin acronym. The "no foreign letter" half
     * is what catches the mixed-script cases above; it works by Unicode letter
     * category rather than by a fixed list of known scripts, so a Greek theta
     * is caught even though Greek is not a target language. Checking foreign
     * scripts by range value rather than by language name is also why Hindi and
     * Marathi - which share one Unicode block - never flag each other.
     */
    fun usesExpectedScript(text: String, language: String): Boolean {
        val ranges = SCRIPT_RANGES[language] ?: return true
        var hasOwn = false
        var hasForeign = false
        for (ch in text) {
            val cp = ch.code
            if (ranges.any { cp in it }) {
                hasOwn = true
            } else if (isForeignLetter(cp)) {
                hasForeign = true
            }
        }
        return hasOwn && !hasForeign
    }

    /** True for a LETTER from some script other than the target's. Digits, punctuation, spaces and combining marks never trigger it. */
    private fun isForeignLetter(cp: Int): Boolean {
        if (cp <= LATIN_MAX) return false
        return Character.isLetter(cp)
    }

    /** Whether a script check can say anything at all about [language]. */
    fun isCheckable(language: String): Boolean = SCRIPT_RANGES.containsKey(language)
}
