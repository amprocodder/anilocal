package com.anilocal.app.di

import com.anilocal.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Surfaces build-time config (in :app's BuildConfig) to the :data layer via Hilt. Keeps the
 * keys in one place (the app module) while letting data-layer impls consume them.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides @Singleton @Named("mal_client_id")
    fun malClientId(): String = BuildConfig.MAL_CLIENT_ID
}
