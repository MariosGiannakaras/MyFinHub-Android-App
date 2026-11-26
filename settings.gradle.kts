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
        exclusiveContent {
            forRepository {
                maven { url = uri("https://jitpack.io") }
            }
            filter {
                includeModule("cz.adaptech.tesseract4android", "tesseract4android")
            }
        }
    }
}

rootProject.name = "MyFinHub-Android"
include(":app")
include(":benchmark")
