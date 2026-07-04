plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.anilocal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anilocal.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }

        // Optional features (blank = no-op). See README. (MAL sync is keyless — username only.)
        buildConfigField("String", "TMDB_API_KEY", "\"${project.findProperty("TMDB_API_KEY") ?: ""}\"")
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coil.compose)

    // Player UI (the player itself lives in :app; the offline cache factory comes from :data).
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    // DASH (.mpd) and SmoothStreaming (.ism) so adaptive extension streams resolve instead of
    // crashing — DefaultMediaSourceFactory reflectively loads these factories when present.
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
}
