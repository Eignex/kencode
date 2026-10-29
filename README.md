<p align="center">
  <a href="https://eignex.com/">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner-white.svg">
      <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner.svg">
      <img alt="Eignex" src="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner.svg" style="max-width: 100%; width: 22em;">
    </picture>
  </a>
</p>

# KEncode

[![Maven Central](https://img.shields.io/maven-central/v/com.eignex/kencode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.eignex/kencode)
[![Build](https://github.com/eignex/kencode/actions/workflows/build.yml/badge.svg)](https://github.com/eignex/kencode/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/eignex/kencode/branch/main/graph/badge.svg)](https://codecov.io/gh/eignex/kencode)
[![License](https://img.shields.io/github/license/eignex/kencode)](https://github.com/eignex/kencode/blob/main/LICENSE)

KEncode turns your data into short strings. It's useful when you need to fit
data into a URL, a file name, a Kubernetes label, or anywhere else with a tight
length limit.

There are three parts. ByteEncoding turns bytes into text. PackedFormat turns
your Kotlin classes into as few bytes as possible. EncodedFormat does both, so
you go straight from an object to a short string. If you want to know how it
works, read the [technical deep dive](https://eignex.com/posts/kencode-packing-data-for-strict-limits/).

## Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.4.0")
}
```

You also need the kotlinx.serialization plugin to use the formats.

## Example

```kotlin
@Serializable
data class Payload(
    @PackedType(IntPacking.DEFAULT) val id: ULong, // varint
    @PackedType(IntPacking.SIGNED) val delta: Int, // zigzag
    val urgent: Boolean,    // 1 bit
    val handled: Instant?,  // 1 bit when null
    val type: PayloadType,  // 2 bits
)

enum class PayloadType { TYPE1, TYPE2, TYPE3 }

val payload = Payload(123u, -2, true, null, PayloadType.TYPE1)
val encoded = EncodedFormat.encodeToString(payload) // "00Xl1", 3 raw bytes
val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
```

## PackedFormat

PackedFormat stores booleans, nulls, and enums in just a few bits each.
Numbers are stored in as few bytes as they need. Add PackedType to a field if
it is often negative (SIGNED) or if you want a fixed size (FIXED).

```kotlin
val format = PackedFormat {
    defaultEncoding = IntPacking.FIXED
    serializersModule = myCustomModule
}
val bytes = format.encodeToByteArray(payload)
```

Fields are written in the order you declare them, with no names or tags. So if
you change a class, old strings may no longer decode.

The encoded format only stays the same between patch versions. A new minor
version may change it, so encode stored strings again after upgrading.

## EncodedFormat

EncodedFormat serializes your object, optionally transforms the bytes, and
then turns them into text. You can swap each step. For example, you can use
ProtoBuf instead of PackedFormat, add a checksum, or pick a different codec.

```kotlin
val format = EncodedFormat {
    codec = Base36
    checksum = Crc16
    binaryFormat = ProtoBuf
}

val compacted = EncodedFormat {
    transform = CompactZeros.then(Crc16.asTransform())
}
```

You can also write your own transforms, like these examples for
[encryption](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/EncryptionExample.kt)
and [error correction](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/ErrorCorrectionExample.kt).

## Codecs

Every codec can be used on its own, and you can give it your own alphabet.
Here is what the text "any byte data" (13 bytes) looks like in each:

| Codec  | Output                  | Length | Alphabet         |
|--------|-------------------------|--------|------------------|
| Base62 | `2BVj6VHhfNlsGmoMQF`    | 18     | `[0-9A-Za-z]`    |
| Base36 | `0ksef5o4kvegb70nre15t` | 21     | `[0-9a-z]`       |
| Base64 | `YW55IGJ5dGUgZGF0YQ==`  | 20     | `[0-9A-Za-z+/=]` |
| Base85 | `@;^?5@X3',+Cno&@/`     | 17     | ASCII 33–117     |

For even shorter strings, BaseRadix can use a large range of Unicode
characters.

## Build validation

`./gradlew check lintDocs koverXmlReport` checks JVM and Linux x64.
JS, Wasm, WASI and other native targets are configured only with `-Ptargets.full`. JVM checks also
run the packaged JAR on Java 17 and 25. CI stages the configured publications,
checks their metadata and artifacts, and runs an independent Java consumer using
both Gradle metadata and POM-only resolution.

Normal pull-request and main builds use one Linux job. Tagged stable and RC releases
enable `-Ptargets.full`, with focused macOS ARM64, iOS simulator and Windows checks.
The build workflow's manual `fullTargets` input runs the same gate without publishing.
Automatic snapshots publish all targets without repeating the full runtime gate.
Releases rerun the full Linux gate at the actual release version, in parallel with
the macOS/iOS and Windows jobs. Publication waits for every platform to pass and
rechecks its staged artifacts and independent consumers before publishing.

ABI references live in `api/` for normal checks and `api-full/` for full checks.
When intentionally updating the API, run `./gradlew updateKotlinAbi` and
`./gradlew updateKotlinAbi -Ptargets.full`. JavaScript and Wasm dependency lockfiles
under `kotlin-js-store/` must be committed; use
`./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock -Ptargets.full` to update them.

CI caches Gradle dependency/task outputs, the host-specific Native toolchain, and
Gradle-encrypted configuration state separately. Configuration state is persisted
only when the encryption secret is available. The local CI publication uses a
stable version so source-only commits can reuse configuration state; the consumer
always refreshes that local module, while external dependencies remain cached.

### Tagged releases

Push an explicit SemVer RC tag to run the full parallel release gate and publish
all 12 publications with `SHA256SUMS` to a GitHub prerelease:

```sh
git tag v1.4.1-rc.1
git push origin v1.4.1-rc.1
```

The version comes directly from the tag. Release workflows never create tags or
increment RC numbers. RCs publish to GitHub only; stable tags such as `v1.4.1`
publish to Maven Central and create a GitHub release after validation.
There is no manual release dispatch.

GitHub RC assets include JVM/native/web artifacts, sources, documentation and
Gradle metadata. They can be consumed anonymously using the flat Ivy repository
pattern in [the independent consumer](consumer/settings.gradle.kts).
