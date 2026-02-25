import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.dokka") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.5"
    `maven-publish`
    signing

    id("io.github.sgtsilvio.gradle.maven-central-publishing") version "0.4.1"
}

group = "com.eignex"
version = findProperty("ciVersion") as String? ?: "SNAPSHOT"

repositories { mavenCentral() }

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(kotlin("test"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.83")
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
                description.set("ASCII-safe encodings and ultra-small binary serialization for Kotlin, optimized for URLs, file names, and Kubernetes labels. Generates short, predictable payloads within tight character limits.")
                url.set("https://github.com/Eignex/kencode")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                scm {
                    url.set("https://github.com/Eignex/kpermute")
                    connection.set("scm:git:https://github.com/Eignex/kpermute.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Eignex/kpermute.git")
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
