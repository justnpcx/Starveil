plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.justnpcx.starveil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.justnpcx.starveil"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-skeleton"

        ndk {
            // libbox.aar ships arm64-v8a only, so that is the only ABI this
            // app can actually run on. Without this filter the APK also
            // carries sqlcipher and friends for armeabi-v7a / x86 / x86_64,
            // which makes the installer treat those devices as compatible —
            // the app then installs and launches, and dies on "connect" when
            // libbox fails to load. Better to fail at install, and to not ship
            // 5.6 MB of native libraries for ABIs that cannot run the kernel.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose + Navigation + DI (the only UI toolkit in this project).
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    ksp(libs.hilt.compiler)

    // First-party modules.
    implementation(project(":core:ui"))
    implementation(project(":core:kernel"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:subscription"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:profiles"))
    implementation(project(":feature:rules"))
    implementation(project(":feature:logs"))
    implementation(project(":feature:settings"))

    // Tests.
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
