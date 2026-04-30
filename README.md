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

KEncode produces short, predictable text payloads for environments with strict
character or length limits such as URLs, file names, Kubernetes labels, and log
keys.

## Overview

KEncode has three standalone entry points. ByteEncoding is a set of text codecs
(Base62, Base36, Base64, Base85) for raw binary data.

PackedFormat is a kotlinx.serialization BinaryFormat that produces compact byte
payloads for Kotlin classes, including nested objects, lists, and maps.

EncodedFormat layers a text codec and optional payload transforms over a binary
format to produce short, deterministic string identifiers.

For a walkthrough of the bit-packing layout and design choices, see the
[technical deep dive](https://eignex.com/posts/kencode-packing-data-for-strict-limits/).

### Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.2.3")
}
```

For PackedFormat and EncodedFormat you also need to load the
kotlinx.serialization plugin and core library.

## Full serialization example

Minimal example using the default EncodedFormat (Base62 + PackedFormat):

```kotlin
@Serializable
data class Payload(
    @PackedType(IntPacking.DEFAULT) val id: ULong, // low numbers are compacted
    @PackedType(IntPacking.SIGNED) val delta: Int, // zigzagged to compact small negatives
    val urgent: Boolean,    // Packed into bitset
    val handled: Instant?,  // Nullability tracked via bitset
    val type: PayloadType
)

enum class PayloadType { TYPE1, TYPE2, TYPE3 }

val payload = Payload(123u, -2, true, null, PayloadType.TYPE1)

val encoded = EncodedFormat.encodeToString(payload)
// > 0fiXYI (that's it, this specific payload fits in 4 raw bytes)
val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
```

---

## PackedFormat

PackedFormat is a BinaryFormat for Kotlin classes that emits compact byte
payloads. Booleans and nullability markers share a single bit-header (about one
bit per field), and nested objects, lists, maps, and polymorphism are handled
recursively.

Int and Long fields can be annotated with @PackedType to choose unsigned varint
or ZigZag, and @ProtoType is recognized as a fallback.

```kotlin
val compactFormat = PackedFormat {
    // Change default from varint to fixed byte-width
    defaultEncoding = IntPacking.FIXED
    // Register custom serializers
    serializersModule = myCustomModule
}
val bytes = compactFormat.encodeToByteArray(payload)
```

---

## EncodedFormat

EncodedFormat is a StringFormat that produces short tokens by composing three
layers. The binary layer is PackedFormat by default, but ProtoBuf is a good
choice when cross-language compatibility matters.

After serialization, an optional PayloadTransform can manipulate the bytes, for
example CompactZeros to strip leading zeros, Checksum to append an integrity
check, or a custom transform for encryption or error correction. Transforms
compose with PayloadTransform.then.

Finally a text codec turns the bytes into a string, with Base62 as the default
and Base36, Base64, and Base85 available.

```kotlin
val customFormat = EncodedFormat {
    codec = Base36                  // Use Base36 instead of Base62 (for lowercase)
    checksum = Crc16                // Convenience shorthand for transform = Crc16.asTransform()
    binaryFormat = ProtoBuf         // Use ProtoBuf instead of PackedFormat
}

val token = customFormat.encodeToString(payload)

// Chain transforms: strip leading zeros, then append checksum
val withBoth = EncodedFormat {
    transform = CompactZeros.then(Crc16.asTransform())
}
```

---

## Base Encoders

KEncode ships standalone byte-to-text codecs, all of which accept custom
alphabets.

Base62 and Base36 use fixed-block encoding for predictable lengths without
padding, and produce purely alpha-numeric output (with or without upper-case).
Base85 trades alphabet size for density, encoding four bytes into five
characters. Base64 and Base64Url are RFC 4648 compatible.

Encoding `"any byte data"` (13 bytes):

| Codec  | Output                  | Length | Alphabet         |
|--------|-------------------------|--------|------------------|
| Base62 | `2BVj6VHhfNlsGmoMQF`    | 18     | `[0-9A-Za-z]`    |
| Base36 | `0ksef5o4kvegb70nre15t` | 21     | `[0-9a-z]`       |
| Base64 | `YW55IGJ5dGUgZGF0YQ==`  | 20     | `[0-9A-Za-z+/=]` |
| Base85 | `@;^?5@X3',+Cno&@/`     | 17     | ASCII 33–117     |

## Extensions

EncodedFormat can be extended by wrapping any byte transformation as a
PayloadTransform. The jvmTest source includes two worked examples: an
[encryption transform](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/EncryptionExample.kt)
built on BouncyCastle, and an
[error-correction transform](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/ErrorCorrectionExample.kt)
built on zxing that recovers from simulated byte corruption.
