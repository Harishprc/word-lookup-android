package com.harish.wordlookup.data.review

import com.harish.wordlookup.data.LookupResult

/**
 * One row from the review queue - a saved lookup plus its current SM-2-lite
 * state. Mirrors [com.harish.wordlookup.data.RegisterEntry]'s shape
 * deliberately (a data.* type, not a cache.* one, crossing the same
 * boundary the register already does) rather than exposing LookupEntity to
 * the UI layer.
 */
data class ReviewCard(
    val result: LookupResult,
    val language: String,
    val schedule: ReviewScheduler.State,
)
