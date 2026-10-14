package com.matik.reatube.net

import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One client for the API, the player and diagnostics. Its default `okhttp/4.x` User-Agent passes
 * Rutube's WAF (the default curl one gets 403).
 */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

/** Like runCatching, but lets coroutine cancellation through. */
suspend fun <T> catching(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.describe(): String {
    val cause = cause?.takeIf { it !== this }?.let { " ← ${it.javaClass.simpleName}: ${it.message}" }.orEmpty()
    return "${javaClass.simpleName}: $message$cause"
}
