# KEncode

![Maven Central](https://img.shields.io/maven-central/v/com.eignex/kencode.svg?label=Maven%20Central)
![Build](https://github.com/eignex/kencode/actions/workflows/build.yml/badge.svg)
![codecov](https://codecov.io/gh/eignex/kencode/branch/main/graph/badge.svg)
![License](https://img.shields.io/github/license/eignex/kencode)

**Compact, efficient binary–text codecs and bit-packed serialization for
Kotlin.**
Provides high-performance Base-N encoders, Base64 variants, ASCII85/Z85,
checksummed string formats, and a minimal-size binary serializer.

---

## Overview

KEncode supplies:

* Alpha-numeric radix based encoders: Base36, Base62
* Other more compact encoders: Base64, Base85 (ASCII85)
* Compact bit-packed serialization using `kotlinx.serialization`
* Optional CRC-16 / CRC-32 checksums
* Varint/varuint and zig-zag encoding
* Minimal-allocation encoding/decoding and predictable output lengths

It is intended for transferring structured payloads across
**character-restricted environments**, such as:

* URLs and URL parameters
* File names
* k8s pod names, labels, and annotations
* HTTP headers and cookies
* Message queue identifiers
* Log keys and structured logging metadata
* Any other system that requires ASCII-safe, short, reversible text encodings

If you transfer secret data you still need to encrypt your payload.

The bit-packed serialization package does not handle arbitrary structures. It's
designed for "flat" structures, so no maps or nested types. You can use
`ProtoBuf` from `kotlinx.serialization` instead of the provided `PackedFormat`
in those cases.

---

## Installation

```kotlin
implementation("com.eignex:kencode:1.0.0")
```

To use the serialization component from KEncode you also need to enable
serialization:

```kotlin
plugins {
    kotlin("plugin.serialization") version "2.2.21"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
}
```

---

## Full serialization example

Here is a full example of using the library with serialization as intended.

```kotlin
@Serializable
data class Payload(

    // VarUInt on Int/Long only uses as many bytes as needed.
    // For numbers that span close to the whole 2^32/64 there is no benefit.
    // So good for sequential ids but bad for snowflake ids.
    @VarUInt
    val id: ULong,

    // The signed version VarInt does zig-zag,
    // so that small negative values can encoded efficiently.
    @VarInt
    val delta: Int,

    // All booleans and nullable-field flags are packed into a bitset
    val urgent: Boolean,
    val sensitive: Boolean,
    val external: Boolean,
    val handledAt: Instant?,

    // This is encoded the same as VarUInt
    val type: PayloadType
)

enum class PayloadType {
    TYPE1, TYPE2, TYPE3
}

val payload = Payload(
    id = 123u,
    delta = -2,
    urgent = true,
    sensitive = false,
    external = true,
    handledAt = null,
    type = PayloadType.TYPE1
)

// This particular example fits in 4-bytes of data.

println(EncodedFormat.encodeToString(payload))
// > 0fiXYI

val decoded = EncodedFormat.decodeFromString("0fiXYI")
assert(payload == decoded)
```

## Standard encodings

You can use just the encoding implementations.

```kotlin
Base62.encode("any byte data".encodeByteArray())
``` 

## ProtoBuf serialization

## Encryption

## Checksums

* CRC-16 (default X.25)
* CRC-32 (default ISO-HDLC)

Checksum mismatch automatically throws.

---

## PackedEncoder Format explanation

* One bitmask for all booleans
* One bitmask for all nullability bits
* Sequential fixed-width primitive payloads
* Varints, varuints, zig-zag for compact integers
* Inline type support
* Minimal-length output, no nested structures

BitPackedFormat supports:

* Packed boolean bitmasks
* Packed nullability bitmasks
* Varints, varuints, zig-zag integers
* Fixed-width primitives
* Inline types (UInt, Duration, Instant)
* Flat object structures (no nested classes or collections)

## EncodedFormat explanation

## Base Encoders

* RFC-compliant Base64, URL-safe Base64
* ASCII85 with partial chunk support
* BaseN: any alphabet, deterministic chunked encoding, no padding

---

## Fun bonus: char-n-grams
