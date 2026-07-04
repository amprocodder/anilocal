plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.anilocal.app.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain"))
    // Vendored Aniyomi source-api + AniyomiSourceAdapter live here. `implementation` (not `api`)
    // so the vendored eu.kanade.* types never leak onto :app's classpath — the seam stays intact.
    implementation(project(":extensions"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)

    // Media3 download + cache stack (DownloadManager, SimpleCache, CacheDataSource, scheduler).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // DASH/SmoothStreaming so the DownloadManager's DefaultDownloaderFactory can create the matching
    // segment downloaders (it loads them reflectively, same as the player's source factory). Without
    // these an adaptive download fails the way DASH playback used to crash.
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
}
