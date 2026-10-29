plugins { application }

val pomOnly = providers.gradleProperty("consumer.pomOnly").getOrElse("false").toBoolean()
val library = providers.gradleProperty("consumer.library").get()
val module = if (pomOnly) "$library-jvm" else library
val version = providers.gradleProperty("consumer.version").get()

dependencies {
    // No KBuild plugin, platform, or Kotlin dependency declaration: published metadata must suffice.
    implementation("com.eignex:$module:$version") {
        // CI reuses a private staging version; always inspect its current local metadata/artifact.
        isChanging = true
    }
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
application { mainClass.set("Consumer") }

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
