package com.harish.wordlookup.data

/** One row in the word register - a cached lookup plus its metadata for display. */
data class RegisterEntry(
    val result: LookupResult,
    val language: String,
    val createdAtMillis: Long,
)
