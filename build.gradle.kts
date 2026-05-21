plugins {
    id("com.eignex.kmp") version "1.2.0"
    kotlin("plugin.serialization") version "2.3.20"
}

eignexPublish {
    description.set("KEncode is a kotlinx.serialization library that produces short ASCII-safe texts for eg URLs or file names.")
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
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core")
            compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
            implementation("com.ionspin.kotlin:bignum:0.3.10")
        }
        commonTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf")
        }
        jvmTest.dependencies {
            implementation("org.bouncycastle:bcprov-jdk18on:1.83")
            implementation("com.google.zxing:core:3.5.3")
        }
    }
}
