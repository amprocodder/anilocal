plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin module: no Android dependency. Trying to import android.* here will not
// compile — that's the enforced boundary that keeps the domain layer clean.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.coroutines.core)
}
