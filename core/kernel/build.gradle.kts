plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.justnpcx.starveil.core.kernel"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Kernel bridge: pure Kotlin + coroutines. No Android UI.
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)

    // sing-box core (libbox), checked in as an AAR. It lives here, not in
    // :app, because :app and :feature:* must never touch the kernel directly.
    // Reproducible via tools/build-libbox.sh — see docs/04-libbox-build.md.
    implementation(files("libs/libbox.aar"))

    testImplementation(libs.junit)
}
