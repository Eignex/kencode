rootProject.name = "library-consumer"

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                val githubRepository = providers.gradleProperty("consumer.githubRepository")
                if (githubRepository.isPresent) {
                    ivy {
                        url = uri(githubRepository.get())
                        patternLayout { artifact("[artifact]-[revision](-[classifier]).[ext]") }
                        metadataSources { gradleMetadata() }
                    }
                } else {
                    maven {
                        url = uri(providers.gradleProperty("consumer.repository").get())
                        if (providers.gradleProperty("consumer.pomOnly").getOrElse("false").toBoolean()) {
                            metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }
                        }
                    }
                }
            }
            filter { includeGroup("com.eignex") }
        }
        mavenCentral()
    }
}
