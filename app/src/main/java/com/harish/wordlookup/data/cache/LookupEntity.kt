package com.harish.wordlookup.data.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harish.wordlookup.data.LookupResult
import java.util.Locale

/**
 * The `createdAt` index backs LookupDao.observeAll's `ORDER BY createdAt DESC`,
 * the query the register screen runs on every keystroke of its search box.
 * The `dueAt` index (round 8) backs the review queue's `ORDER BY dueAt ASC`.
 *
 * No longer byte-for-byte matched against v0.1.0's DDL - see CLAUDE.md's
 * "Verifying a change" section. `deleted`/`updatedAt` existed only to support
 * sync tombstones (a word's local delete had to survive as a marker until it
 * propagated to the shared gist); with sync removed, a delete is a real
 * DELETE again and there is nothing left to reconcile `updatedAt` against.
 *
 * Round 8's six scheduling columns are appended last, matching round 7's own
 * rule for `synonymsNative`: [toResult] and the DAO's row constructors are
 * positional, so a field inserted in the middle would silently misassign
 * every column after it. They default so every pre-migration row (via
 * MIGRATION_4_5's UPDATE) and every fresh row from a lookup the user never
 * opens the quiz on both land in a consistent, immediately-schedulable
 * state - see [com.harish.wordlookup.data.review.ReviewScheduler] for what
 * consumes them.
 */
@Entity(tableName = "lookups", primaryKeys = ["language", "key"], indices = [Index("createdAt"), Index("dueAt")])
data class LookupEntity(
    val language: String,
    val key: String,
    val original: String,
    val translation: String,
    val partOfSpeech: String = "",
    val meaning: String = "",
    val synonyms: String = "",
    val exampleEn: String = "",
    val exampleNative: String = "",
    val provider: String = "",
    val createdAt: Long,
    val synonymsNative: String = "",
    /** SM-2-lite scheduling state (round 8) - see [com.harish.wordlookup.data.review.ReviewScheduler]. */
    val dueAt: Long = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long = 0,
) {
    fun toResult() = LookupResult(
        original, translation, partOfSpeech, meaning, synonyms, exampleEn, exampleNative, synonymsNative,
    )

    companion object {
        /** Cache key: trimmed, whitespace-collapsed, lowercased. */
        fun normalize(text: String): String =
            text.trim().split(Regex("\\s+")).joinToString(" ").lowercase(Locale.ROOT)
    }
}
