plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Hosts the vendored Aniyomi anime source-api (the REAL classes loaded extensions bind to at
// runtime), the Injekt runtime that seeds them, and the AniyomiSourceAdapter that maps a loaded
// source onto :domain's AnimeSource seam. Depends on :domain only; :data depends on :extensions.
// Kept Hilt-free — Injekt is the DI mechanism inside the vendored source classes, separate from Hilt.
android {
    namespace = "com.anilocal.app.extensions"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
}
