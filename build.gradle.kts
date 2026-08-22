buildscript {
    dependencies {
        // AGP 9 uses built-in Kotlin. Pin the KGP runtime used by the Compose compiler as well.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
