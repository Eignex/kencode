<p align="center">
  <a href="https://www.eignex.com/">
    <img src="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner.svg" style="max-width: 100%; width: 22em" />
  </a>
</p>

# KEncode

**High-efficiency binary/text codecs with compact bit-packed serialization for
Kotlin.**

![Maven Central](https://img.shields.io/maven-central/v/com.eignex/kencode.svg?label=Maven%20Central)
![Build](https://github.com/eignex/kencode/actions/workflows/build.yml/badge.svg)
![codecov](https://codecov.io/gh/eignex/kencode/branch/main/graph/badge.svg)
![License](https://img.shields.io/github/license/eignex/kencode)

> KEncode produces short, predictable text payloads that fit into environments
> with strict character or length limits such as URLs, file names, Kubernetes
> labels, and log keys.

> It provides compact radix/base encoders, efficient integer coding, optional
> checksums, and a bit-packed serializer for flat data models.

---

## Overview

KEncode has three focused entry points, all aimed at compact, ASCII-safe
representations:

1. ByteEncoding codecs: `Base62` / `Base36` / `Base64` / `Base85`  
   Low-level encoders/decoders for byte arrays when you already have binary
   data.

2. Standalone BinaryFormat: `PackedFormat`  
   A compact binary serializer optimized for Kotlin. It supports arbitrary
   object graphs, including nested objects, lists, and maps. It uses
   per-object bitsets for booleans and nullability, and varint encodings for
   integers to keep the layout significantly smaller than standard formats.

3. Standalone StringFormat: `EncodedFormat`  
   A wrapper that applies a binary format, optionally appends a checksum, and
   then encodes the final byte sequence using a chosen `ByteEncoding`. This
   produces short, deterministic string representations suitable for external
   identifiers.

### Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.0.0")
}
```

For PackedFormat and EncodedFormat you also need you also need to load the
serialization plugin.

### Full serialization example

Minimal example using the default `EncodedFormat` (`Base62` + `PackedFormat`):

```kotlin
@Serializable
data class Payload(
    @VarUInt val id: ULong, // varint
    @VarInt val delta: Int, // zig-zag + varint
    val urgent: Boolean,    // joined to bitset
    val sensitive: Boolean,
    val external: Boolean,
    val handled: Instant?,  // nullable, tracked via bitset

    val type: PayloadType   // encoded as varint
)

enum class PayloadType { TYPE1, TYPE2, TYPE3 }

val payload = Payload(
    id = 123u,
    delta = -2,
    urgent = true,
    sensitive = false,
    external = true,
    handled = null,
    type = PayloadType.TYPE1
)

val encoded = EncodedFormat.encodeToString(payload)
// Example: 0fiXYI (this specific payload fits in 4 raw bytes)
val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
```

---

## Standard encodings

You can use the encoders standalone on raw byte arrays.

```kotlin
val bytes = "any byte data".encodeToByteArray()
println(Base36.encode(bytes)) // 0ksef5o4kvegb70nre15t
println(Base62.encode(bytes)) // 2BVj6VHhfNlsGmoMQF
println(Base64.encode(bytes)) // YW55IGJ5dGUgZGF0YQ==
println(Base85.encode(bytes)) // @;^?5@X3',+Cno&@/

// Decoding is symmetric:
val back = Base62.decode("2BVj6VHhfNlsGmoMQF")
```

---

## ProtoBuf serialization

`PackedFormat` is optimized for Kotlin-to-Kotlin scenarios. If you need
cross-language compatibility (consuming the payload in non-JVM languages) or
require standard Protocol Buffer schema evolution, you can swap the binary
format for `ProtoBuf`:

```kotlin
@Serializable
data class ProtoBufRequired(val map: Map<String, Int>)

val payload = ProtoBufRequired(mapOf("k1" to 1285, "k2" to 9681))
val format = EncodedFormat(binaryFormat = ProtoBuf)
val encoded = format.encodeToString(payload) // 05cAKYGWf6gBgtZVpkqPEWOYH
val decoded = format.decodeFromString<ProtoBufRequired>(encoded)
```

This example relies on kotlinx protobuf, which you install like so:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0")
```

---

## Encryption

Typical confidential payload pattern:

1. Serialize (PackedFormat or ProtoBuf).
2. Encrypt with your crypto library.
3. Encode ciphertext using Base62/Base64/etc.

This is an example with a stream cipher from Bouncy Castle.

```kotlin
@Serializable
data class SecretPayload(val id: Long)

// Initialization
Security.addProvider(BouncyCastleProvider())
val random = SecureRandom()

// This key is stored permanently so we can read payloads after jvm restart
val keyBytes = ByteArray(16)
random.nextBytes(keyBytes)
val key = SecretKeySpec(keyBytes, "XTEA")
val cipher = Cipher.getInstance("XTEA/CTR/NoPadding", "BC")

// Encrypt one payload
// we use 8 bytes as the initialization vector
val payload = SensitiveData(random.nextLong())
val iv8 = ByteArray(8)
random.nextBytes(iv8)
cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv8))

// This particular encryption adds 16-bytes in an initialization vector
// regardless of how big the payload is.
val encrypted =
    iv8 + cipher.doFinal(PackedFormat.encodeToByteArray(payload))
val encoded = Base62.encode(encrypted)
println(encoded)

// This recovers the initial payload
val iv8received = encrypted.copyOfRange(0, 8)
val received = encrypted.copyOfRange(8, encrypted.size)
cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv8received))
val decoded = Base62.decode(encoded)
val decrypted = cipher.doFinal(received)
val result: SensitiveData = PackedFormat.decodeFromByteArray(decrypted)
```

---

## Checksums

You can add a CRC checksum to an `EncodedFormat`, this is built in. On decode, a
mismatch throws, so you get a simple integrity check on the serialized payload.

```kotlin
@Serializable
data class Command(val id: Int, val payload: String)

val format = EncodedFormat(checksum = Crc32)
val encoded = format.encodeToString(Command(42, "restart-worker"))
val decoded = format.decodeFromString<Command>(encoded)
```

---

## PackedFormat explanation

`PackedFormat` is a `BinaryFormat` designed to produce the smallest feasible
payloads for Kotlin classes. Unlike JSON or standard ProtoBuf, it uses a
state-aware bit-packing strategy to merge boolean flags and nullability
indicators.

### Capabilities

* Full Graph Support: Handles nested objects, lists, maps, and polymorphic
  types.
* Bit-Packing: For every class in the hierarchy, all `Boolean` fields and
  `Nullable` indicators are packed into a single varlong header. For very or 
  dynamic payloads a
* VarInts: Integers can be encoded as VarInts via annotation (or ZigZag).

### Field layout

For a standard class, the encoding follows this structure:

1. Bitmask Header A single varlong containing:
    * Boolean bits — one per boolean property in the specific class.
    * Nullability bits — one per nullable property.
   *(This ensures that a class with 10 booleans and 5 nullable fields only uses ~2 bytes of overhead).*

2. Payload bytes After the flags, fields are encoded in declaration order:
    * Primitives: Encoded densely (VarInt for Int/Long, fixed for others).
    * Strings**: `[varint length][UTF-8 bytes]`.
    * Nested Objects: Recursively encodes the child object (starting with its own Bitmask Header).
    * Collections: `[varint size][item 1][item 2]...` (Collections do not use bitmasks; nulls in lists use inline markers).

### VarInt / VarUInt annotations

You can further optimize integer fields using annotations:

```kotlin
@Serializable
data class Counters(
    @VarUInt val seq: Long, // unsigned varint
    @VarInt val delta: Int  // zig-zag + varint (good for small negative numbers)
)
```

---

## EncodedFormat explanation

`EncodedFormat` provides a single `StringFormat` API that produces short,
ASCII-safe tokens by composing three layers:

1. Binary format  
   Default is `PackedFormat`, but any `BinaryFormat` (e.g. ProtoBuf) can be
   used.

2. Checksum (optional)  
   Supports `Crc16`, `Crc32`, or a custom implementation.  
   The checksum is appended to the binary payload and verified during decode.

3. Text codec  
   Converts the final bytes into a compact ASCII representation.  
   Default is `Base62`, with alternatives such as `Base36`, `Base64`,
   `Base64UrlSafe`, `Base85`, or custom alphabets.

This makes it easy to generate stable, compact identifiers suitable for URLs,
headers, filenames, cookies, and system labels.

```kotlin
@Serializable
data class Event(val id: Long, val name: String)

val format = EncodedFormat(
    codec = Base36,
    checksum = Crc16,
    binaryFormat = ProtoBuf
)

val token = format.encodeToString(Event(1L, "startup"))
val back = format.decodeFromString<Event>(token)
```

---

## Base encoders

KEncode includes a focused set of practical ASCII-safe encoders: `Base36`,
`Base62`, `Base64`, and `Base85`. All implementations allow custom alphabets.

### Base64 and URL-safe Base64

* RFC 4648–compatible.
* 3 bytes → 4 characters (`=` padding).
* URL-safe variant substitutes `-` and `_`.

### Base85

* 4 bytes → 5 characters.
* Supports partial final groups (1–3 bytes).
* No delimiters or `z` compression.

### Base36 / Base62 / custom alphabets

Built on `BaseRadix`, these encoders use fixed-size blocks for predictable
lengths and safe decoding, without padding. Custom alphabets are supported.
