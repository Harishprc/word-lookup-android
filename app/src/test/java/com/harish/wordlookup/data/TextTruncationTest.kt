package com.harish.wordlookup.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTruncationTest {
    @Test
    fun `short text is trimmed but not cut`() {
        assertEquals("hello", TextTruncation.truncate("  hello  "))
    }

    @Test
    fun `long text is cut at the last word boundary`() {
        val text = "word ".repeat(200).trim() // far past 500 chars, all single spaces
        val result = TextTruncation.truncate(text)
        assertEquals(true, result.length <= 500)
        assertEquals(false, result.endsWith(" "))
    }

    @Test
    fun `a single run with no spaces is hard-cut at max`() {
        val text = "a".repeat(600)
        assertEquals(500, TextTruncation.truncate(text).length)
    }
}
