plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Hosts the vendored Aniyomi anime source-api (the REAL classes loaded extensions bind to at
// runtime — see VENDORING.md), the Injekt runtime that seeds them, and the AniyomiSourceAdapter
// that maps a loaded source onto :domain's AnimeSource seam. Depends on :domain only; :data depends
// on :extensions via `implementation` so the vendored eu.kanade.* types never reach :app. Kept
// Hilt-free — Injekt is the DI mechanism inside the vendored source classes, separate from Hilt.
android {
    namespace = "com.anilocal.app.extensions"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)

    // Vendored Aniyomi source-api runtime deps. `implementation` (not `api`): :data consumes
    // :extensions as `implementation`, so none of these leak onto :app's classpath.
    implementation(libs.rxjava)
    implementation(libs.kotlin.reflect)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.jsoup)
    implementation(libs.injekt.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.jsonOkio)
    // Some extensions (keiyoushi.utils-based, e.g. AnimePahe) resolve a ProtoBuf singleton via Injekt.
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.androidx.preference)
    // The JavaScript engine Aniyomi provides to extensions. Shared extractors (e.g. StreamWish via the
    // synchrony deobfuscator) and several sources evaluate obfuscated player JS with it; without it
    // they fail with NoClassDefFoundError, which the adapter turns into "nothing found".
    implementation(libs.quickjs.android)
}
