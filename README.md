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

[![Maven Central](https://img.shields.io/maven-central/v/com.eignex/kencode.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.eignex/kencode/1.1.0)
[![Build](https://github.com/eignex/kencode/actions/workflows/build.yml/badge.svg)](https://github.com/eignex/kencode/actions/workflows/build.yml)
[![codecov](https://codecov.io/gh/eignex/kencode/branch/main/graph/badge.svg)](https://codecov.io/gh/eignex/kencode)
[![License](https://img.shields.io/github/license/eignex/kencode)](https://github.com/eignex/kencode/blob/main/LICENSE)

KEncode produces short, predictable text payloads for environments with strict
character or length limits such as URLs, file names, Kubernetes labels, and log
keys.

---

## Overview

KEncode provides three standalone entry points:

1. **ByteEncoding** text codecs: Base62, Base36, Base64, and Base85 encoders
   for raw binary data.
2. **PackedFormat**: A binary format serializer that supports nested objects,
   lists, and maps. It uses bitsets for booleans and nullability to minimize
   overhead.
3. **EncodedFormat**: A string format serializer that wraps the above to produce
   small deterministic string identifiers.

### Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.0.0")
}
```

For PackedFormat and EncodedFormat you also need to load the
`kotlinx.serialization` plugin and core library.

## Full serialization example

Minimal example using the default `EncodedFormat` (Base62 + PackedFormat):

```kotlin
@Serializable
data class Payload(
    @VarUInt val id: ULong, // low numbers are compacted
    @VarInt val delta: Int, // low numbers are compacted and zigzagged to improve negative numbers
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

PackedFormat is a BinaryFormat designed to produce the smallest feasible
payloads for Kotlin classes by moving structural metadata into a compact header.

* Bit-Packing: Booleans and nullability markers are stored in a single
  bit-header (about 1 bit per field).
* VarInts: Int/Long fields can be optimized using @VarUInt or @VarInt (ZigZag)
  annotations.
* Full Graph Support: Handles nested objects, lists, maps, and polymorphism
  recursively. While this is supported it will not produce as compact
  representations as flat structures that can pack all metadata into the same
  header.

```kotlin
val compactFormat = PackedFormat {
    defaultVarInt = true    // All Int/Long use VarInt by default
    defaultZigZag = true    // All Int/Long use ZigZag by default
    serializersModule = myCustomModule
}
val bytes = PackedFormat.encodeToBytes(payload)
```

---

## EncodedFormat

EncodedFormat provides a StringFormat API that produces short tokens by
composing three layers:

1. Binary Layer: PackedFormat (default) or ProtoBuf (recommended for
   cross-language compatibility).
2. Checksum Layer: Optional Crc16 or Crc32 appended to the binary payload.
3. Text Layer: Base62 (default), Base36, Base64, or Base85.

```kotlin
val customFormat = EncodedFormat {
    codec = Base36          // Use Base36 instead of Base62 (for lowercase)
    checksum = Crc16        // Append a 2-byte checksum
    binaryFormat = ProtoBuf // Use ProtoBuf instead of PackedFormat (or the compactFormat from previous example)
}

val token = customFormat.encodeToString(payload)
```

---

## Base Encoders

KEncode includes standalone codecs for byte-to-text conversion. All
implementations support custom alphabets.

* Base62 / Base36: Uses fixed-block encoding for predictable lengths without
  padding. Main use is to have 100% alpha-numeric output, with or without
  upper-case.
* Base85: High-density encoding (4 bytes to 5 characters).
* Base64 / Base64UrlSafe: RFC 4648 compatible.

---

## Encryption

Typical confidential payload pattern:

```kotlin
@Serializable
data class SecretPayload(val id: Long)

// 1. Serialize
val binary = PackedFormat.encodeToByteArray(payload)

// 2. Encrypt (e.g., AES or XTEA)
val encrypted = cipher.doFinal(binary)

// 3. Encode to Text
val token = Base62.encode(encrypted)
```

See [Examples](https://github.com/Eignex/kencode/blob/main/src/test/kotlin/com/eignex/kencode/Examples.kt)
for a BouncyCastle demo.
