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

Short, predictable text payloads for places with strict character or length
limits: URLs, file names, Kubernetes labels, log keys.

ByteEncoding encodes raw bytes as text. PackedFormat is a compact
kotlinx.serialization BinaryFormat. EncodedFormat combines the two into short
strings. See the [technical deep dive](https://eignex.com/posts/kencode-packing-data-for-strict-limits/)
for the design.

## Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.3.0")
}
```

The formats also need the kotlinx.serialization plugin.

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

Booleans, null markers, and enum ordinals are packed into a bit header. Int and
Long fields default to varint; PackedType selects zigzag or fixed width.

```kotlin
val format = PackedFormat {
    defaultEncoding = IntPacking.FIXED
    serializersModule = myCustomModule
}
val bytes = format.encodeToByteArray(payload)
```

Fields are untagged and written in declaration order.

## EncodedFormat

A binary format, optional PayloadTransforms, and a text codec. Built-in
transforms are CompactZeros and the Crc8, Crc16, and Crc32 checksums.

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

Custom transforms: [encryption](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/EncryptionExample.kt),
[error correction](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/ErrorCorrectionExample.kt).

## Codecs

All codecs accept custom alphabets. Encoding the 13 bytes of "any byte data":

| Codec  | Output                  | Length | Alphabet         |
|--------|-------------------------|--------|------------------|
| Base62 | `2BVj6VHhfNlsGmoMQF`    | 18     | `[0-9A-Za-z]`    |
| Base36 | `0ksef5o4kvegb70nre15t` | 21     | `[0-9a-z]`       |
| Base64 | `YW55IGJ5dGUgZGF0YQ==`  | 20     | `[0-9A-Za-z+/=]` |
| Base85 | `@;^?5@X3',+Cno&@/`     | 17     | ASCII 33–117     |

BaseRadix also accepts a UnicodeRangeAlphabet. Tokens from alphabets over 256
characters encoded with 1.3.0 must be regenerated.
