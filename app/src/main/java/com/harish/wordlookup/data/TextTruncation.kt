package com.harish.wordlookup.data

/** Bounds API quota use per lookup (see kannada_lookup: selections capped at 500 chars). */
object TextTruncation {
    const val MAX_CHARS: Int = 500

    fun truncate(text: String, max: Int = MAX_CHARS): String {
        val trimmed = text.trim()
        if (trimmed.length <= max) return trimmed
        val cut = trimmed.substring(0, max)
        val lastSpace = cut.lastIndexOf(' ')
        return if (lastSpace > 0) cut.substring(0, lastSpace) else cut
    }
}
