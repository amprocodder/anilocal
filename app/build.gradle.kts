plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.anilocal.app"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.anilocal.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // Debug builds are signed with the COMMITTED app/debug.keystore (a standard, non-secret
    // debug key — storepass/keypass "android"). Without this, every CI run mints a fresh
    // ephemeral debug key, so no sideloaded APK can ever update another install channel
    // (INSTALL_FAILED_UPDATE_INCOMPATIBLE / "App not installed" + full data wipe each time).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 shrinks and optimizes the app; the extension host's ABI (the vendored source API
            // and the libraries extensions link against by name) is kept by :extensions'
            // consumer-rules.pro. Resource shrinking stays off: androidx.preference, which
            // extensions build their settings with, resolves its styles by name.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    // Typed navigation routes are @Serializable so the back stacks survive process death.
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    // The design system is the app's own (ui/theme + ui/kit) on Compose Foundation — no Material.
    implementation(libs.compose.foundation)

    // The image loader (ui/image) fetches through the shared OkHttp client.
    implementation(libs.okhttp)

    // The player engine. Its UI — video surface, captions, controls, media session — is the app's own.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // DASH (.mpd) and SmoothStreaming (.ism) so adaptive extension streams resolve instead of
    // crashing — DefaultMediaSourceFactory reflectively loads these factories when present.
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
}
