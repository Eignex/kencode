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
    implementation("com.eignex:kencode:1.3.0")
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
characters. If you used an alphabet with more than 256 characters in 1.3.0,
you'll need to encode those strings again, since the format has changed.
