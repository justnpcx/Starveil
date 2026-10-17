// Top-level build file. All version pins live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Same AGP artifact as the two above: declaring it here is what lets
    // :benchmark ask for it by alias without a version, instead of Gradle
    // finding AGP already on the classpath and refusing to check compatibility.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.baselineprofile) apply false
}

// The performance tests print their measurement (CONFIGBUILD_500_NODES_MS, and
// whatever comes after it). Without this Gradle swallows stdout, so the number
// only ever appears when the assertion fails — too late to see a regression
// coming, and no way to tell a slow runner from a slow change.
subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            showStandardStreams = true
        }
    }
}
