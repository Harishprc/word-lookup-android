package com.harish.wordlookup.data

import java.util.Locale

/**
 * Order and glyphs are recovered byte-exact from the shipped v0.1.0 APK's
 * dex constants, with one deliberate addition: Swedish, present in the
 * companion desktop app's language list but missing from the Android build.
 * It slots between German and Japanese to match the desktop's own ordering
 * (see kannada_lookup/languages.py / README.md).
 */
object Languages {
    val ALL: List<Language> = listOf(
        Language("Kannada", "kn", "ಕ"),
        Language("Hindi", "hi", "अ"),
        Language("Tamil", "ta", "த"),
        Language("Telugu", "te", "త"),
        Language("Malayalam", "ml", "മ"),
        Language("Marathi", "mr", "म"),
        Language("Bengali", "bn", "ব"),
        Language("Gujarati", "gu", "ગ"),
        Language("Punjabi", "pa", "ਪ"),
        Language("Odia", "or", "ଓ"),
        Language("Urdu", "ur", "ا"),
        Language("Spanish", "es", "Ñ"),
        Language("French", "fr", "Ç"),
        Language("German", "de", "ß"),
        Language("Swedish", "sv", "Å"),
        Language("Japanese", "ja", "あ"),
        Language("Korean", "ko", "한"),
        Language("Chinese (Simplified)", "zh", "中"),
        Language("Arabic", "ar", "ع"),
        Language("Russian", "ru", "Я"),
        Language("Portuguese", "pt", "Ã"),
        Language("Italian", "it", "È"),
        Language("Turkish", "tr", "Ş"),
        Language("Vietnamese", "vi", "ơ"),
        Language("Thai", "th", "ท"),
        Language("Indonesian", "id", "ᬅ"),
    )

    val DEFAULT: Language = ALL.first { it.name == "Kannada" }

    private val byName: Map<String, Language> = ALL.associateBy { it.name }

    fun get(name: String): Language =
        byName[name] ?: Language(name, "", name.firstOrNull()?.uppercase(Locale.ROOT) ?: "?")
}
