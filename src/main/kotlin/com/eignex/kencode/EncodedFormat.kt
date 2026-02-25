package com.eignex.kencode

import PackedFormat
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule

data class EncodedConfiguration(
    val codec: ByteEncoding = Base62,
    val checksum: Checksum? = null,
    val binaryFormat: BinaryFormat = PackedFormat.Default
)

/**
 * Text `StringFormat` that combines:
 *
 * - A binary format (e.g. [PackedFormat], `ProtoBuf`)
 * - An optional checksum
 * - An ASCII-safe byte encoding (e.g. [Base62], [Base64], [Base36], [Base85])
 *
 * Typical use:
 *
 * - `encodeToString`: serialize → (optionally) append checksum → encode bytes
 * - `decodeFromString`: decode bytes → (optionally) verify checksum → deserialize
 *
 * This is intended for short, predictable tokens for URLs, headers, file names, etc.
 *
 * @property codec ASCII-safe byte codec used to turn raw bytes into text.
 * @property checksum Optional checksum appended to the binary payload and verified on decode.
 * @property binaryFormat Binary serialization format used before encoding.
 */
@OptIn(ExperimentalSerializationApi::class)
open class EncodedFormat(
    val configuration: EncodedConfiguration,
) : StringFormat {

    constructor(
        codec: ByteEncoding = Base62,
        checksum: Checksum? = null,
        binaryFormat: BinaryFormat = PackedFormat,
    ) : this(EncodedConfiguration(codec, checksum, binaryFormat))

    override val serializersModule: SerializersModule get() = configuration.binaryFormat.serializersModule

    /**
     * Default format: `PackedFormat` + `Base62` without checksum.
     */
    companion object Default : EncodedFormat()

    /**
     * Serializes [value] with [binaryFormat], optionally appends [checksum],
     * and encodes the result using [codec].
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
     * Decodes [string] using [codec], optionally verifies [checksum],
     * then deserializes the remaining bytes with [binaryFormat].
     *
     * @throws IllegalArgumentException if a checksum is configured and does not match.
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

class EncodedFormatBuilder {
    var codec: ByteEncoding = Base62
    var checksum: Checksum? = null
    var binaryFormat: BinaryFormat = PackedFormat.Default
}

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
