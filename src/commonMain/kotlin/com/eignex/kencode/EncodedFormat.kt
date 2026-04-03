package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule

/**
 * Holds the configuration for an [EncodedFormat] instance.
 *
 * @property codec The ASCII-safe byte codec used to turn raw bytes into text (e.g., Base62, Base64).
 * @property transform An optional [PayloadTransform] applied after serialization and before encoding.
 *   Common uses: [CompactZeros] to strip leading zeros, integrity checks via [Checksum.asTransform],
 *   encryption, or error-correcting codes. Use [PayloadTransform.then] to chain multiple transforms.
 * @property binaryFormat The underlying binary serialization format used before encoding to text.
 */
data class EncodedConfiguration(
    val codec: ByteEncoding = Base62,
    val transform: PayloadTransform? = null,
    val binaryFormat: BinaryFormat = PackedFormat.Default,
)

/**
 * Text `StringFormat` that produces short, predictable string tokens by composing:
 *
 * 1. A binary format (e.g. [PackedFormat], `ProtoBuf`).
 * 2. An optional [PayloadTransform] ([CompactZeros], checksum, encryption, ECC, …).
 * 3. An ASCII-safe byte encoding (e.g. [Base62], [Base64], [Base36], [Base85]).
 *
 * Typical use:
 * - `encodeToString`: serialize -> transform.encode -> encode bytes to text.
 * - `decodeFromString`: decode text to bytes -> transform.decode -> deserialize.
 *
 * Use the [EncodedFormat] builder function to create a customized instance.
 *
 * @property configuration The active configuration dictating the codec, transform, and binary format.
 */
@OptIn(ExperimentalSerializationApi::class)
open class EncodedFormat(
    val configuration: EncodedConfiguration,
) : StringFormat {

    /**
     * Secondary constructor for direct instantiation without the builder.
     */
    constructor(
        codec: ByteEncoding = Base62,
        transform: PayloadTransform? = null,
        binaryFormat: BinaryFormat = PackedFormat,
    ) : this(EncodedConfiguration(codec, transform, binaryFormat))

    /**
     * Delegates to the underlying [BinaryFormat]'s serializers module.
     */
    override val serializersModule: SerializersModule get() = configuration.binaryFormat.serializersModule

    /**
     * Default format: `PackedFormat` + `Base62`, no transform.
     */
    companion object Default : EncodedFormat()

    /**
     * Serializes [value] with the configured binary format, applies the transform,
     * and encodes the resulting byte array using the text codec.
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val bytes = configuration.binaryFormat.encodeToByteArray(serializer, value)
        val payload = configuration.transform?.encode(bytes) ?: bytes
        return configuration.codec.encode(payload)
    }

    /**
     * Decodes [string] using the text codec, applies the inverse transform,
     * then deserializes with the configured binary format.
     *
     * @throws IllegalArgumentException if the transform's decode step fails (e.g. checksum mismatch).
     */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val input = configuration.codec.decode(string)
        val bytes = configuration.transform?.decode(input) ?: input
        return configuration.binaryFormat.decodeFromByteArray(deserializer, bytes)
    }
}

/**
 * Builder for configuring [EncodedFormat] instances.
 */
class EncodedFormatBuilder {
    var codec: ByteEncoding = Base62
    var transform: PayloadTransform? = null
    var binaryFormat: BinaryFormat = PackedFormat.Default

    var checksum: Checksum?
        get() = null
        set(value) { transform = value?.asTransform() }
}

/**
 * Creates a customized [EncodedFormat] instance.
 *
 * ```
 * val format = EncodedFormat {
 *     codec = Base36
 *     transform = CompactZeros.then(Crc16.asTransform())
 * }
 * ```
 *
 * @param from An existing [EncodedFormat] instance to copy defaults from.
 * @param builderAction The configuration block.
 */
fun EncodedFormat(
    from: EncodedFormat = EncodedFormat.Default,
    builderAction: EncodedFormatBuilder.() -> Unit
): EncodedFormat {
    val builder = EncodedFormatBuilder().apply {
        codec = from.configuration.codec
        transform = from.configuration.transform
        binaryFormat = from.configuration.binaryFormat
    }
    builder.builderAction()
    return EncodedFormat(EncodedConfiguration(
        builder.codec,
        builder.transform,
        builder.binaryFormat,
    ))
}
