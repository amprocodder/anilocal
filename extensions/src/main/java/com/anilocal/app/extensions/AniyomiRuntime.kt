package com.anilocal.app.extensions

import android.app.Application
import uy.kohesive.injekt.Injekt

/**
 * Seeds the process-global Injekt registry that the vendored Aniyomi source classes consult
 * (see [AniyomiInjektModule]) so a loaded `AnimeHttpSource` constructs without crashing. Idempotent;
 * must run before any extension source is instantiated — the dynamic extension loader calls this
 * (Phase 3). Independent of Hilt.
 */
object AniyomiRuntime {

    @Volatile
    private var seeded = false

    @Synchronized
    fun seed(app: Application) {
        if (seeded) return
        Injekt.importModule(AniyomiInjektModule(app))
        seeded = true
    }
}
