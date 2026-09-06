package com.harish.wordlookup.ui

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.harish.wordlookup.WordLookupApp
import com.harish.wordlookup.data.LookupResult
import com.harish.wordlookup.data.Languages
import com.harish.wordlookup.data.speech.Speaker
import com.harish.wordlookup.data.speech.TtsLocales
import java.util.Locale

/**
 * One entry point every card host calls with nothing but the result it is
 * already showing: OverlayHost's instant popup, ProcessTextActivity's menu
 * popup, RegisterScreen's saved entries, and the round-8 quiz all reach
 * [WordLookupApp.speaker] through here rather than each wiring TTS by hand.
 *
 * [languageName] defaults to the app's *current* target language - correct
 * for a live lookup, which is always shown in that language. The register
 * (and the quiz, which reads from the same rows) overrides it with the
 * saved entry's own [com.harish.wordlookup.data.RegisterEntry.language],
 * since a word saved under a language the user has since switched away from
 * must still speak in the language it was actually saved in.
 */
@Composable
fun rememberCardSpeech(result: LookupResult, languageName: String? = null): CardSpeech {
    val context = LocalContext.current
    val app = context.applicationContext as WordLookupApp
    val resolvedLanguage = languageName ?: app.targetLanguageState.value
    return remember(result.original, result.translation, resolvedLanguage) {
        buildCardSpeech(
            context = context,
            speaker = app.speaker,
            original = result.original,
            translation = result.translation,
            languageCode = Languages.get(resolvedLanguage).code,
        )
    }
}

/** The non-composable core, kept separate so it needs nothing but plain values - no LocalContext lookup baked into it. */
fun buildCardSpeech(
    context: Context,
    speaker: Speaker,
    original: String,
    translation: String,
    languageCode: String,
): CardSpeech {
    val nativeLocale = TtsLocales.forCode(languageCode)
    return CardSpeech(
        english = SpeechAction(
            availability = speaker.availability(Locale.ENGLISH),
            onSpeak = { speaker.speak(original, Locale.ENGLISH) },
            onInstallVoice = { launchVoiceInstall(context) },
        ),
        native = SpeechAction(
            availability = speaker.availability(nativeLocale),
            onSpeak = { speaker.speak(translation, nativeLocale) },
            onInstallVoice = { launchVoiceInstall(context) },
        ),
    )
}

/**
 * FLAG_ACTIVITY_NEW_TASK is required here, not optional: the instant overlay
 * calls this from an AccessibilityService context, which has no Activity
 * behind it and throws without the flag. Harmless on the popup path, which
 * does have one.
 */
private fun launchVoiceInstall(context: Context) {
    val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
