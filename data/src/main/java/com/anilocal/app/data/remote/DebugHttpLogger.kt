package com.anilocal.app.data.remote

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Debug-build request log: one line per request and one per response (method, URL, status, time) —
 * what OkHttp's BASIC logging printed, without its extra library. Installed only in debuggable
 * builds: URLs carry tokens (signed CDN paths, stream links) that must not reach a release logcat.
 */
internal class DebugHttpLogger : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        Log.d(TAG, "--> ${request.method} ${request.url}")
        val start = System.nanoTime()
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            Log.d(TAG, "<-- FAILED ${request.method} ${request.url}: $e")
            throw e
        }
        Log.d(TAG, "<-- ${response.code} ${response.request.url} (${(System.nanoTime() - start) / 1_000_000}ms)")
        return response
    }

    private companion object {
        const val TAG = "AniLocalHttp"
    }
}
