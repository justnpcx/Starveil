plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.justnpcx.starveil.feature.editor"
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

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)

    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    // Pasting a link into the editor resolves it with the same resolver the
    // importer uses, so a pasted link and an imported one cannot disagree.
    implementation(project(":core:subscription"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coroutines.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
