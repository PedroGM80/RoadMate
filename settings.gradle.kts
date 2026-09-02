pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // BRouter (offline routing engine) publishes only via JitPack.
        // Scoped so nothing else can resolve from here.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.abrensch.*") }
        }
    }
}

rootProject.name = "RoadMate"
include(":app")
include(":domain")
include(":data")
