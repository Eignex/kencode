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

There are three entry points. ByteEncoding is a set of text codecs for raw
binary data: Base62, Base36, Base64, Base85.

PackedFormat is a kotlinx.serialization BinaryFormat that emits compact byte
payloads for Kotlin classes, with full support for nested objects, lists, and
maps. EncodedFormat then layers a text codec and optional payload transforms
on top, producing short, deterministic string identifiers.

For the design rationale and a walkthrough of the bit-packing layout, see the
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

PackedFormat is a BinaryFormat aimed at the smallest feasible byte output.
Booleans and nullability markers share a single bit-header, costing about one
bit per field. Nested objects, lists, maps, and polymorphism all work
recursively.

Int and Long fields take a @PackedType annotation to opt into unsigned varint
or ZigZag. @ProtoType works as a fallback.

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

EncodedFormat is a StringFormat built from three composable layers.

The first is binary: PackedFormat by default, or ProtoBuf when cross-language
compatibility matters. The second is an optional PayloadTransform that
manipulates the bytes after serialization. CompactZeros strips leading zeros,
Checksum appends an integrity check, and custom transforms cover encryption or
error correction; chain them with PayloadTransform.then.

A text codec finishes the job. Base62 is the default; Base36, Base64, and
Base85 are also available.

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

All four codecs are usable on their own and accept custom alphabets.

Base62 and Base36 use fixed-block encoding for predictable, unpadded output in
a strictly alpha-numeric alphabet (with or without upper-case). Base85 is
denser: four bytes in, five characters out. Base64 and Base64Url are RFC 4648
compatible.

Encoding `"any byte data"` (13 bytes):

| Codec  | Output                  | Length | Alphabet         |
|--------|-------------------------|--------|------------------|
| Base62 | `2BVj6VHhfNlsGmoMQF`    | 18     | `[0-9A-Za-z]`    |
| Base36 | `0ksef5o4kvegb70nre15t` | 21     | `[0-9a-z]`       |
| Base64 | `YW55IGJ5dGUgZGF0YQ==`  | 20     | `[0-9A-Za-z+/=]` |
| Base85 | `@;^?5@X3',+Cno&@/`     | 17     | ASCII 33–117     |

## Extensions

Any byte transformation can be wrapped as a PayloadTransform. Two worked
examples live in the jvmTest source: an
[encryption transform](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/EncryptionExample.kt)
built on BouncyCastle, and an
[error-correction transform](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/ErrorCorrectionExample.kt)
on zxing that recovers from simulated byte corruption.
