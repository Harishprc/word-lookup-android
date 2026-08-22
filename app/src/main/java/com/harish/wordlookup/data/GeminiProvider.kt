package com.harish.wordlookup.data

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Fast path: one request, greedy decoding, a bounded output, and a cache-first
 * repository above this class means most calls never happen at all. Accuracy
 * path: [ScriptValidator] catches a wrong-alphabet answer before it reaches
 * the cache (which has no expiry - one bad entry would be served forever),
 * with the same retry-then-escalate ladder the companion desktop app uses
 * (kannada_lookup/translator.py) - request, retry same model with the mistake
 * spelled out, escalate once to [fallbackModel], then fail loudly rather than
 * cache something unreadable.
 *
 * `temperature = 0` (see [DEFAULT_GENERATION_CONFIG]) makes a repeated prompt
 * return the identical reply, so every retry here appends a different
 * instruction rather than resending the same prompt - otherwise the retry
 * would be pointless.
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
    private val language: String,
    private val client: OkHttpClient = defaultClient,
    private val endpointTemplate: String = DEFAULT_ENDPOINT_TEMPLATE,
    private val fallbackModel: String = DEFAULT_FALLBACK_MODEL,
    private val ladderTimeoutMs: Long = LADDER_TIMEOUT_MS,
) : TranslationProvider {
    override suspend fun lookup(text: String): LookupResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw LookupFailedException("No API key. Get a free one at aistudio.google.com and set it in Settings.")
        }
        // The ladder below is up to three sequential network calls (fast
        // model, fast-model retry, fallback model), each with its own 10s
        // per-call timeout - a genuinely slow network could chain those into
        // a ~30s silent spinner before ever surfacing an error, which is what
        // "Conciseness" did on-device (12s+ of spinning, then a confusing
        // compound error). One wall-clock budget over the whole ladder means
        // a real failure surfaces in a bounded, human-scale time instead of
        // stacking every retry's own patience on top of the others'.
        // withTimeoutOrNull cancels whichever executeOnce is in flight, and
        // because that call is suspend-cancellable (Call.await(), not the
        // blocking execute()), the underlying socket genuinely aborts rather
        // than being abandoned to finish on its own.
        return@withContext withTimeoutOrNull(ladderTimeoutMs) { runLadder(text) }
            ?: throw LookupFailedException("Lookup is taking too long — try again.")
    }

    private suspend fun runLadder(text: String): LookupResult {
        val first = try {
            executeOnce(text)
        } catch (e: MalformedReplyException) {
            // A repeat of the exact same prompt would just repeat the exact
            // same malformed reply at temperature 0 - ask for JSON only, explicitly.
            executeOnce(text, extra = MALFORMED_REPLY_RETRY_SUFFIX)
        }

        if (!ScriptValidator.isCheckable(language) || ScriptValidator.usesExpectedScript(first.translation, language)) {
            return first
        }

        // Wrong script: retry the same model once, naming the mistake -
        // that works better than repeating the original instruction, which
        // the model already ignored once.
        val corrected = runCatching { executeOnce(text, extra = scriptCorrectionSuffix(language)) }.getOrNull()
        if (corrected != null && ScriptValidator.usesExpectedScript(corrected.translation, language)) {
            return backfillEnglishFields(corrected, first)
        }

        // Still wrong (or unparseable): escalate once to a stronger model.
        // The escalation is what turns a hard word into a solved one instead
        // of a dead end - paid for on the rare word that actually needs it,
        // not on every lookup.
        if (fallbackModel.isNotBlank() && fallbackModel != model) {
            val escalated = try {
                executeOnce(text, extra = scriptCorrectionSuffix(language), modelOverride = fallbackModel)
            } catch (e: LookupFailedException) {
                // The fallback is a best-effort backstop; its own failure must
                // not replace the real problem with a confusing unrelated one.
                throw LookupFailedException(
                    "Model answered in the wrong script for $language twice, and the fallback model failed too: ${e.message}",
                )
            }
            if (ScriptValidator.usesExpectedScript(escalated.translation, language)) {
                return backfillEnglishFields(escalated, first)
            }
        }

        // Raising keeps the bad answer out of the cache, which is keyed by
        // word only - a wrong entry stored here would be served forever.
        throw LookupFailedException(
            "Model answered in the wrong script for $language. Try again, or switch GEMINI_MODEL in Settings.",
        )
    }

    /**
     * Fields that describe the ENGLISH side of the card are safe to carry
     * over from a script-rejected attempt; the target-language fields are
     * not - [primary] already passed the script check for those. Only fills
     * blanks: asking the model to "reply again in the target script" can make
     * it treat the translation as the whole task and skip the English fields.
     */
    private fun backfillEnglishFields(primary: LookupResult, earlier: LookupResult): LookupResult = primary.copy(
        partOfSpeech = primary.partOfSpeech.ifBlank { earlier.partOfSpeech },
        meaning = primary.meaning.ifBlank { earlier.meaning },
        synonyms = primary.synonyms.ifBlank { earlier.synonyms },
        exampleEn = primary.exampleEn.ifBlank { earlier.exampleEn },
    )

    private suspend fun executeOnce(text: String, extra: String = "", modelOverride: String = model): LookupResult {
        val prompt = prompt(language, text) + extra
        val requestJson = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", GENERATION_TEMPERATURE)
                put("topP", GENERATION_TOP_P)
                put("maxOutputTokens", GENERATION_MAX_OUTPUT_TOKENS)
            }
        }.toString()

        val request = Request.Builder()
            .url(String.format(endpointTemplate, modelOverride))
            .addHeader("x-goog-api-key", apiKey)
            .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).await().use { response ->
                when (response.code) {
                    400, 401, 403 -> throw LookupFailedException("API key rejected (${response.code}). Check the key in Settings.")
                    404 -> throw LookupFailedException("Model '$modelOverride' not found — check GEMINI_MODEL in Settings (e.g. gemini-flash-lite-latest).")
                    429 -> throw LookupFailedException("Free-tier quota hit (~1,500/day). Wait a minute or try tomorrow.")
                }
                if (!response.isSuccessful) {
                    throw LookupFailedException("Gemini API error ${response.code}.")
                }
                try {
                    val body = response.body?.string()
                        ?: throw LookupFailedException("Empty response from the API.")
                    val replyText = Json.parseToJsonElement(body).jsonObject["candidates"]!!
                        .jsonArray[0].jsonObject["content"]!!
                        .jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!
                        .jsonPrimitive.content
                    val parsed = parseModelJson(replyText)

                    val translation = (parsed["translation"] as? JsonPrimitive)?.content?.trim().orEmpty()
                    if (translation.isEmpty()) {
                        throw MalformedReplyException("No translation returned — try again.")
                    }

                    val meaning = stringField(parsed, "meaning")
                    LookupResult(
                        original = text,
                        translation = translation,
                        partOfSpeech = stringField(parsed, "part_of_speech").lowercase(Locale.ROOT),
                        meaning = meaning,
                        synonyms = synonymsField(parsed, meaning, text),
                        exampleEn = stringField(parsed, "example_en"),
                        exampleNative = stringField(parsed, "example_native"),
                        // meaning/headword are English, meaningless as de-dup
                        // exclusions against target-language text - omitted.
                        synonymsNative = synonymsField(parsed, key = "synonyms_native"),
                    )
                } catch (e: LookupFailedException) {
                    throw e
                } catch (e: Exception) {
                    throw LookupFailedException("Unexpected API response format.")
                }
            }
        } catch (e: SocketTimeoutException) {
            throw LookupFailedException("Lookup timed out — check your connection.")
        } catch (e: IOException) {
            throw LookupFailedException("No internet connection.")
        }
    }

    fun parseModelJson(raw: String): JsonObject {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter("\n", text).substringBeforeLast("```")
        }
        return try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw MalformedReplyException("Could not read model reply — try again.")
        }
    }

    fun stringField(obj: JsonObject, key: String): String =
        (obj[key] as? JsonPrimitive)?.content?.trim().orEmpty()

    /**
     * [meaning] and [headword] are excluded (case-insensitively) so the model
     * echoing the meaning back as a "synonym" doesn't show twice - the
     * recorded bug: "autodidactic" -> meaning "self-taught", synonyms
     * "self-taught, independent, untutored".
     */
    fun synonymsField(obj: JsonObject, meaning: String = "", headword: String = "", key: String = "synonyms"): String {
        val exclude = setOf(meaning.trim().lowercase(Locale.ROOT), headword.trim().lowercase(Locale.ROOT))
        val element = obj[key] ?: return ""
        val raw = if (element is JsonArray) {
            element.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
        } else {
            listOfNotNull((element as? JsonPrimitive)?.content?.trim())
        }

        val seen = LinkedHashSet<String>()
        val kept = mutableListOf<String>()
        for (s in raw) {
            if (s.isEmpty()) continue
            val key = s.lowercase(Locale.ROOT)
            if (key in exclude) continue
            if (!seen.add(key)) continue
            kept += s
        }
        return kept.joinToString(", ")
    }

    companion object {
        const val DEFAULT_ENDPOINT_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

        /** Measured 1.4-3.1s/lookup against this API with this prompt (kannada_lookup/translator.py), vs 4.4-10.6s+ for plain "flash". */
        const val DEFAULT_MODEL = "gemini-flash-lite-latest"

        /** flash-lite invents wrong-script answers more often on its own; this is what the script-retry ladder escalates to. */
        const val DEFAULT_FALLBACK_MODEL = "gemini-flash-latest"

        /**
         * Caps the *whole* retry/escalate ladder, not any single call. Chosen
         * to comfortably fit the legitimate worst case (fast model + fast
         * model retry + fallback model, each a few seconds) while still
         * failing in bounded, human-scale time if the network is genuinely
         * struggling - rather than three independent 10s per-call timeouts
         * stacking into a ~30s silent spinner, which is what happened
         * on-device for "Conciseness".
         */
        private const val LADDER_TIMEOUT_MS = 15_000L

        // Greedy decoding is both the fastest setting and what makes the
        // Room cache coherent (same input -> same cached answer). topP is inert
        // at temperature 0 but kept as an explicit backstop if that ever changes.
        private const val GENERATION_TEMPERATURE = 0.0
        private const val GENERATION_TOP_P = 0.9
        // Roomy enough for a non-Latin example sentence plus native-script
        // synonyms (more tokens per character than English) while still
        // bounding the generation tail. Raised from 200 when synonyms_native
        // was added - a non-Latin script's extra field pushed the reply
        // close enough to the old cap to risk truncating the JSON.
        private const val GENERATION_MAX_OUTPUT_TOKENS = 320

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectionPool(NetworkModule.connectionPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * Opens (and pools) a connection to the API host before the user has
         * finished selecting text, so the DNS+TLS handshake overlaps their own
         * reaction time instead of sitting on the lookup's critical path.
         * Fire-and-forget: any failure here is silently irrelevant, since the
         * real request will simply pay for its own connection if this didn't land.
         */
        fun warmUp(client: OkHttpClient = defaultClient, endpointTemplate: String = DEFAULT_ENDPOINT_TEMPLATE) {
            val request = Request.Builder().url(String.format(endpointTemplate, DEFAULT_MODEL)).head().build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) = Unit
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) = response.close()
            })
        }

        // The translation rules exist because weaker models invent plausible-looking
        // words in low-resource scripts - naming the failure modes explicitly is
        // what suppresses them (see kannada_lookup/translator.py for the same
        // reasoning). Field length caps are new here (not in the shipped v0.1.0
        // prompt) - fewer output tokens is the single biggest latency lever
        // after the model choice itself.
        fun prompt(language: String, text: String): String =
            "You are an English-$language dictionary. For the English word or phrase below, reply with ONLY this JSON:\n" +
                "{\"part_of_speech\": \"<noun/verb/adjective/adverb/…, or empty for multi-word phrases>\", " +
                "\"meaning\": \"<plain English meaning, at most 8 words>\", " +
                "\"synonyms\": [\"<2-3 synonyms common in conversational English>\"], " +
                "\"example_en\": \"<one short example sentence in English, at most 12 words>\", " +
                "\"translation\": \"<the $language translation>\", " +
                "\"example_native\": \"<one short example sentence in $language, at most 12 words>\", " +
                "\"synonyms_native\": [\"<2-3 synonyms for the translation, in $language script>\"]}\n" +
                "For multi-word phrases, synonyms and synonyms_native may be empty lists.\n\n" +
                "Rules for the $language text:\n" +
                "- Give exactly ONE translation: the single most commonly used word. Never offer alternatives, and never use a slash.\n" +
                "- It must be a real, standard $language word that a native speaker would recognise and a dictionary would list. If no true equivalent exists, use the ordinary $language phrase for the idea rather than inventing a word.\n" +
                "- Write it in correct, well-formed $language script. Never spell the English word out phonetically in that script.\n" +
                "- Use only valid letter combinations for $language. Do not produce malformed clusters.\n" +
                "- synonyms_native must be real $language words in $language script, not translations of the English synonyms list word-for-word.\n\n" +
                "English: $text"

        private const val MALFORMED_REPLY_RETRY_SUFFIX =
            "\n\nYour previous reply could not be parsed as JSON. Reply again with ONLY the JSON object described above - no markdown fences, no extra text before or after it."

        // Naming the mistake concretely works better than repeating the
        // original instruction, which the model has already ignored once.
        private fun scriptCorrectionSuffix(language: String): String =
            "\n\nYour previous answer was written in the wrong alphabet. The translation MUST be written in the " +
                "$language script itself, not in the script of any other language, and not in Latin letters. " +
                "Reply again with the COMPLETE JSON object - every field above, including example_native and " +
                "synonyms_native, filled in exactly as specified. Do not return only the translation."
    }
}

/**
 * `Call.execute()` blocks a thread and, once started, cannot be aborted by
 * cancelling the coroutine around it - the socket read runs to completion
 * regardless, burning quota on an answer nobody will see. `enqueue()` plus
 * [suspendCancellableCoroutine] makes the call genuinely cancellable: when the
 * wrapping coroutine is cancelled (a newer selection superseded this one),
 * `invokeOnCancellation` fires synchronously and aborts the real socket.
 */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation {
        runCatching { cancel() }
    }
}
