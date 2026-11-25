package com.anilocal.app.data.remote

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The one shared [OkHttpClient]. The app's own APIs (AniList, AniSkip, MAL, extension repos) are
 * small classes over [JsonHttp], each injected directly — there is no REST framework.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        // Explicit budget per attempt. No callTimeout: the extension-APK download streams a large
        // body through this client, and per-read timeouts already catch stalls without capping a
        // legitimately slow transfer.
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // A standard HTTP cache for whatever GETs honor caching (AniList's POST bypasses it; the
        // app-level JsonCache is what carries the catalog offline).
        .cache(Cache(File(context.cacheDir, "http_cache"), 20L * 1024 * 1024))
        .addInterceptor(RetryInterceptor())
        .apply {
            // Request lines name tokenized URLs: log them only in debuggable builds.
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                addInterceptor(DebugHttpLogger())
            }
        }
        .build()

    /**
     * Bounded retry for the flaky-network case: ONE extra attempt on connect/IO failure with a
     * short backoff, or one honored Retry-After on 429 (AniList rate-limits at 90 req/min; its
     * Retry-After can be a minute — anything past [MAX_RETRY_AFTER_SEC] isn't worth blocking a
     * caller for, so those bubble out to the stale-cache fallback instead). A single retry is
     * deliberate: it absorbs the common transient failure while keeping the worst case on a
     * connected-but-dead network (cold cache, connect timeouts) to ~2 × the per-attempt budget.
     * Every request body in the app is a small buffered payload, so re-sending is always safe.
     * Cancelled calls (e.g. Explore's latest-wins search) are never retried.
     */
    private class RetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            while (true) {
                try {
                    val response = chain.proceed(chain.request())
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull() ?: 1L
                    if (response.code == 429 && attempt < MAX_RETRIES &&
                        retryAfterSec <= MAX_RETRY_AFTER_SEC && !chain.call().isCanceled()
                    ) {
                        response.close()
                        attempt++
                        backoff(retryAfterSec * 1000)
                        continue
                    }
                    return response
                } catch (e: IOException) {
                    if (attempt >= MAX_RETRIES || chain.call().isCanceled()) throw e
                    attempt++
                    backoff(BACKOFF_MS * attempt)
                }
            }
        }

        /** Sleep between attempts; an interrupt surfaces as a normal call failure, not a stray crash. */
        private fun backoff(ms: Long) {
            try {
                Thread.sleep(ms)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("retry backoff interrupted", ie)
            }
        }

        private companion object {
            const val MAX_RETRIES = 1
            const val BACKOFF_MS = 400L
            const val MAX_RETRY_AFTER_SEC = 5L
        }
    }
}
