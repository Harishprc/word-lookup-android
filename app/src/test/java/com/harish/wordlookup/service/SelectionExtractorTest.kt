package com.harish.wordlookup.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression coverage for the "select one word, whole paragraph gets looked up" bug. */
class SelectionExtractorTest {

    private val paragraph =
        "This is a long paragraph of reader text that goes on for a while and is " +
            "definitely not a plausible single word or short phrase selection at all."

    @Test
    fun `no indices and a long node text returns null, not the whole paragraph`() {
        val result = SelectionExtractor.extract(
            eventText = paragraph,
            sourceText = paragraph,
            fromIndex = -1,
            toIndex = -1,
        )
        assertNull(result)
    }

    @Test
    fun `no event indices but the node reports its own selection range`() {
        val result = SelectionExtractor.extract(
            eventText = paragraph,
            sourceText = paragraph,
            fromIndex = -1,
            toIndex = -1,
            nodeSelStart = 10,
            nodeSelEnd = 14,
        )
        assertEquals(paragraph.substring(10, 14), result)
    }

    @Test
    fun `no indices at all, but a short word-like candidate is accepted`() {
        val result = SelectionExtractor.extract(
            eventText = "autodidactic",
            sourceText = "some other node text",
            fromIndex = -1,
            toIndex = -1,
        )
        assertEquals("autodidactic", result)
    }

    @Test
    fun `explicit event range spanning a paragraph is honoured in full`() {
        val result = SelectionExtractor.extract(
            eventText = paragraph,
            sourceText = paragraph,
            fromIndex = 0,
            toIndex = paragraph.length,
        )
        assertEquals(paragraph, result)
    }

    @Test
    fun `a native view that leaves event text empty still resolves via the node text`() {
        // The non-browser case: WebViews populate event.text, many native views
        // don't and only fill the source node. Reading event.text alone meant
        // selections outside a browser produced nothing.
        val result = SelectionExtractor.extract(
            eventText = null,
            sourceText = "the quick brown fox",
            fromIndex = 4,
            toIndex = 9,
        )
        assertEquals("quick", result)
    }

    @Test
    fun `a blank event text is treated the same as a missing one`() {
        val result = SelectionExtractor.extract(
            eventText = "   ",
            sourceText = "the quick brown fox",
            fromIndex = 4,
            toIndex = 9,
        )
        assertEquals("quick", result)
    }

    @Test
    fun `event text still wins when both carry the range`() {
        val result = SelectionExtractor.extract(
            eventText = "abcdefghij",
            sourceText = "zzzzzzzzzz",
            fromIndex = 0,
            toIndex = 3,
        )
        assertEquals("abc", result)
    }

    @Test
    fun `reversed indices are normalized`() {
        val result = SelectionExtractor.extract(
            eventText = "hello world",
            sourceText = null,
            fromIndex = 5,
            toIndex = 0,
        )
        assertEquals("hello", result)
    }

    @Test
    fun `equal indices yield no selection`() {
        val result = SelectionExtractor.extract(
            eventText = "hello world",
            sourceText = null,
            fromIndex = 3,
            toIndex = 3,
        )
        assertNull(result)
    }

    @Test
    fun `out-of-bounds indices are clamped rather than crashing`() {
        val result = SelectionExtractor.extract(
            eventText = "hi",
            sourceText = null,
            fromIndex = 0,
            toIndex = 999,
        )
        assertEquals("hi", result)
    }

    @Test
    fun `no signals at all yields null`() {
        val result = SelectionExtractor.extract(
            eventText = null,
            sourceText = null,
            fromIndex = -1,
            toIndex = -1,
        )
        assertNull(result)
    }
}
