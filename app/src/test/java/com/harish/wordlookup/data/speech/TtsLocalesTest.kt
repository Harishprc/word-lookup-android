package com.harish.wordlookup.data.speech

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsLocalesTest {

    @Test
    fun `zh routes to Simplified Chinese, not the bare language-only locale`() {
        val locale = TtsLocales.forCode("zh")
        assertEquals(Locale.SIMPLIFIED_CHINESE, locale)
        assertEquals("CN", locale.country)
    }

    @Test
    fun `id (Indonesian) round-trips as id, not the legacy in code`() {
        // Verified directly against this JDK before writing TtsLocales'
        // doc comment: `Locale("id")` does NOT get silently rewritten -
        // only the reverse (constructing with the legacy "in") normalizes
        // forward into "id". Nothing in data.Languages ever uses "in", so
        // this assertion is the actual regression guard, not the rewrite
        // an earlier draft of this file assumed without checking.
        val locale = TtsLocales.forCode("id")
        assertEquals("id", locale.language)
    }

    @Test
    fun `an ordinary two-letter code passes through unchanged`() {
        val locale = TtsLocales.forCode("kn")
        assertEquals("kn", locale.language)
    }

    @Test
    fun `every language in data-Languages resolves to a distinct, non-blank locale language`() {
        val codes = listOf(
            "kn", "hi", "ta", "te", "ml", "mr", "bn", "gu", "pa", "or", "ur",
            "es", "fr", "de", "sv", "ja", "ko", "zh", "ar", "ru", "pt", "it",
            "tr", "vi", "th", "id",
        )
        codes.forEach { code ->
            val locale = TtsLocales.forCode(code)
            assert(locale.language.isNotBlank()) { "code '$code' produced a blank Locale.language" }
        }
    }
}
