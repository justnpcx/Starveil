plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    // Generates app/src/main/baseline-prof.txt from the :benchmark module's
    // profile generator, so the startup classes are AOT-compiled on device
    // instead of being JIT-warmed on every cold start.
    alias(libs.plugins.baselineprofile)
}

/**
 * Versioning.
 *
 * [appVersionName] is semver and is the only value edited by hand; milestone N
 * ships as `0.N.0`, so the delivered M1–M3 are `0.3.0`.
 *
 * [appVersionCode] is *derived* rather than written down separately. Two
 * hand-maintained numbers that must stay in step is a rule that will be broken
 * eventually, and the failure is silent: the store rejects an upload for a
 * non-increasing code long after the mistake. Deriving it means the code is
 * monotonic exactly when the name moves forward, which is the property that
 * actually matters.
 */
val appVersionName = "0.3.0"
val appVersionCode = appVersionName.split('.').let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

android {
    namespace = "com.justnpcx.starveil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.justnpcx.starveil"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    splits {
        // libbox.aar carries armeabi-v7a, arm64-v8a and x86_64, so all three
        // are real targets. One APK holding every ABI would be ~230 MB of
        // native code, so each ABI gets its own — the build emits three, and
        // CI uploads all of them. (x86 32-bit is deliberately absent: it is
        // emulator-only and libbox is not built for it.)
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
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

        /**
         * What the benchmarks measure.
         *
         * `initWith(release)` rather than a copy of the release settings: the
         * numbers are only worth having if they describe something close to
         * what ships, and a hand-copied variant drifts the moment release
         * changes. Debug-signed because it has to install on a device, and
         * non-debuggable because a debuggable app has different runtime
         * characteristics (assertions, no AOT) and would flatter the numbers.
         */
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
    implementation(project(":feature:editor"))
    implementation(project(":feature:rules"))
    implementation(project(":feature:logs"))
    implementation(project(":feature:settings"))

    // Installs the Baseline Profile bundled in the APK, on first run.
    implementation(libs.androidx.profileinstaller)

    // The home-screen widget. Its own rendering path (RemoteViews), not Compose.
    implementation(libs.glance.appwidget)

    // The profile generator lives with the benchmarks: both need a device and
    // both describe the same build.
    baselineProfile(project(":benchmark"))

    // Tests.
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
