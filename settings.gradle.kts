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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NexLock"

include(
    ":app",
    ":core",
    ":core-common",
    ":core-security",
    ":core-database",
    ":core-design",
    ":feature-dashboard",
    ":feature-app-lock",
    ":feature-authentication",
    ":feature-vault",
    ":feature-intruder-detection",
    ":feature-automation",
    ":feature-settings",
)
