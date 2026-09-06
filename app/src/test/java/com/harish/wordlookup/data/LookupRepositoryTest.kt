package com.harish.wordlookup.data

import com.harish.wordlookup.data.cache.LookupDao
import com.harish.wordlookup.data.cache.LookupEntity
import com.harish.wordlookup.data.review.ReviewGrade
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

        override fun observeDueCount(now: Long): Flow<Int> =
            flowOf(rows.values.count { it.dueAt <= now })

        override suspend fun dueBatch(now: Long, limit: Int): List<LookupEntity> =
            rows.values.filter { it.dueAt <= now }.sortedBy { it.dueAt }.take(limit)

        override suspend fun updateSchedule(
            language: String,
            key: String,
            dueAt: Long,
            intervalDays: Int,
            ease: Double,
            reps: Int,
            lapses: Int,
            lastReviewedAt: Long,
        ) {
            val existing = rows[language to key] ?: return
            rows[language to key] = existing.copy(
                dueAt = dueAt,
                intervalDays = intervalDays,
                ease = ease,
                reps = reps,
                lapses = lapses,
                lastReviewedAt = lastReviewedAt,
            )
        }

        override suspend fun nextDueAfter(now: Long): Long? =
            rows.values.map { it.dueAt }.filter { it > now }.minOrNull()
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

    @Test
    fun `loadDueBatch only returns rows already due, oldest first, capped at the limit`() = runTest {
        val dao = FakeDao()
        dao.rows["Kannada" to "a"] = LookupEntity("Kannada", "a", "a", "x", createdAt = 1, dueAt = 300)
        dao.rows["Kannada" to "b"] = LookupEntity("Kannada", "b", "b", "x", createdAt = 1, dueAt = 100)
        dao.rows["Kannada" to "c"] = LookupEntity("Kannada", "c", "c", "x", createdAt = 1, dueAt = 200)
        dao.rows["Kannada" to "d"] = LookupEntity("Kannada", "d", "d", "x", createdAt = 1, dueAt = 999) // not due yet
        val repo = LookupRepository(dao, providerFactory = { TranslationProvider { text -> result(text) } }, timeSource = { 500L })

        val batch = repo.loadDueBatch(limit = 2)

        assertEquals(listOf("b", "c"), batch.map { it.result.original }) // oldest-due first, capped at 2
    }

    @Test
    fun `grading a card persists the scheduler's outcome back to the row`() = runTest {
        val dao = FakeDao()
        dao.rows["Kannada" to "sky"] = LookupEntity(
            "Kannada", "sky", "sky", "x", createdAt = 1, dueAt = 100, intervalDays = 3, ease = 2.5, reps = 2,
        )
        val repo = LookupRepository(dao, providerFactory = { TranslationProvider { text -> result(text) } }, timeSource = { 1_000_000L })
        val card = repo.loadDueBatch().first()

        repo.grade(card, ReviewGrade.GOOD)

        val updated = dao.rows["Kannada" to "sky"]!!
        // reps=2 -> round(3 * 2.5) = 8, per ReviewSchedulerTest's own coverage of the same transition.
        assertEquals(8, updated.intervalDays)
        assertEquals(3, updated.reps)
        assertEquals(1_000_000L + 8 * 24 * 60 * 60 * 1000L, updated.dueAt)
    }

    @Test
    fun `observeDueCount reflects only rows due at the given time`() = runTest {
        val dao = FakeDao()
        dao.rows["Kannada" to "a"] = LookupEntity("Kannada", "a", "a", "x", createdAt = 1, dueAt = 50)
        dao.rows["Kannada" to "b"] = LookupEntity("Kannada", "b", "b", "x", createdAt = 1, dueAt = 5_000)
        val repo = LookupRepository(dao, providerFactory = { TranslationProvider { text -> result(text) } }, timeSource = { 100L })

        assertEquals(1, repo.observeDueCount().first())
    }

    @Test
    fun `nextDueAt is null when nothing is scheduled beyond now`() = runTest {
        val dao = FakeDao()
        val repo = LookupRepository(dao, providerFactory = { TranslationProvider { text -> result(text) } }, timeSource = { 100L })

        assertNull(repo.nextDueAt())
    }

    @Test
    fun `nextDueAt finds the earliest row due strictly after now`() = runTest {
        val dao = FakeDao()
        dao.rows["Kannada" to "a"] = LookupEntity("Kannada", "a", "a", "x", createdAt = 1, dueAt = 50) // already due, excluded
        dao.rows["Kannada" to "b"] = LookupEntity("Kannada", "b", "b", "x", createdAt = 1, dueAt = 9_000)
        dao.rows["Kannada" to "c"] = LookupEntity("Kannada", "c", "c", "x", createdAt = 1, dueAt = 5_000)
        val repo = LookupRepository(dao, providerFactory = { TranslationProvider { text -> result(text) } }, timeSource = { 100L })

        assertEquals(5_000L, repo.nextDueAt())
        assertTrue(repo.nextDueAt()!! < 9_000L)
    }
}
