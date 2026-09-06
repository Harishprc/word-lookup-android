package com.harish.wordlookup.data.speech

import java.util.Locale

/**
 * data.Language.code -> java.util.Locale, for TextToSpeech. Verified against
 * the real JDK (not assumed): `Locale("id")` round-trips cleanly on modern
 * JVMs/Android (minSdk 24, post-Lollipop) - the historical "id"/"in"
 * ISO-639 rewrite only fires the other direction, normalizing the legacy
 * "in" *into* "id", which nothing in this codebase ever constructs (see
 * TtsLocalesTest for the assertion this rests on, not a comment alone).
 *
 * The one override that is real: bare "zh" is language-only, and several TTS
 * engines report LANG_MISSING_DATA for it even when they do have a Mandarin
 * voice, because they key their installed-voice table by region as well as
 * language. Routing to Locale.SIMPLIFIED_CHINESE (zh_CN) matches what
 * data.Languages.ALL's "Chinese (Simplified)" entry actually means.
 */
object TtsLocales {
    private val OVERRIDES: Map<String, Locale> = mapOf(
        "zh" to Locale.SIMPLIFIED_CHINESE,
    )

    fun forCode(code: String): Locale = OVERRIDES[code] ?: Locale(code)
}
