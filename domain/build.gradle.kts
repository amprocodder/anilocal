plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin module: no Android dependency. Trying to import android.* here will not
// compile — that's the enforced boundary that keeps the domain layer clean.
// Bytecode level is pinned explicitly (not via a JVM toolchain) so the module compiles with whatever
// JDK 17+ runs Gradle — no toolchain download plugin needed.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.coroutines.core)
}
