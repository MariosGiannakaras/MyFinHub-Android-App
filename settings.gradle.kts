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
                includeModule("com.github.adaptech-cz.Tesseract4Android", "tesseract4android")
            }
        }
    }
}

rootProject.name = "MyFinHub-Android"
include(":app")
include(":benchmark")
