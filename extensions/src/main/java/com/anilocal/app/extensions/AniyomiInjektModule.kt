package com.anilocal.app.extensions

import android.app.Application
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.serialization.json.Json
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

/**
 * Registers the singletons a loaded Aniyomi [eu.kanade.tachiyomi.animesource.online.AnimeHttpSource]
 * resolves via Injekt at construction (`injectLazy()` / `Injekt.get()`): the [Application] (used by
 * `ConfigurableAnimeSource.getSourcePreferences()`), a lenient [Json], and the [NetworkHelper] that
 * provides the shared OkHttpClient. Seeded once by [AniyomiRuntime]; kept entirely separate from
 * Hilt (Injekt is the DI the vendored source code expects).
 */
class AniyomiInjektModule(private val app: Application) : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        addSingleton<Application>(app)
        addSingletonFactory {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            }
        }
        addSingletonFactory { NetworkPreferences() }
        addSingletonFactory { NetworkHelper(app, get()) }
    }
}
