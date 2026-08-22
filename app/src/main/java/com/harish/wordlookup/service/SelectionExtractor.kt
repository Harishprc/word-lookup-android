package com.harish.wordlookup.service

/**
 * Works out what the user actually selected, from the loose signals an
 * accessibility event carries.
 *
 * The hard case is an event with no selection indices (`-1`), which WebViews
 * and reader apps send routinely. v0.1.0 treated that as "select everything"
 * and looked up the node's entire text - so double-tapping one word in Chrome
 * translated the whole paragraph. A range is never guessed from a whole-node
 * dump any more: without a real range we would rather do nothing.
 *
 * Kept free of framework types on purpose. AccessibilityEvent cannot be
 * constructed in a JVM test, which is exactly why this logic shipped untested.
 */
object SelectionExtractor {

    /** Longest rangeless candidate still plausible as a hand-made selection. */
    const val MAX_RANGELESS_CHARS = 80
    const val MAX_RANGELESS_WORDS = 8

    fun extract(
        eventText: String?,
        sourceText: String?,
        fromIndex: Int,
        toIndex: Int,
        nodeSelStart: Int = -1,
        nodeSelEnd: Int = -1,
    ): String? {
        val event = eventText?.takeIf { it.isNotBlank() }

        // An explicit range - even a degenerate one - is authoritative and
        // final: present-but-equal indices mean "no selection", not "unknown".
        //
        // The range indexes whichever string actually carries the text, and
        // that differs by app: WebViews (so every browser) populate
        // event.text, while plenty of native views leave it empty and only
        // fill the source node's text. Trying event first and falling back to
        // the node is what makes this work outside a browser - reading only
        // event.text meant selections in most non-browser apps produced
        // nothing at all.
        if (fromIndex >= 0 && toIndex >= 0) {
            return substring(event, fromIndex, toIndex)
                ?: substring(sourceText, fromIndex, toIndex)
        }

        // No event range. The node may still know its own selection.
        substring(sourceText, nodeSelStart, nodeSelEnd)?.let { return it }

        // Nothing authoritative. Accept only something short enough to
        // plausibly be a selection rather than the whole paragraph.
        val candidate = event?.takeIf { it != sourceText } ?: return null
        return candidate.takeIf { looksLikeSelection(it) }
    }

    private fun substring(text: String?, from: Int, to: Int): String? {
        if (text == null || from < 0 || to < 0) return null
        val start = minOf(from, to).coerceIn(0, text.length)
        val end = maxOf(from, to).coerceIn(0, text.length)
        if (start == end) return null
        return text.substring(start, end).takeIf { it.isNotBlank() }
    }

    private fun looksLikeSelection(text: String): Boolean =
        text.length <= MAX_RANGELESS_CHARS &&
            text.trim().split(Regex("\\s+")).size <= MAX_RANGELESS_WORDS
}
