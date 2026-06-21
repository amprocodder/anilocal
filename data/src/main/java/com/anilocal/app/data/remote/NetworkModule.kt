package com.anilocal.app.data.remote

import com.anilocal.app.data.metadata.anilist.AniListApi
import com.anilocal.app.data.metadata.mal.MalApi
import com.anilocal.app.data.metadata.tmdb.TmdbApi
import com.anilocal.app.data.skip.AniSkipApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun moshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

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

    @Provides @Singleton
    fun malApi(client: OkHttpClient, moshi: Moshi): MalApi =
        retrofit("https://api.myanimelist.net/", client, moshi).create(MalApi::class.java)
}
