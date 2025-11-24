import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.3"
    `maven-publish`
    signing

    id("io.github.sgtsilvio.gradle.maven-central-publishing") version "0.4.1"
}

group = "com.eignex"
version = "1.0.0"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JVM_21) }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.80")
}

tasks.test { useJUnitPlatform() }

tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaGenerate"))
    from(layout.buildDirectory.dir("dokka/html"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("kencode")
                description.set("Pure Kotlin JVM library.")
                url.set("https://github.com/Eignex/kencode")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm {
                    url.set("https://github.com/Eignex/kencode")
                    connection.set("scm:git:https://github.com/Eignex/kencode.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Eignex/kencode.git")
                }
                developers {
                    developer {
                        id.set("rasros")
                        name.set("Rasmus Ros")
                        url.set("https://github.com/rasros")
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "localStaging"
            url = uri(layout.buildDirectory.dir("staging-repo"))
        }
    }
}

signing {
    val key = findProperty("signingKey") as String?
    val pass = findProperty("signingPassword") as String?

    if (key != null && pass != null) {
        useInMemoryPgpKeys(key, pass)
        sign(publishing.publications["mavenJava"])
    } else {
        logger.lifecycle("Signing disabled: signingKey or signingPassword not defined.")
    }
}
