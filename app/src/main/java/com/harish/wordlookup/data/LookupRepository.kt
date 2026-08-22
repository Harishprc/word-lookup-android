package com.harish.wordlookup.data

import com.harish.wordlookup.data.cache.LookupDao
import com.harish.wordlookup.data.cache.LookupEntity
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Three layers, checked in order, each one skipping the cost of the next:
 * an in-memory LRU (instant, no disk I/O), Room (durable, survives process
 * death), then the network provider (single-flighted, see below) - which
 * also persists to both layers so the same word never pays the network cost
 * twice.
 */
class LookupRepository(
    private val dao: LookupDao,
    private val providerFactory: (language: String) -> TranslationProvider,
    private val timeSource: () -> Long = System::currentTimeMillis,
) {
    private val memoryCache = Collections.synchronizedMap(
        object : LinkedHashMap<CacheKey, LookupResult>(MEMORY_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, LookupResult>) =
                size > MEMORY_CACHE_SIZE
        },
    )

    // Two triggers close together on the same word (a drag re-selecting it,
    // or both trigger paths firing near-simultaneously) must not become two
    // API calls. In-flight fetches are shared by key; a second caller awaits
    // the first's result instead of starting its own.
    private val inFlight = mutableMapOf<CacheKey, Deferred<LookupResult>>()
    private val inFlightMutex = Mutex()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A plain, non-suspend map read - safe to call straight from a UI/event
     * callback with no coroutine overhead, before deciding whether a loading
     * indicator is worth showing at all. Only checks the in-memory layer,
     * not Room: this is for the "asked this exact word a moment ago" case
     * (a re-selection, or two trigger paths firing on the same word), where
     * even a Room read's Loading flash would be visible flicker for nothing.
     */
    fun peekMemory(text: String, language: String): LookupResult? =
        memoryCache[CacheKey(language, LookupEntity.normalize(text))]

    suspend fun lookup(text: String, language: String): LookupResult {
        val cacheKey = CacheKey(language, LookupEntity.normalize(text))

        memoryCache[cacheKey]?.let { return it }

        dao.get(language, cacheKey.key)?.let { entity ->
            val result = entity.toResult()
            memoryCache[cacheKey] = result
            return result
        }

        return singleFlight(cacheKey) { fetchAndPersist(text, cacheKey) }
    }

    private suspend fun fetchAndPersist(text: String, cacheKey: CacheKey): LookupResult {
        val result = providerFactory(cacheKey.language).lookup(text)
        dao.upsert(
            LookupEntity(
                language = cacheKey.language,
                key = cacheKey.key,
                original = result.original,
                translation = result.translation,
                partOfSpeech = result.partOfSpeech,
                meaning = result.meaning,
                synonyms = result.synonyms,
                exampleEn = result.exampleEn,
                exampleNative = result.exampleNative,
                synonymsNative = result.synonymsNative,
                provider = "GeminiProvider",
                createdAt = timeSource(),
            ),
        )
        memoryCache[cacheKey] = result
        return result
    }

    /**
     * Runs [block] at most once per [key] across concurrent callers. The
     * shared fetch runs on [repositoryScope] (its own SupervisorJob), not on
     * any individual caller's job - a caller cancelling its own lookup (e.g.
     * the debounced accessibility trigger cancelling a superseded request)
     * must not cancel a fetch that a *different*, still-live caller is also
     * waiting on.
     */
    private suspend fun singleFlight(key: CacheKey, block: suspend () -> LookupResult): LookupResult {
        val deferred = inFlightMutex.withLock {
            inFlight.getOrPut(key) { repositoryScope.async { block() } }
        }
        return try {
            deferred.await()
        } finally {
            inFlightMutex.withLock {
                if (inFlight[key] === deferred) inFlight.remove(key)
            }
        }
    }

    fun observeRegister(): Flow<List<RegisterEntry>> =
        dao.observeAll().map { entities ->
            entities.map { RegisterEntry(it.toResult(), it.language, it.createdAt) }
        }

    suspend fun delete(language: String, original: String) {
        val key = LookupEntity.normalize(original)
        dao.delete(language, key)
        memoryCache.remove(CacheKey(language, key))
    }

    private data class CacheKey(val language: String, val key: String)

    companion object {
        private const val MEMORY_CACHE_SIZE = 256
    }
}
