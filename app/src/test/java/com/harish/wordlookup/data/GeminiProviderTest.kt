package com.harish.wordlookup.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** JSON handling and the retry loop. A live network round trip is covered on-device. */
class GeminiProviderTest {

    private val provider = GeminiProvider(apiKey = "test", model = "gemini-flash-latest", language = "Kannada")

    @Test
    fun `parses a well-formed reply`() {
        val obj = provider.parseModelJson(
            """{"part_of_speech":"noun","meaning":"a period of 100 years","synonyms":["age","era"],"example_en":"a century ago","translation":"ಶತಮಾನ","example_native":"ಒಂದು ಶತಮಾನದ ಹಿಂದೆ","synonyms_native":["ಯುಗ","ಕಾಲ"]}""",
        )
        assertEquals("noun", provider.stringField(obj, "part_of_speech"))
        assertEquals("age, era", provider.synonymsField(obj))
        assertEquals("ಯುಗ, ಕಾಲ", provider.synonymsField(obj, key = "synonyms_native"))
    }

    @Test
    fun `strips a markdown code fence around the JSON`() {
        val obj = provider.parseModelJson("```json\n{\"meaning\":\"x\"}\n```")
        assertEquals("x", provider.stringField(obj, "meaning"))
    }

    @Test
    fun `malformed reply throws MalformedReplyException with the recorded message`() {
        val ex = assertThrows(MalformedReplyException::class.java) {
            provider.parseModelJson("not json at all {")
        }
        assertEquals("Could not read model reply — try again.", ex.message)
    }

    @Test
    fun `synonyms as a bare string, not an array, still works`() {
        val obj = Json.parseToJsonElement("""{"synonyms":"lone synonym"}""").jsonObject
        assertEquals("lone synonym", provider.synonymsField(obj))
    }

    @Test
    fun `empty synonym entries are dropped`() {
        val obj = Json.parseToJsonElement("""{"synonyms":["a","","  ","b"]}""").jsonObject
        assertEquals("a, b", provider.synonymsField(obj))
    }

    @Test
    fun `a synonym that just echoes the meaning is dropped`() {
        val obj = Json.parseToJsonElement("""{"synonyms":["self-taught","independent","untutored"]}""").jsonObject
        assertEquals("independent, untutored", provider.synonymsField(obj, meaning = "self-taught"))
    }

    @Test
    fun `a synonym that echoes the headword is dropped`() {
        val obj = Json.parseToJsonElement("""{"synonyms":["quantum","discrete"]}""").jsonObject
        assertEquals("discrete", provider.synonymsField(obj, headword = "Quantum"))
    }

    @Test
    fun `duplicate synonyms collapse case-insensitively, first occurrence kept`() {
        val obj = Json.parseToJsonElement("""{"synonyms":["Quick","quick","QUICK","fast"]}""").jsonObject
        assertEquals("Quick, fast", provider.synonymsField(obj))
    }

    @Test
    fun `synonyms_native is parsed onto the result independently of the English synonyms`() {
        val (client, _) = fakeClient(
            envelope("""{"translation":"ಶತಮಾನ","synonyms":["age","era"],"synonyms_native":["ಯುಗ","ಕಾಲ"]}"""),
        )
        val result = runBlocking {
            GeminiProvider("key", "model", "Kannada", client).lookup("century")
        }
        assertEquals("age, era", result.synonyms)
        assertEquals("ಯುಗ, ಕಾಲ", result.synonymsNative)
    }

    @Test
    fun `synonyms_native is not backfilled after a script retry - it's a target-language field`() {
        // Mirrors the English-field backfill test above, but for the one
        // field that must NOT be backfilled: synonymsNative is target-language,
        // so only the corrected (script-passing) attempt's own value counts,
        // same treatment as translation/exampleNative.
        val (client, _) = fakeClient(
            envelope("""{"translation":"दमन","synonyms_native":["ಸಹಜ"]}"""), // wrong script, has a (meaningless) native field
            envelope("""{"translation":"ನಿಗ್ರಹ"}"""), // correct script, no synonyms_native at all
        )
        val result = runBlocking {
            GeminiProvider("key", "model", "Kannada", client).lookup("suppression")
        }
        assertEquals("ನಿಗ್ರಹ", result.translation)
        assertEquals("", result.synonymsNative)
    }

    // --- retry-on-malformed-reply, over a fully faked network -------------------

    private fun envelope(innerText: String): String =
        """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(innerText)}}]}}]}"""

    private fun fakeClient(vararg bodies: String): Pair<OkHttpClient, () -> Int> {
        var calls = 0
        val interceptor = Interceptor { chain ->
            val body = bodies[minOf(calls, bodies.lastIndex)]
            calls++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build() to { calls }
    }

    @Test
    fun `a malformed first reply is retried once and the retry succeeds`() {
        val (client, callCount) = fakeClient(
            envelope("this is not valid json"),
            envelope("""{"translation":"ಶತಮಾನ","meaning":"100 years"}"""),
        )
        val result = runBlocking {
            GeminiProvider("key", "model", "Kannada", client).lookup("century")
        }
        assertEquals("ಶತಮಾನ", result.translation)
        assertEquals(2, callCount())
    }

    @Test
    fun `two malformed replies in a row give up after the single retry`() {
        val (client, callCount) = fakeClient(envelope("junk"), envelope("still junk"))
        assertThrows(MalformedReplyException::class.java) {
            runBlocking { GeminiProvider("key", "model", "Kannada", client).lookup("century") }
        }
        assertEquals(2, callCount())
    }

    // --- wrong-script retry / escalation ladder ---------------------------------

    @Test
    fun `wrong script on the first reply is corrected by the same-model retry`() {
        val (client, callCount) = fakeClient(
            // "suppression" answered in Devanagari instead of Kannada
            envelope("""{"translation":"दमन","meaning":"holding back","example_en":"He felt suppression."}"""),
            envelope("""{"translation":"ನಿಗ್ರಹ"}"""), // retry: correct script, but English fields blank
        )
        val result = runBlocking {
            GeminiProvider("key", "model", "Kannada", client).lookup("suppression")
        }
        assertEquals("ನಿಗ್ರಹ", result.translation)
        assertEquals(2, callCount())
        // backfilled from the first, rejected attempt
        assertEquals("holding back", result.meaning)
        assertEquals("He felt suppression.", result.exampleEn)
    }

    @Test
    fun `wrong script twice escalates once to the fallback model and succeeds`() {
        val (client, callCount) = fakeClient(
            envelope("""{"translation":"दमन","meaning":"holding back"}"""),
            envelope("""{"translation":"θಳಿಗೆದು"}"""),   // same-model retry: still wrong (mixed script)
            envelope("""{"translation":"ನಿಗ್ರಹ"}"""),      // fallback model: correct
        )
        val result = runBlocking {
            GeminiProvider("key", "model", "Kannada", client, fallbackModel = "stronger-model").lookup("suppression")
        }
        assertEquals("ನಿಗ್ರಹ", result.translation)
        assertEquals(3, callCount())
        assertEquals("holding back", result.meaning) // backfilled from the very first attempt
    }

    @Test
    fun `wrong script through every attempt fails loudly instead of caching garbage`() {
        val (client, callCount) = fakeClient(
            envelope("""{"translation":"दमन"}"""),
            envelope("""{"translation":"दमन"}"""),
            envelope("""{"translation":"दमन"}"""),
        )
        val ex = assertThrows(LookupFailedException::class.java) {
            runBlocking {
                GeminiProvider("key", "model", "Kannada", client, fallbackModel = "stronger-model").lookup("suppression")
            }
        }
        assertEquals(3, callCount())
        assertEquals(true, ex.message?.contains("wrong script for Kannada") ?: false)
    }

    @Test
    fun `no fallback model configured skips escalation and fails after one retry`() {
        val (client, callCount) = fakeClient(
            envelope("""{"translation":"दमन"}"""),
            envelope("""{"translation":"दमन"}"""),
        )
        assertThrows(LookupFailedException::class.java) {
            runBlocking {
                GeminiProvider("key", "model", "Kannada", client, fallbackModel = "model").lookup("suppression")
            }
        }
        assertEquals(2, callCount()) // never made a third call - fallback == model, so it's skipped
    }

    @Test
    fun `a Latin-target language never triggers the script ladder at all`() {
        val (client, callCount) = fakeClient(
            envelope("""{"translation":"anything at all, script check does not apply"}"""),
        )
        val result = runBlocking {
            GeminiProvider("key", "model", "Swedish", client).lookup("test")
        }
        assertEquals(1, callCount())
        assertEquals("anything at all, script check does not apply", result.translation)
    }

    // --- overall ladder timeout --------------------------------------------

    /** A client whose calls never return within the test's lifetime - simulates a hung network, not a fast HTTP error. */
    private fun hangingClient(hangMillis: Long): OkHttpClient {
        val interceptor = Interceptor { chain ->
            Thread.sleep(hangMillis) // blocks OkHttp's dispatcher thread, not the calling coroutine
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(envelope("""{"translation":"ಆಕಾಶ"}""").toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun `a genuinely hung network fails on the ladder budget, not three stacked per-call timeouts`() {
        // The on-device bug: three sequential 10s-timeout calls could chain
        // into a ~30s silent spinner. A 200ms ladder budget against a network
        // that never responds inside 5s must fail fast, well under 5s.
        val client = hangingClient(hangMillis = 5_000)
        val provider = GeminiProvider("key", "model", "Kannada", client, ladderTimeoutMs = 200)

        val started = System.currentTimeMillis()
        val ex = assertThrows(LookupFailedException::class.java) {
            runBlocking { provider.lookup("suppression") }
        }
        val elapsed = System.currentTimeMillis() - started

        assertEquals("Lookup is taking too long — try again.", ex.message)
        assertEquals(true, elapsed < 3_000) // nowhere near the 5s the network was told to hang for
    }

    @Test
    fun `a lookup that finishes comfortably inside the ladder budget is unaffected by it`() {
        val (client, _) = fakeClient(envelope("""{"translation":"ಆಕಾಶ"}"""))
        val result = runBlocking {
            GeminiProvider("key", "model", "Kannada", client, ladderTimeoutMs = 15_000).lookup("sky")
        }
        assertEquals("ಆಕಾಶ", result.translation)
    }
}
