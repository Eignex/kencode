import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.eignex.kmp")
    id("com.eignex.publish.github")
    kotlin("plugin.serialization")
}

repositories {
    ivy {
        url = uri("https://github.com/Eignex/kbuild/releases/download")
        patternLayout { artifact("v[revision]/[artifact]-[revision](-[classifier]).[ext]") }
        metadataSources { gradleMetadata() }
        content { includeModule("com.eignex", "kbuild-platform") }
    }
}

eignexPublish {
    description.set("KEncode is a kotlinx.serialization library that produces short ASCII-safe texts for eg URLs or file names.")
    githubRepo.set("Eignex/kencode")
}

// Routine checks cover JVM + Linux x64; release validation opts into every target.
val fullTargets = providers.gradleProperty("targets.full").isPresent

eignexBuild {
    jvmToolchain.set(17)
    abiValidationEnabled.set(fullTargets)
}

@OptIn(ExperimentalWasmDsl::class)
kotlin {
    jvm()
    linuxX64()
    if (fullTargets) {
        js { browser(); nodejs() }
        wasmJs { browser(); nodejs() }
        wasmWasi { nodejs() }
        linuxArm64()
        macosArm64(); mingwX64()
        iosX64(); iosArm64(); iosSimulatorArm64()
    }

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
            implementation("com.google.zxing:core:3.5.4")
        }
    }
}

dokka {
    dokkaSourceSets.configureEach {
        includes.from("package.md")
        sourceLink {
            localDirectory.set(projectDir.resolve("src"))
            val sub = projectDir.relativeTo(rootDir).invariantSeparatorsPath
            val prefix = if (sub.isEmpty()) "src" else "$sub/src"
            remoteUrl("https://github.com/Eignex/${rootProject.name}/blob/main/$prefix")
            remoteLineSuffix.set("#L")
        }
    }
}

// Select an installed simulator in CI rather than relying on a particular iPhone model.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.gradleProperty("iosSimulatorDevice").orNull?.let { device.set(it) }
}
