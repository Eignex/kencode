plugins {
    id("com.eignex.kmp") version "1.1.4"
    kotlin("plugin.serialization") version "2.3.0"
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
            implementation("com.google.zxing:core:3.5.3")
        }
    }
}
