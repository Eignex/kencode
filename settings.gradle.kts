rootProject.name = "kencode"

pluginManagement {
    val kbuildVersion = "1.3.6"
    repositories {
        ivy {
            url = uri("https://github.com/Eignex/kbuild/releases/download")
            patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
            metadataSources { gradleMetadata() }
            content { includeModule("com.eignex", "kbuild") }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy.eachPlugin {
        if (requested.id.id == "com.eignex.kmp") {
            useModule("com.eignex:kbuild:${requested.version ?: kbuildVersion}")
        }
    }
    plugins {
        id("com.eignex.kmp") version kbuildVersion
    }
}

plugins {
    // Auto-provisions the required JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
