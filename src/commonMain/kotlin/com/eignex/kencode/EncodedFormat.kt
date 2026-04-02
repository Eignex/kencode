package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule

/**
 * Holds the configuration for an [EncodedFormat] instance.
 *
 * @property codec The ASCII-safe byte codec used to turn raw bytes into text (e.g., Base62, Base64).
 * @property transform An optional [PayloadTransform] applied after serialization and before encoding.
 *   Common uses: integrity checks via [Checksum.asTransform], encryption, or error-correcting codes.
 * @property binaryFormat The underlying binary serialization format used before encoding to text.
 * @property compactZeros When true, leading zero bytes are stripped before encoding and restored on decode.
 *   A varint prefix encodes the count, costing 1 byte for up to 127 stripped bytes.
 */
data class EncodedConfiguration(
    val codec: ByteEncoding = Base62,
    val transform: PayloadTransform? = null,
    val binaryFormat: BinaryFormat = PackedFormat.Default,
    val compactZeros: Boolean = false,
)

/**
 * Text `StringFormat` that produces short, predictable string tokens by composing:
 *
 * 1. A binary format (e.g. [PackedFormat], `ProtoBuf`).
 * 2. An optional [PayloadTransform] (checksum, encryption, ECC, …).
 * 3. An ASCII-safe byte encoding (e.g. [Base62], [Base64], [Base36], [Base85]).
 *
 * Typical use:
 * - `encodeToString`: serialize -> transform.encode -> (optionally) compact zeros -> encode bytes to text.
 * - `decodeFromString`: decode text to bytes -> (optionally) restore zeros -> transform.decode -> deserialize.
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
        compactZeros: Boolean = true,
    ) : this(EncodedConfiguration(codec, transform, binaryFormat, compactZeros))

    /**
     * Delegates to the underlying [BinaryFormat]'s serializers module.
     */
    override val serializersModule: SerializersModule get() = configuration.binaryFormat.serializersModule

    /**
     * Default format: `PackedFormat` + `Base62` without a transform.
     */
    companion object Default : EncodedFormat()

    /**
     * Serializes [value] with the configured binary format, applies the transform,
     * and encodes the resulting byte array using the text codec.
     */
    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        val bytes = configuration.binaryFormat.encodeToByteArray(serializer, value)
        val transformed = configuration.transform?.encode(bytes) ?: bytes
        val payload = if (configuration.compactZeros) compactZerosEncode(transformed) else transformed
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
        val raw = if (configuration.compactZeros) compactZerosDecode(input) else input
        val bytes = configuration.transform?.decode(raw) ?: raw
        return configuration.binaryFormat.decodeFromByteArray(deserializer, bytes)
    }

    private fun compactZerosEncode(bytes: ByteArray): ByteArray {
        var k = 0
        while (k < bytes.size && bytes[k] == 0.toByte()) k++
        val prefix = varintEncode(k)
        val result = ByteArray(prefix.size + bytes.size - k)
        prefix.copyInto(result)
        bytes.copyInto(result, destinationOffset = prefix.size, startIndex = k)
        return result
    }

    private fun compactZerosDecode(bytes: ByteArray): ByteArray {
        require(bytes.isNotEmpty()) { "Compact payload cannot be empty." }
        val (k, prefixLen) = varintDecode(bytes)
        val result = ByteArray(k + bytes.size - prefixLen)
        bytes.copyInto(result, destinationOffset = k, startIndex = prefixLen)
        return result
    }

    private fun varintEncode(value: Int): ByteArray {
        val out = ByteOutput(5)
        PackedUtils.writeVarInt(value, out)
        return out.toByteArray()
    }

    private fun varintDecode(bytes: ByteArray): Pair<Int, Int> =
        PackedUtils.decodeVarInt(bytes, 0)
}

/**
 * Builder for configuring [EncodedFormat] instances.
 */
class EncodedFormatBuilder {
    var codec: ByteEncoding = Base62
    var transform: PayloadTransform? = null
    var binaryFormat: BinaryFormat = PackedFormat.Default
    var compactZeros: Boolean = true

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
 *     transform = Crc16.asTransform()
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
        compactZeros = from.configuration.compactZeros
    }
    builder.builderAction()
    return EncodedFormat(EncodedConfiguration(
        builder.codec,
        builder.transform,
        builder.binaryFormat,
        builder.compactZeros,
    ))
}
