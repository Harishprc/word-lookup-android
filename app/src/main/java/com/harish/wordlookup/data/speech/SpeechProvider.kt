package com.harish.wordlookup.data.speech

import java.util.Locale

/**
 * Whether a locale can be spoken right now. Three states, not two, because
 * "not available" has two very different responses: a missing voice can be
 * installed (see CardSpeech's install action), an unsupported language never
 * will be.
 */
enum class SpeechAvailability { AVAILABLE, MISSING_DATA, NOT_SUPPORTED }

/**
 * The pronunciation seam. [Speaker] (on-device android.speech.tts) is the
 * only implementation this app ships - free, offline, zero API calls, zero
 * tokens. A cloud voice (Sarvam Bulbul was evaluated) was priced (₹100 trial
 * credit, then ₹30/10,000 characters) and rejected against the standing
 * "free of cost, always" instruction. This interface is the only residue of
 * that evaluation: one seam, so a paid provider could be added later behind
 * the user's own key, without disturbing callers - not a second
 * implementation, and not dead code today.
 */
interface SpeechProvider {
    fun availability(locale: Locale): SpeechAvailability
    fun speak(text: String, locale: Locale)
}
