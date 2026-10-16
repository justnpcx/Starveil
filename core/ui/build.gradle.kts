plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.justnpcx.starveil.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged resources/manifest to render and to
            // find the test Activity that createComposeRule launches.
            isIncludeAndroidResources = true
        }
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The health chart draws a NodeHealth, so the model types are part of this
    // module's public API — hence `api` rather than `implementation`.
    api(project(":core:model"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui.tooling.preview)
    // For LocalLifecycleOwner: the core's ambient loops must pause when the
    // screen is not visible (design system §5.5).
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    // Gesture tests: a real pointer stream through Compose, on the JVM.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
