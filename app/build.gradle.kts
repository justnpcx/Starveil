import java.util.Properties

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
 * ships as `0.N.0`, so the delivered M1–M3 are `0.3.0` and the M7 beta is
 * `0.7.0`. M4–M6 were never released on their own, so they have no version of
 * their own either — the number tracks the milestone that ships, not the count
 * of milestones passed.
 *
 * [appVersionCode] is *derived* rather than written down separately. Two
 * hand-maintained numbers that must stay in step is a rule that will be broken
 * eventually, and the failure is silent: the store rejects an upload for a
 * non-increasing code long after the mistake. Deriving it means the code is
 * monotonic exactly when the name moves forward, which is the property that
 * actually matters.
 */
val appVersionName = "0.7.0"
val appVersionCode = appVersionName.split('.').let { (major, minor, patch) ->
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

/**
 * The Sentry DSN is injected, never committed.
 *
 * It is not a secret in the usual sense — it ships inside every client that
 * uses it — but committing it bakes one person's project into every fork and
 * makes swapping to a self-hosted instance a code change. It is also the only
 * switch that decides whether the app has crash reporting at all: an empty DSN
 * means `SentryAndroid.init` is never called and the setting does not appear.
 *
 * Three sources, in the order a build is most likely to have one:
 * `-PsentryDsn=…`, `SENTRY_DSN` in the environment (CI), and `sentry.dsn` in
 * `local.properties` (the local sideload build). `local.properties` is
 * gitignored, which is why it is the one written down for a developer.
 */
val sentryDsn: String = providers.gradleProperty("sentryDsn").orNull
    ?: providers.environmentVariable("SENTRY_DSN").orNull
    ?: Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }.getProperty("sentry.dsn")
    ?: ""

android {
    namespace = "com.justnpcx.starveil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.justnpcx.starveil"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        // Empty means "no crash reporting in this build". Read at runtime, so a
        // debug build without a DSN is a build without Sentry rather than a
        // build that fails on a missing key.
        buildConfigField("String", "SENTRY_DSN", "\"${sentryDsn.replace("\"", "\\\"")}\"")
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
            // R8 was off through the betas: a minified build that has not been
            // run on a device is worse than a large one, and there was no
            // device to run it on. On now, because a public release cannot ship
            // 127 MB of unshrunk code — and the keeps below are the parts that
            // break silently when it is wrong (JNI, reflection, Room).
            isMinifyEnabled = true
            isShrinkResources = true
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
        // SENTRY_DSN above; nothing else in the app uses BuildConfig.
        buildConfig = true
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

    // Crash reporting. Opt-out, and redacted before it leaves the device.
    implementation(libs.sentry.android)

    // The profile generator lives with the benchmarks: both need a device and
    // both describe the same build.
    baselineProfile(project(":benchmark"))

    // Tests.
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
