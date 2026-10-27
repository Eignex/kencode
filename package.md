# Module kencode

# Package com.eignex.kencode

| Entry point | Use it to |
|---|---|
| [EncodedFormat] | Encode a `@Serializable` value as a short string and back. |
| [PackedFormat] | Get the compact `ByteArray` without the text layer. |
| [ByteEncoding] | Encode raw bytes with [Base62], [Base36], [Base64], or [Base85]. |

```kotlin
val encoded = EncodedFormat.encodeToString(payload)
val decoded = EncodedFormat.decodeFromString<Payload>(encoded)
```

Configure formats with the builder lambda: set the [codec][EncodedFormatBuilder.codec],
add a [checksum][EncodedFormatBuilder.checksum], or chain [PayloadTransform]s.
Annotate `Int`/`Long` fields with [PackedType] to choose an [IntPacking].
