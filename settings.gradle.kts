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
        // Braze — uncomment when Phase 3 activates
        // maven { url = uri("https://appboy.github.io/appboy-android-sdk/sdk") }
    }
}

rootProject.name = "MarketApp"
include(":app")
