package com.eduspecial.utils

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * OkHttp interceptor that retries only idempotent requests with exponential backoff.
 *
 * Retrying non-idempotent requests such as POST/PATCH can duplicate user actions
 * like sign-up or content creation, so those requests are returned immediately.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2
) : Interceptor {

    private val retryableMethods = setOf("GET", "HEAD", "OPTIONS")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val shouldRetry = request.method.uppercase() in retryableMethods
        var lastException: IOException? = null
        var response: Response? = null

        for (attempt in 0..maxRetries) {
            response?.close()

            try {
                response = chain.proceed(request)

                when {
                    response.isSuccessful -> return response
                    !shouldRetry -> return response
                    response.code == 429 -> {
                        val retryAfterSec = response.header("Retry-After")?.toLongOrNull() ?: (1L shl attempt)
                        Log.w("RetryInterceptor", "429 rate limited; waiting ${retryAfterSec}s (attempt $attempt)")
                        if (attempt < maxRetries) Thread.sleep(retryAfterSec * 1000)
                    }
                    response.code in listOf(502, 503, 504) -> {
                        val backoffMs = (1L shl attempt) * 1000
                        Log.w("RetryInterceptor", "Server error ${response.code}; backoff ${backoffMs}ms (attempt $attempt)")
                        if (attempt < maxRetries) Thread.sleep(backoffMs)
                    }
                    else -> return response
                }
            } catch (e: SocketTimeoutException) {
                if (!shouldRetry) throw e
                lastException = e
                val backoffMs = (1L shl attempt) * 1000
                Log.w("RetryInterceptor", "Timeout; backoff ${backoffMs}ms (attempt $attempt)")
                if (attempt < maxRetries) Thread.sleep(backoffMs)
            } catch (e: IOException) {
                if (!shouldRetry) throw e
                lastException = e
                val backoffMs = (1L shl attempt) * 1000
                Log.w("RetryInterceptor", "IO error; backoff ${backoffMs}ms (attempt $attempt)")
                if (attempt < maxRetries) Thread.sleep(backoffMs)
            }
        }

        return response ?: throw lastException ?: IOException("Request failed after $maxRetries retries")
    }
}
