<p align="center">
  <a href="https://www.eignex.com/">
    <img src="https://raw.githubusercontent.com/Eignex/.github/refs/heads/main/profile/banner.svg" style="max-width: 100%; width: 22em" />
  </a>
</p>

# KEncode

**Compact, ASCII-safe encodings and ultra-small binary serialization for Kotlin,
ideal for URLs, headers, file names, and other size-limited channels. Produces
short and predictable payloads.**

![Maven Central](https://img.shields.io/maven-central/v/com.eignex/kencode.svg?label=Maven%20Central)
![Build](https://github.com/eignex/kencode/actions/workflows/build.yml/badge.svg)
![codecov](https://codecov.io/gh/eignex/kencode/branch/main/graph/badge.svg)
![License](https://img.shields.io/github/license/eignex/kencode)

> KEncode produces short, predictable text payloads that fit into environments
> with strict character or length limits such as URLs, file names, Kubernetes
> labels, and log keys.

> It provides high-performance radix and base encoders, efficient integer
> coding, optional checksums, and a compact bit-packed serializer for flat data
> models.

---

## Overview

KEncode provides **three focused entry points**, all aimed at producing compact,
ASCII-safe representations:

1. **ByteEncoding codecs**: `Base62` / `Base36` / `Base64` / `Base85`
   Low-level encoders/decoders for `ByteArray` values.  
   Useful when you already have binary data and only need an ASCII-safe
   representation.

2. **Standalone BinaryFormat**: `PackedFormat`
   Produce compact binary payloads from Kotlin objects using
   `kotlinx.serialization` `BinaryFormat`.
   `PackedFormat` produces very small from flat structures using bitmasks,
   varints, but no object nesting. Use `kotlinx.serialization.ProtoBuf` instead
   when you need nested types, lists, or maps.

3. **Standalone StringFormat**: `EncodedFormat`
   Produce string payloads from Kotlin objects using `kotlinx.serialization`
   `StringFormat`.
   Encompasses a `BinaryFormat` + optional checksum + `ByteEncoding` text codec.
   Use when you want a single `encodeToString` / `decodeFromString` API that
   yields short, deterministic tokens.

KEncode focuses on minimal outputs; encrypt the payload first if it contains
sensitive information.

---

## Installation

```kotlin
dependencies {
    implementation("com.eignex:kencode:1.0.0")

    // For serialization support
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
}
```

You also need to load the serialization plugin.

---

## Full serialization example

Minimal example using the default `EncodedFormat` (`Base62` + `PackedFormat`):

```kotlin
@Serializable
data class Payload(
    @VarUInt
    val id: ULong,         // varint

    @VarInt
    val delta: Int,        // zig-zag + varint

    val urgent: Boolean,  // joined to bitset
    val sensitive: Boolean,
    val external: Boolean,
    val handled: Instant?, // nullable, tracked via bitset

    val type: PayloadType  // encoded as varint
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
println(encoded)
// Example: 0fiXYI (this specific payload fits in 4 raw bytes)

val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
assert(payload == decoded)
```

---

## Standard encodings

You can use the encoders standalone on raw byte arrays.

```kotlin

val bytes = "any byte data".encodeToByteArray()

println(Base36.encode(bytes))
// 0ksef5o4kvegb70nre15t

println(Base62.encode(bytes))
// 2BVj6VHhfNlsGmoMQF

println(Base64.encode(bytes))
// YW55IGJ5dGUgZGF0YQ==

println(Base85.encode(bytes))
// @;^?5@X3',+Cno&@/
```

Decoding is symmetric:

```kotlin
val back = Base62.decode("2BVj6VHhfNlsGmoMQF")
```

---

## ProtoBuf serialization

For more complex payloads (nested types, lists, maps) use `ProtoBuf` as the
binary format and still get compact, ASCII-safe strings:

```kotlin
@Serializable
data class ProtoBufRequired(val map: Map<String, Int>)

val payload = ProtoBufRequired(
    mapOf("k1" to 1285, "k2" to 9681)
)

val format = EncodedFormat(binaryFormat = ProtoBuf)

val encoded = format.encodeToString(payload)
println(encoded)
// 05cAKYGWf6gBgtZVpkqPEWOYH

val decoded = format.decodeFromString<ProtoBufRequired>(encoded)
```

This example relies on kotlinx protobuf implementation, which you install:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.9.0")
```

---

## Encryption

Typical pattern when you need confidentiality:

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
println(result)
```

---

## Checksums

You can add a CRC checksum to an `EncodedFormat`. On decode, a mismatch
throws, so you get a simple integrity check on the serialized payload.

```kotlin

@Serializable
data class Command(val id: Int, val payload: String)

val format = EncodedFormat(
    checksum = Crc32,    // or Crc16
)

val original = Command(42, "restart-worker")
val encoded = format.encodeToString(original)
println(encoded)

// Tampering will fail:
// val corrupted = encoded.dropLast(1) + "x"
// format.decodeFromString<Command>(corrupted) // throws "Checksum mismatch."

val decoded = format.decodeFromString<Command>(encoded)
```

---

## PackedFormat explanation

`PackedFormat` is a `BinaryFormat` optimized for small, flat structures:

* No nested objects, lists, or maps.
* Booleans and nullability encoded as bitmasks.
* Optional varint / zig-zag for `Int`/`Long` via annotations.

### Field layout

For a single class:

1. **Flags varlong**:

    * First `N` bits for booleans (in declaration order).
    * Next `M` bits for nullable fields (in declaration order).
    * Boolean bit = `true`/`false`.
      Nullable bit = `1` means `null`, `0` means non-null.

2. **Payload bytes** (non-boolean fields only, in declaration order):

    * Fixed-size primitives: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`.
    * `String`: `[varint length][UTF-8 bytes]`.
    * `Char`: UTF-8 encoding of a single `Char`.
    * Enum: varint ordinal.
    * Nullable fields:
        * If null: only the null-bit is set; no payload bytes.
        * If non-null: encoded exactly like the non-null case.

Top-level nullable values are encoded with one varlong flag:

* Bit 0 = `0` → non-null (value follows).
* Bit 0 = `1` → null (no payload).

### VarInt / VarUInt annotations

Varint support is opt-in to keep fixed-width behavior as default:

```kotlin
@Serializable
data class Counters(
    @VarUInt val seq: Long,  // good for monotonically increasing IDs
    @VarInt val delta: Int  // good for small positive/negative changes
)
```

Internally:

* `@VarUInt` uses an unsigned varint.
* `@VarInt` uses zig-zag + varint, so small negative numbers are compact.

### Limitations

If you need:

* Nested objects
* Lists / arrays / maps
* Polymorphism

then use `ProtoBuf` or another `BinaryFormat` with `EncodedFormat`.

---

## EncodedFormat explanation

`EncodedFormat` composes three concerns:

1. **Binary format** (`BinaryFormat`):
    * Default is `PackedFormat`.
    * Can be any `BinaryFormat` (ProtoBuf, CBOR, etc.).

2. **Checksum** (`Checksum?`):
    * Optional CRC-16 or CRC-32, or a custom implementation.
    * Appended to the binary payload and verified on decode.

3. **Text codec** (`ByteEncoding`):
    * Base62 by default, but you can swap `Base36`, `Base64`, `Base64UrlSafe`,
      `Base85`, or custom.

Typical customization:

```kotlin

@Serializable
data class Event(val id: Long, val name: String)

val format = EncodedFormat(
    codec = Base36,      // file-name friendly
    checksum = Crc16,    // short checksum
    binaryFormat = ProtoBuf
)

val token = format.encodeToString(Event(1L, "startup"))
val back = format.decodeFromString<Event>(token)
```

---

## Base encoders

KEncode does not intend to support ALL encoding variants, just the useful ones.
They are the standard Base64, compact Base85, and alphanumeric only Base36/62.
For all the implementations you can customize the alphabet if needed.

### Base64 and URL-safe Base64

* RFC 4648–compatible.
* 3 input bytes → 4 characters, with `=` padding.
* URL-safe variant (`Base64UrlSafe`) uses `-` and `_` instead of `+` and `/`.

### Base85 (ASCII85 / Z85-style)

* 4 bytes → 5 characters.
* Supports final partial group (1–3 bytes → 2–4 chars).
* No delimiters (`<~ ~>`) and no `z` compression.

### Base36 / Base62 / custom alphabets

Backed by `BaseRadix`, these encoders operate in fixed-size blocks with
deterministic lengths for safe decoding. No padding is used. A naïve
implementation without blocks is simpler but has an `O(n^2)` run time, where `n`
is the length of the bytes to encode.
