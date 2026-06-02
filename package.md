# Module kencode

# Package com.eignex.kencode

Start here to find your way around the API. Three entry points cover the
common tasks; everything else in the symbol list supports them.

| Start with | When you want to |
|---|---|
| [EncodedFormat] | Turn a `@Serializable` type into a short string, and back. |
| [PackedFormat] | Get the compact `ByteArray` without the text layer. |
| [ByteEncoding] | Encode raw bytes with a codec directly &mdash; [Base62], [Base36], [Base64], [Base85]. |

```kotlin
val encoded = EncodedFormat.encodeToString(payload)
val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
```

Configure a format through its builder lambda: swap the [codec][EncodedFormatBuilder.codec],
add a [checksum][EncodedFormatBuilder.checksum], or chain [PayloadTransform]s.
On the packed side, annotate `Int`/`Long` fields with [PackedType] and
[IntPacking] to opt into varint or ZigZag.
