package com.harish.wordlookup.data

/**
 * The modularity seam for "how does a word get translated" - [GeminiProvider]
 * is the only implementation today, but the boundary is what lets
 * [LookupRepository] be tested with a fake instead of a fake HTTP layer, and
 * is where a future paid provider (Claude/GPT, matching the desktop app's own
 * `TranslationProvider` ABC in kannada_lookup/translator.py) would plug in
 * without touching any call site.
 */
fun interface TranslationProvider {
    suspend fun lookup(text: String): LookupResult
}
