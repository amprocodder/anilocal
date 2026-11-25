plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.anilocal.app.data"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    // Vendored Aniyomi source-api + AniyomiSourceAdapter live here. `implementation` (not `api`)
    // so the vendored eu.kanade.* types never leak onto :app's classpath — the seam stays intact.
    implementation(project(":extensions"))



    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    // HTTP + JSON: the same OkHttp and kotlinx.serialization the extension host already ships
    // (extensions link against them), so the data layer adds no library of its own for either.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)

    // Media3 download + cache stack (DownloadManager, SimpleCache, CacheDataSource, scheduler).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // DASH/SmoothStreaming so the DownloadManager's DefaultDownloaderFactory can create the matching
    // segment downloaders (it loads them reflectively, same as the player's source factory). Without
    // these an adaptive download fails the way DASH playback used to crash.
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
}
