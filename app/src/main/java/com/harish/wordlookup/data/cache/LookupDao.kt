package com.harish.wordlookup.data.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LookupDao {

    @Query("SELECT * FROM lookups WHERE language = :language AND key = :key LIMIT 1")
    suspend fun get(language: String, key: String): LookupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LookupEntity)

    @Query("SELECT * FROM lookups ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LookupEntity>>

    @Query("DELETE FROM lookups WHERE language = :language AND key = :key")
    suspend fun delete(language: String, key: String)

    /** Backs the register's "Quiz - N due" dock. Reactive so the count updates the moment a card is graded. */
    @Query("SELECT COUNT(*) FROM lookups WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    /**
     * The 20-card cap lives here, not in the caller: the migration makes
     * every pre-round-8 word due at once (see MIGRATION_4_5), and without a
     * cap the first quiz session after upgrade is an unusable wall of
     * cards. ORDER BY dueAt ASC means the longest-overdue words come first.
     */
    @Query("SELECT * FROM lookups WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun dueBatch(now: Long, limit: Int): List<LookupEntity>

    @Query(
        "UPDATE lookups SET dueAt = :dueAt, intervalDays = :intervalDays, ease = :ease, " +
            "reps = :reps, lapses = :lapses, lastReviewedAt = :lastReviewedAt " +
            "WHERE language = :language AND key = :key",
    )
    suspend fun updateSchedule(
        language: String,
        key: String,
        dueAt: Long,
        intervalDays: Int,
        ease: Double,
        reps: Int,
        lapses: Int,
        lastReviewedAt: Long,
    )

    /** Backs the review screen's "Done" state - when the next word beyond today's capped batch will actually be due. */
    @Query("SELECT MIN(dueAt) FROM lookups WHERE dueAt > :now")
    suspend fun nextDueAfter(now: Long): Long?
}
