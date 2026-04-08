plugins {
    id("com.eignex.kmp") version "1.1.1"
    kotlin("plugin.serialization") version "2.3.0"
}

eignexPublish {
    description.set("ASCII-safe encodings and ultra-small binary serialization for Kotlin, optimized for URLs, file names, and Kubernetes labels. Generates short, predictable payloads within tight character limits.")
    githubRepo.set("Eignex/kencode")
}

kotlin {
    jvm()
    js(IR) { browser(); nodejs() }
    wasmJs { browser(); nodejs() }
    wasmWasi { nodejs() }
    linuxX64(); linuxArm64()
    macosX64(); macosArm64(); mingwX64()
    iosX64(); iosArm64(); iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
            implementation("com.ionspin.kotlin:bignum:0.3.10")
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.10.0")
        }
        jvmTest.dependencies {
            implementation("org.bouncycastle:bcprov-jdk18on:1.83")
        }
    }
}
