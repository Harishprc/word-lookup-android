package com.harish.wordlookup.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Process-lifetime wrapper around one android.speech.tts.TextToSpeech engine
 * - the app's only pronunciation path (see [SpeechProvider]'s doc). Owned by
 * WordLookupApp.speaker, joining the six singletons already built in
 * onCreate; no DI framework, per round 2's decision.
 *
 * Constructed lazily on first use and kept for the life of the process:
 * engine init is slow enough to be audible latency if paid on every card.
 */
class Speaker(context: Context) : SpeechProvider {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false

    // At most one pending utterance - a speak() that arrives before init
    // completes is held here and flushed on SUCCESS, never queued: a stale
    // word from an earlier card must not speak later just because init was
    // slow when it was tapped.
    private var pending: Pair<String, Locale>? = null

    private fun ensureInit(): TextToSpeech {
        tts?.let { return it }
        val engine = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) pending?.let { (text, locale) -> speakNow(text, locale) }
            pending = null
        }
        tts = engine
        return engine
    }

    /**
     * A snapshot, not a Flow: init may still be running when this is first
     * called (e.g. the very first card of a session), in which case
     * MISSING_DATA is the honest answer - the native speaker button stays
     * visible and offers the install route rather than silently vanishing,
     * and a later recomposition (the state that drives this is re-read on
     * every card show) picks up the real answer once init lands.
     */
    override fun availability(locale: Locale): SpeechAvailability {
        val engine = ensureInit()
        if (!ready) return SpeechAvailability.MISSING_DATA
        return when (engine.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_MISSING_DATA -> SpeechAvailability.MISSING_DATA
            TextToSpeech.LANG_NOT_SUPPORTED -> SpeechAvailability.NOT_SUPPORTED
            else -> SpeechAvailability.AVAILABLE
        }
    }

    /** QUEUE_FLUSH: a second tap (a different word, or the same one again) replaces, never queues. */
    override fun speak(text: String, locale: Locale) {
        if (text.isBlank()) return
        val engine = ensureInit()
        if (ready) {
            speakNow(text, locale)
        } else {
            pending = text to locale
        }
    }

    private fun speakNow(text: String, locale: Locale) {
        val engine = tts ?: return
        engine.language = locale
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word_lookup")
    }
}
