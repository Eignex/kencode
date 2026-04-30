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

For a walkthrough of the bit-packing layout and design choices, see the
[technical deep dive](https://eignex.com/posts/kencode-packing-data-for-strict-limits/).

### Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.2.3")
}
```

For PackedFormat and EncodedFormat you also need to load the
`kotlinx.serialization` plugin and core library.

## Full serialization example

Minimal example using the default `EncodedFormat` (Base62 + PackedFormat):

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

PackedFormat is a `BinaryFormat` for Kotlin classes that emits compact byte
payloads.

* Bit-Packing: Booleans and nullability markers share a single bit-header
  (~1 bit per field).
* VarInts: annotate `Int`/`Long` fields with `@PackedType(IntPacking.DEFAULT)`
  (unsigned varint) or `@PackedType(IntPacking.SIGNED)` (ZigZag). `@ProtoType`
  is recognized as a fallback.
* Full Graph Support: nested objects, lists, maps, and polymorphism are
  handled recursively.

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

EncodedFormat provides a StringFormat API that produces short tokens by
composing three layers:

1. Binary Layer: PackedFormat (default) or ProtoBuf (recommended for
   cross-language compatibility).
2. Transform Layer: Optional `PayloadTransform` applied after serialization
   (e.g. `CompactZeros`, `Checksum.asTransform()`, or a custom transform for
   encryption or ECC). Chain transforms with `PayloadTransform.then`.
3. Text Layer: Base62 (default), Base36, Base64, or Base85.

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

KEncode includes standalone codecs for byte-to-text conversion. All
implementations support custom alphabets.

* Base62 / Base36: fixed-block encoding with predictable lengths and no
  padding; alpha-numeric output, with or without upper-case.
* Base85: high-density encoding (4 bytes → 5 characters).
* Base64 / Base64Url: RFC 4648 compatible.

Encoding `"any byte data"` (13 bytes):

| Codec  | Output                  | Length | Alphabet         |
|--------|-------------------------|--------|------------------|
| Base62 | `2BVj6VHhfNlsGmoMQF`    | 18     | `[0-9A-Za-z]`    |
| Base36 | `0ksef5o4kvegb70nre15t` | 21     | `[0-9a-z]`       |
| Base64 | `YW55IGJ5dGUgZGF0YQ==`  | 20     | `[0-9A-Za-z+/=]` |
| Base85 | `@;^?5@X3',+Cno&@/`     | 17     | ASCII 33–117     |

---

## Extensions

There are examples in the jvmTest source of how to extend the encoding with encryption or error correction.

### Encryption

Wrap a cipher as a `PayloadTransform` and pass it to `EncodedFormat`:

```kotlin
@Serializable
data class SecretPayload(val id: Long)

val encryptingTransform = object : PayloadTransform {
    override fun encode(data: ByteArray): ByteArray = cipher.encrypt(data)
    override fun decode(data: ByteArray): ByteArray = cipher.decrypt(data)
}

val secureFormat = EncodedFormat {
    transform = encryptingTransform
}

val token = secureFormat.encodeToString(SecretPayload.serializer(), payload)
val decoded = secureFormat.decodeFromString(SecretPayload.serializer(), token)
```

See [EncryptionExample](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/EncryptionExample.kt)
for the full example using BouncyCastle.

### Error Correction

Wrap an error-correcting code as a `PayloadTransform` to recover from corrupted bytes:

```kotlin
val eccTransform = object : PayloadTransform {
    override fun encode(data: ByteArray): ByteArray = ecc.encode(data)
    override fun decode(data: ByteArray): ByteArray = ecc.decode(data)
}

val robustFormat = EncodedFormat {
    transform = eccTransform
}

val token = robustFormat.encodeToString(SecretPayload.serializer(), payload)
val decoded = robustFormat.decodeFromString(SecretPayload.serializer(), token)
```

See [ErrorCorrectionExample](https://github.com/Eignex/kencode/blob/main/src/jvmTest/kotlin/com/eignex/kencode/ErrorCorrectionExample.kt)
for the full example using zxing and simulated byte corruption.
