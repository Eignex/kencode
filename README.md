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

- **ByteEncoding**: text codecs for raw bytes (Base62, Base36, Base64, Base85).
- **PackedFormat**: a kotlinx.serialization `BinaryFormat` with compact output.
- **EncodedFormat**: PackedFormat plus optional transforms and a text codec,
  producing short, deterministic strings.

The [technical deep dive](https://eignex.com/posts/kencode-packing-data-for-strict-limits/)
covers the design and bit-packing layout.

## Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.3.0")
}
```

PackedFormat and EncodedFormat also need the kotlinx.serialization plugin and
core library.

## Example

```kotlin
@Serializable
data class Payload(
    @PackedType(IntPacking.DEFAULT) val id: ULong, // varint: small numbers stay small
    @PackedType(IntPacking.SIGNED) val delta: Int, // zigzag: small negatives stay small
    val urgent: Boolean,    // one bit in the header
    val handled: Instant?,  // null marker is one bit in the header
    val type: PayloadType,  // ordinal bits in the header
)

enum class PayloadType { TYPE1, TYPE2, TYPE3 }

val payload = Payload(123u, -2, true, null, PayloadType.TYPE1)
val encoded = EncodedFormat.encodeToString(payload) // "00Xl1", 3 raw bytes
val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
```

## PackedFormat

Booleans, null markers, and enum ordinals share one bit header, so each costs
a bit or two. Nested objects, lists, maps, and polymorphism are supported.
`Int` and `Long` fields default to varint; `@PackedType` (or `@ProtoType` as a
fallback) selects varint, zigzag, or fixed width per field.

```kotlin
val format = PackedFormat {
    defaultEncoding = IntPacking.FIXED // default for unannotated Int/Long
    serializersModule = myCustomModule
}
val bytes = format.encodeToByteArray(payload)
```

The layout has no field tags: every element is written in declaration order,
so encoding fails when `@EncodeDefault(NEVER)` would skip a field. Decoding rejects
malformed or non-canonical input with `IllegalArgumentException`.

## EncodedFormat

Three layers: a binary format (PackedFormat, or ProtoBuf for cross-language
use), an optional `PayloadTransform`, and a text codec (Base62 by default).
Built-in transforms are `CompactZeros` (strips leading zeros) and checksums
(`Crc8`, `Crc16`, `Crc32`); chain them with `then`.

```kotlin
val format = EncodedFormat {
    codec = Base36           // lowercase only
    checksum = Crc16         // appended after any transform already set
    binaryFormat = ProtoBuf
}

val compacted = EncodedFormat {
    transform = CompactZeros.then(Crc16.asTransform())
}
```

Any byte transformation can be a `PayloadTransform`. The jvmTest sources have an
[encryption example](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/EncryptionExample.kt)
(BouncyCastle) and an
[error-correction example](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/ErrorCorrectionExample.kt)
(zxing) that recovers corrupted bytes.

## Codecs

Each codec works standalone and accepts a custom alphabet. Base62 and Base36
use fixed-size blocks for unpadded alphanumeric output. Base85 maps 4 bytes to
5 characters. Base64 and Base64Url follow RFC 4648.

Encoding `"any byte data"` (13 bytes):

| Codec  | Output                  | Length | Alphabet         |
|--------|-------------------------|--------|------------------|
| Base62 | `2BVj6VHhfNlsGmoMQF`    | 18     | `[0-9A-Za-z]`    |
| Base36 | `0ksef5o4kvegb70nre15t` | 21     | `[0-9a-z]`       |
| Base64 | `YW55IGJ5dGUgZGF0YQ==`  | 20     | `[0-9A-Za-z+/=]` |
| Base85 | `@;^?5@X3',+Cno&@/`     | 17     | ASCII 33–117     |

### Large alphabets

`BaseRadix(UnicodeRangeAlphabet())` uses BMP characters outside the surrogate
block; ranges must end at or before U+FFFF. Alphabets over 256 characters
prefix partial blocks with a sentinel byte to keep the exact byte count. This
large-alphabet format differs from 1.3.0, so tokens encoded with 1.3.0 must be
regenerated. Base36 and Base62 output is unchanged.
