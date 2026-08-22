package com.harish.wordlookup.data.cache

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.harish.wordlookup.data.LookupResult
import java.util.Locale

/**
 * The `createdAt` index backs LookupDao.observeAll's `ORDER BY createdAt DESC`,
 * the query the register screen runs on every keystroke of its search box.
 *
 * No longer byte-for-byte matched against v0.1.0's DDL - see CLAUDE.md's
 * "Verifying a change" section. `deleted`/`updatedAt` existed only to support
 * sync tombstones (a word's local delete had to survive as a marker until it
 * propagated to the shared gist); with sync removed, a delete is a real
 * DELETE again and there is nothing left to reconcile `updatedAt` against.
 */
@Entity(tableName = "lookups", primaryKeys = ["language", "key"], indices = [Index("createdAt")])
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
