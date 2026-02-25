package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule

/**
 * Holds the configuration for an [EncodedFormat] instance.
 *
 * @property codec The ASCII-safe byte codec used to turn raw bytes into text (e.g., Base62, Base64).
 * @property checksum An optional checksum appended to the binary payload and verified upon decoding.
 * @property binaryFormat The underlying binary serialization format used before encoding to text.
 */
data class EncodedConfiguration(
    val codec: ByteEncoding = Base62,
    val checksum: Checksum? = null,
    val binaryFormat: BinaryFormat = PackedFormat.Default
)

/**
 * Text `StringFormat` that produces short, predictable string tokens by composing:
 *
 * 1. A binary format (e.g. [PackedFormat], `ProtoBuf`).
 * 2. An optional checksum.
 * 3. An ASCII-safe byte encoding (e.g. [Base62], [Base64], [Base36], [Base85]).
 *
 * Typical use:
 * - `encodeToString`: serialize -> (optionally) append checksum -> encode bytes to text.
 * - `decodeFromString`: decode text to bytes -> (optionally) verify checksum -> deserialize.
 *
 * Use the [EncodedFormat] builder function to create a customized instance.
 *
 * @property configuration The active configuration dictating the codec, checksum, and binary format.
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
        checksum: Checksum? = null,
        binaryFormat: BinaryFormat = PackedFormat,
    ) : this(EncodedConfiguration(codec, checksum, binaryFormat))

    /**
     * Delegates to the underlying [BinaryFormat]'s serializers module.
     */
    override val serializersModule: SerializersModule get() = configuration.binaryFormat.serializersModule

    /**
     * Default format: `PackedFormat` + `Base62` without a checksum.
     */
    companion object Default : EncodedFormat()

    /**
     * Serializes [value] with the configured binary format, optionally appends a checksum,
     * and encodes the resulting byte array using the text codec.
     */
    override fun <T> encodeToString(
        serializer: SerializationStrategy<T>, value: T
    ): String {
        val bytes = configuration.binaryFormat.encodeToByteArray(serializer, value)
        val checked = if (configuration.checksum != null) {
            bytes + configuration.checksum.digest(bytes)
        } else bytes
        return configuration.codec.encode(checked)
    }

    /**
     * Decodes [string] using the text codec, optionally verifies and strips the checksum,
     * then deserializes the remaining bytes with the configured binary format.
     *
     * @throws IllegalArgumentException if a checksum is configured and the verification fails.
     */
    override fun <T> decodeFromString(
        deserializer: DeserializationStrategy<T>, string: String
    ): T {
        val input = configuration.codec.decode(string)
        val bytes = if (configuration.checksum != null) {
            require(input.size >= configuration.checksum.size)
            val bytes = input.sliceArray(0..<input.size - configuration.checksum.size)
            val actual =
                input.sliceArray(input.size - configuration.checksum.size..<input.size)
            val expected = configuration.checksum.digest(bytes)
            require(actual.contentEquals(expected)) {
                "Checksum mismatch."
            }
            bytes
        } else input
        return configuration.binaryFormat.decodeFromByteArray(deserializer, bytes)
    }
}

/**
 * Builder for configuring [EncodedFormat] instances.
 */
class EncodedFormatBuilder {
    /**
     * The ASCII-safe byte codec used to turn raw bytes into text. Defaults to [Base62].
     */
    var codec: ByteEncoding = Base62

    /**
     * An optional checksum appended to the binary payload. Defaults to `null`.
     */
    var checksum: Checksum? = null

    /**
     * The underlying binary serialization format. Defaults to [PackedFormat.Default].
     */
    var binaryFormat: BinaryFormat = PackedFormat.Default
}

/**
 * Creates a customized [EncodedFormat] instance.
 *
 * ```
 * val format = EncodedFormat {
 * codec = Base36
 * checksum = Crc16
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
        checksum = from.configuration.checksum
        binaryFormat = from.configuration.binaryFormat
    }
    builder.builderAction()

    val newConfig = EncodedConfiguration(builder.codec, builder.checksum, builder.binaryFormat)

    return EncodedFormat(newConfig)
}
