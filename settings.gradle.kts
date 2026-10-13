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
    versionCatalogs {
        create("libs") {
            from(files("gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "Starveil"

include(":app")
include(":core:model")
include(":core:data")
include(":core:subscription")
include(":core:kernel")
include(":core:ui")
include(":feature:dashboard")
include(":feature:profiles")
include(":feature:rules")
include(":feature:logs")
include(":feature:settings")
