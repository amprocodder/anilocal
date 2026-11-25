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
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            // Keep the extension host ABI intact and the original Phase 2 update identity.
            // Loaded extension APKs reference the host's class and method names at runtime.
            isMinifyEnabled = false
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
    implementation(libs.compose.material3)


    implementation(libs.coil.compose)

    // The player engine. Its UI — video surface, captions, controls, media session — is the app's own.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // DASH (.mpd) and SmoothStreaming (.ism) so adaptive extension streams resolve instead of
    // crashing — DefaultMediaSourceFactory reflectively loads these factories when present.
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
}
