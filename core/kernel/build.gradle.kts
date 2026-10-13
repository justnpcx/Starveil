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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Kernel bridge: pure Kotlin + coroutines. No Android UI.
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
}
