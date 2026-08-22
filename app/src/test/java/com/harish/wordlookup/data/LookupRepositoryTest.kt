package com.harish.wordlookup.data

import com.harish.wordlookup.data.cache.LookupDao
import com.harish.wordlookup.data.cache.LookupEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LookupRepositoryTest {

    /** In-memory fake - no Room, no instrumentation test needed. */
    private class FakeDao : LookupDao {
        val rows = mutableMapOf<Pair<String, String>, LookupEntity>()
        var getCalls = 0

        override suspend fun get(language: String, key: String): LookupEntity? {
            getCalls++
            return rows[language to key]
        }

        override suspend fun upsert(entity: LookupEntity) {
            rows[entity.language to entity.key] = entity
        }

        override fun observeAll(): Flow<List<LookupEntity>> = flowOf(rows.values.toList())

        override suspend fun delete(language: String, key: String) {
            rows.remove(language to key)
        }
    }

    private fun result(word: String) = LookupResult(original = word, translation = "x-$word")

    @Test
    fun `a fresh lookup calls the provider once and persists to Room`() = runTest {
        val dao = FakeDao()
        val calls = AtomicInteger(0)
        val repo = LookupRepository(dao, providerFactory = {
            TranslationProvider { text -> calls.incrementAndGet(); result(text) }
        })

        val r = repo.lookup("sky", "Kannada")

        assertEquals("x-sky", r.translation)
        assertEquals(1, calls.get())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `a second identical lookup is served from memory without touching Room or the provider again`() = runTest {
        val dao = FakeDao()
        val calls = AtomicInteger(0)
        val repo = LookupRepository(dao, providerFactory = {
            TranslationProvider { text -> calls.incrementAndGet(); result(text) }
        })

        repo.lookup("sky", "Kannada")
        val daoCallsAfterFirst = dao.getCalls
        val second = repo.lookup("sky", "Kannada")

        assertEquals("x-sky", second.translation)
        assertEquals(1, calls.get()) // provider never called a second time
        assertEquals(daoCallsAfterFirst, dao.getCalls) // memory cache short-circuited before Room too
    }

    @Test
    fun `a row already in Room is served without a provider call`() = runTest {
        val dao = FakeDao()
        dao.rows["Kannada" to "sky"] = LookupEntity(
            language = "Kannada", key = "sky", original = "sky", translation = "ಆಕಾಶ",
            createdAt = 1,
        )
        val calls = AtomicInteger(0)
        val repo = LookupRepository(dao, providerFactory = {
            TranslationProvider { text -> calls.incrementAndGet(); result(text) }
        })

        val r = repo.lookup("sky", "Kannada")

        assertEquals("ಆಕಾಶ", r.translation)
        assertEquals(0, calls.get())
    }

    @Test
    fun `two concurrent identical lookups share one in-flight provider call`() = runTest {
        val dao = FakeDao()
        val calls = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()
        val repo = LookupRepository(dao, providerFactory = {
            TranslationProvider { text ->
                calls.incrementAndGet()
                gate.await() // held open until the test releases it
                result(text)
            }
        })

        val first = async { repo.lookup("sky", "Kannada") }
        val second = async { repo.lookup("sky", "Kannada") }

        // Give both coroutines a chance to reach the gate before releasing it.
        kotlinx.coroutines.yield()
        gate.complete(Unit)

        assertEquals("x-sky", first.await().translation)
        assertEquals("x-sky", second.await().translation)
        assertEquals(1, calls.get())
    }

    @Test
    fun `deleting a word evicts it from the memory cache too`() = runTest {
        val dao = FakeDao()
        val calls = AtomicInteger(0)
        val repo = LookupRepository(dao, providerFactory = {
            TranslationProvider { text -> calls.incrementAndGet(); result(text) }
        })

        repo.lookup("sky", "Kannada")
        repo.delete("Kannada", "sky")
        repo.lookup("sky", "Kannada") // must hit the provider again, not the stale memory entry

        assertEquals(2, calls.get())
    }

    @Test
    fun `results for different keys never collide in memory`() = runTest {
        val dao = FakeDao()
        val repo = LookupRepository(dao, providerFactory = {
            TranslationProvider { text -> result(text) }
        })

        val sky = repo.lookup("sky", "Kannada")
        val moon = repo.lookup("moon", "Kannada")
        val skyAgain = repo.lookup("sky", "Kannada")

        assertEquals("x-sky", sky.translation)
        assertEquals("x-moon", moon.translation)
        assertSame(sky.translation, skyAgain.translation)
    }
}
