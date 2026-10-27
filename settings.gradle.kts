rootProject.name = "kencode"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the required JDK if missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
