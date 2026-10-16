plugins {
    // A com.android.test module: built against the app's `benchmark` variant
    // and never shipped. The baseline-profile plugin is applied to :app, not
    // here — this module only *provides* the generator and the benchmarks.
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.justnpcx.starveil.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The harness drops the app's shader cache before measuring, so the
        // first frame's shader compilation is included in the number. On this
        // ROM the app cannot delete its own code_cache, so the broadcast comes
        // back with the failure code and the harness — whose only two states
        // are "worked" and "throw" — refuses to run at all.
        //
        // Downgrading that to a warning is the honest option available: the
        // drop is still attempted, and the failure is logged rather than
        // silently ignored. What it costs is that the first iteration may be
        // faster than production, which is why the metric reported is the
        // median of 10 rather than the best.
        //
        // Declared here rather than passed on the command line: a custom runner
        // argument from the command line is incompatible with the configuration
        // cache, and Gradle says so on every build.
        testInstrumentationRunnerArguments["androidx.benchmark.dropShadersThrowOnFailure"] = "false"
    }

    // Benchmarks the app module rather than shipping anything of its own.
    targetProjectPath = ":app"

    buildTypes {
        // A test module does not inherit the target's build types, so the one
        // the benchmarks run against has to exist here too — and the name is
        // what pairs this module's variant with :app's. Without it the
        // `beforeVariants` filter below has nothing to enable and the module
        // builds no variants at all.
        create("benchmark") {
            matchingFallbacks += listOf("release")
            // A test module does not inherit the app's signing config, and an
            // unsigned APK cannot be installed at all — the failure is
            // INSTALL_PARSE_FAILED_NO_CERTIFICATES at the very end of the
            // install session, which reads like a corrupt file rather than a
            // missing signature.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // The harness instruments itself; without this the module is built as a
    // test APK for someone else's target and the rule cannot start.
    experimentalProperties["android.experimental.self-instrumenting"] = true

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
    implementation(libs.androidx.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.espresso.core)
    // Drives the app's own screens to set the list up before measuring it.
    implementation(libs.androidx.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

// Only ever built for the benchmark variant: there is no debug or release
// benchmark APK to accidentally install.
androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}
