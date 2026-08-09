rootProject.name = "kencode"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Provisions the JDK the conventions ask for, so a machine without it does not need one installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
