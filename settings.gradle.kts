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
        maven("https://jitpack.io")
        flatDir {
            dirs("app/libs")
        }
    }
}

rootProject.name = "vr2xr"
include(":app", ":onexr")
project(":onexr").projectDir = file("reference/one-xr/onexr")
