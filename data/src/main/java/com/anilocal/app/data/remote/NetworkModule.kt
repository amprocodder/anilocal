package com.anilocal.app.data.remote

import android.content.Context
import com.anilocal.app.data.metadata.anilist.AniListApi
import com.anilocal.app.data.metadata.mal.MalApi
import com.anilocal.app.data.metadata.tmdb.TmdbApi
import com.anilocal.app.data.metadata.extension.ExtensionRepoApi
import com.anilocal.app.data.skip.AniSkipApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun moshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

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
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
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

    private fun retrofit(baseUrl: String, client: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides @Singleton
    fun aniListApi(client: OkHttpClient, moshi: Moshi): AniListApi =
        retrofit("https://graphql.anilist.co/", client, moshi).create(AniListApi::class.java)

    @Provides @Singleton
    fun aniSkipApi(client: OkHttpClient, moshi: Moshi): AniSkipApi =
        retrofit("https://api.aniskip.com/", client, moshi).create(AniSkipApi::class.java)

    @Provides @Singleton
    fun tmdbApi(client: OkHttpClient, moshi: Moshi): TmdbApi =
        retrofit("https://api.themoviedb.org/3/", client, moshi).create(TmdbApi::class.java)

    // Keyless: the public list comes from MAL's own `load.json` page endpoint (not the v2 API).
    @Provides @Singleton
    fun malApi(client: OkHttpClient, moshi: Moshi): MalApi =
        retrofit("https://myanimelist.net/", client, moshi).create(MalApi::class.java)

    // Extension repo index.min.json — full URLs are supplied per-call via @Url, so the base is a placeholder.
    @Provides @Singleton
    fun extensionRepoApi(client: OkHttpClient, moshi: Moshi): ExtensionRepoApi =
        retrofit("https://raw.githubusercontent.com/", client, moshi).create(ExtensionRepoApi::class.java)
}
