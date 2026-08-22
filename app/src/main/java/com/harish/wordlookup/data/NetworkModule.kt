package com.harish.wordlookup.data

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool

/**
 * [GeminiProvider]'s connection pool, kept as its own object rather than an
 * inline field so a warm-up call and a real lookup call are guaranteed to
 * share the same pool regardless of which `OkHttpClient` instance issues
 * either one.
 */
object NetworkModule {
    val connectionPool: ConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)
}
