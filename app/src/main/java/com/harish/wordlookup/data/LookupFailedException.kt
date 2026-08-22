package com.harish.wordlookup.data

/** User-presentable lookup failure (bad key, quota, malformed reply, network). */
open class LookupFailedException(message: String) : Exception(message)

/**
 * The model's reply this time was unparseable JSON, or was missing a
 * translation. Unlike a bad key or exhausted quota, this is transient - the
 * same request usually succeeds on an immediate retry - so it is a distinct
 * type `GeminiProvider.lookup` can catch and retry once, while every existing
 * `catch (LookupFailedException)` elsewhere keeps working unchanged. Mirrors
 * the companion desktop's `MalformedReply` (kannada_lookup/translator.py).
 */
class MalformedReplyException(message: String) : LookupFailedException(message)
