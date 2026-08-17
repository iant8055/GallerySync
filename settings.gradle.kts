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

        // MSAL depends transitively on com.microsoft.device.display:display-mask (Surface Duo
        // hinge support), which Microsoft publishes only to this feed — it is on neither Google
        // Maven nor Maven Central, so MSAL cannot resolve without it.
        //
        // Deliberately scoped with includeGroup: this feed may serve exactly that one group and
        // nothing else, so it can never satisfy any other dependency in the build.
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
            content {
                includeGroup("com.microsoft.device.display")
            }
        }
    }
}

rootProject.name = "Gallery Sync"
include(":app")
