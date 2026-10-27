package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule

/**
 * @property codec Text codec for the serialized bytes.
 * @property transform Optional transform between [binaryFormat] and [codec].
 * @property binaryFormat Format that serializes values to bytes.
 */
data class EncodedConfiguration(
    val codec: ByteEncoding = Base62,
    val transform: PayloadTransform? = null,
    val binaryFormat: BinaryFormat = PackedFormat.Default,
)

/**
 * [StringFormat] that serializes with a [BinaryFormat], applies an optional [PayloadTransform],
 * and encodes the bytes with a [ByteEncoding]. Defaults to [PackedFormat] and [Base62].
 *
 * @property configuration Codec, transform, and binary format.
 */
@OptIn(ExperimentalSerializationApi::class)
open class EncodedFormat(val configuration: EncodedConfiguration) : StringFormat {

    constructor(
        codec: ByteEncoding = Base62,
        transform: PayloadTransform? = null,
        binaryFormat: BinaryFormat = PackedFormat,
    ) : this(EncodedConfiguration(codec, transform, binaryFormat))

    override val serializersModule: SerializersModule get() = configuration.binaryFormat.serializersModule

    /** [PackedFormat] and [Base62] with no transform. */
    companion object Default : EncodedFormat()

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val bytes =
            configuration.binaryFormat.encodeToByteArray(serializer, value)
        val payload = configuration.transform?.encode(bytes) ?: bytes
        return configuration.codec.encode(payload)
    }

    /** @throws IllegalArgumentException if any layer rejects the input, e.g. a checksum mismatch. */
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val input = configuration.codec.decode(string)
        val bytes = configuration.transform?.decode(input) ?: input
        return configuration.binaryFormat.decodeFromByteArray(
            deserializer,
            bytes,
        )
    }
}

/** Builder for [EncodedFormat]. */
class EncodedFormatBuilder {
    /** Text codec for the serialized bytes. */
    var codec: ByteEncoding = Base62

    /** Optional transform between [binaryFormat] and [codec]. */
    var transform: PayloadTransform? = null

    /** Format that serializes values to bytes. */
    var binaryFormat: BinaryFormat = PackedFormat.Default

    /**
     * Appends the checksum after the current [transform]. Setting `null` does nothing;
     * reads always return `null`.
     */
    var checksum: Checksum?
        get() = null
        set(value) {
            if (value == null) return
            val checksumTransform = value.asTransform()
            transform = transform?.then(checksumTransform) ?: checksumTransform
        }
}

/**
 * Creates an [EncodedFormat].
 *
 * ```
 * val format = EncodedFormat {
 *     codec = Base36
 *     transform = CompactZeros.then(Crc16.asTransform())
 * }
 * ```
 *
 * @param from Instance to copy settings from.
 * @param builderAction Configuration block.
 */
fun EncodedFormat(
    from: EncodedFormat = EncodedFormat.Default,
    builderAction: EncodedFormatBuilder.() -> Unit,
): EncodedFormat {
    val builder = EncodedFormatBuilder().apply {
        codec = from.configuration.codec
        transform = from.configuration.transform
        binaryFormat = from.configuration.binaryFormat
    }
    builder.builderAction()
    return EncodedFormat(
        EncodedConfiguration(
            builder.codec,
            builder.transform,
            builder.binaryFormat,
        ),
    )
}
