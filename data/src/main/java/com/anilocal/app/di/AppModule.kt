package com.anilocal.app.di

import com.anilocal.app.data.auth.FirebaseAuthRepository
import com.anilocal.app.data.download.DownloadRepositoryImpl
import com.anilocal.app.data.local.RoomLibraryRepository
import com.anilocal.app.data.local.RoomProgressRepository
import com.anilocal.app.data.metadata.anilist.AniListCatalogRepository
import com.anilocal.app.data.metadata.mal.MalRepositoryImpl
import com.anilocal.app.data.settings.DataStoreSettingsRepository
import com.anilocal.app.data.skip.AniSkipRepository
import com.anilocal.app.data.source.SampleLocalSource
import com.anilocal.app.data.source.SampleSintelSource
import com.anilocal.app.data.source.SourceRegistryImpl
import com.anilocal.app.data.source.SourceStreamRepository
import com.anilocal.app.domain.auth.AuthRepository
import com.anilocal.app.domain.repo.CatalogRepository
import com.anilocal.app.domain.repo.DownloadRepository
import com.anilocal.app.domain.repo.LibraryRepository
import com.anilocal.app.domain.repo.MalRepository
import com.anilocal.app.domain.repo.ProgressRepository
import com.anilocal.app.domain.repo.SettingsRepository
import com.anilocal.app.domain.repo.SkipRepository
import com.anilocal.app.domain.repo.StreamRepository
import com.anilocal.app.domain.source.AnimeSource
import com.anilocal.app.domain.source.SourceRegistry
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * Built-in stream sources, contributed into a set. The active source is chosen at runtime from
     * [SourceRegistry] by the user's selected-source setting (see [SourceStreamRepository]);
     * dynamically loaded extensions get added to the same set later. Each impl is `@Singleton`.
     */
    @Binds @IntoSet
    abstract fun bindSampleSource(impl: SampleLocalSource): AnimeSource

    @Binds @IntoSet
    abstract fun bindSintelSource(impl: SampleSintelSource): AnimeSource

    @Binds @Singleton
    abstract fun bindSourceRegistry(impl: SourceRegistryImpl): SourceRegistry

    @Binds @Singleton
    abstract fun bindCatalogRepository(impl: AniListCatalogRepository): CatalogRepository

    @Binds @Singleton
    abstract fun bindStreamRepository(impl: SourceStreamRepository): StreamRepository

    @Binds @Singleton
    abstract fun bindSkipRepository(impl: AniSkipRepository): SkipRepository

    @Binds @Singleton
    abstract fun bindLibraryRepository(impl: RoomLibraryRepository): LibraryRepository

    @Binds @Singleton
    abstract fun bindProgressRepository(impl: RoomProgressRepository): ProgressRepository

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds @Singleton
    abstract fun bindMalRepository(impl: MalRepositoryImpl): MalRepository

    companion object {
        /**
         * App-lifetime scope for fire-and-forget work that must outlive a ViewModel — e.g. the final
         * watch-progress save when the player VM is being cleared (its own `viewModelScope` is already
         * cancelled by then). Never cancelled; lives for the process.
         */
        @Provides
        @Singleton
        @Named("appScope")
        fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
