pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Starveil"

include(":app")
include(":core:model")
include(":core:data")
include(":core:subscription")
include(":core:kernel")
include(":core:probe")
include(":core:ui")
include(":feature:dashboard")
include(":feature:profiles")
include(":feature:editor")
include(":feature:rules")
include(":feature:logs")
include(":feature:settings")

// Performance measurement (M4). A com.android.test module, built against the
// app's benchmark variant and never shipped — kept out of the release path on
// purpose, since its dependencies (macrobenchmark, uiautomator) are large.
include(":benchmark")
