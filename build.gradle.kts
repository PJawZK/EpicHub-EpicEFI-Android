buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9.x uses built-in Kotlin. Pin the newer KGP runtime explicitly
        // so the Compose compiler and Kotlin compiler stay on the same release.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.android.test") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
