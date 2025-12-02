package com.eignex.kencode

import PackedFormat
import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

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
    val codec: ByteEncoding = Base62,
    val checksum: Checksum? = null,
    val binaryFormat: BinaryFormat = PackedFormat
) : StringFormat {

    /**
     * Default format: `PackedFormat` + `Base62` without checksum.
     */
    companion object Default : EncodedFormat()

    override val serializersModule: SerializersModule = EmptySerializersModule()

    /**
     * Serializes [value] with [binaryFormat], optionally appends [checksum],
     * and encodes the result using [codec].
     */
    override fun <T> encodeToString(
        serializer: SerializationStrategy<T>, value: T
    ): String {
        val bytes = binaryFormat.encodeToByteArray(serializer, value)
        val checked = if (checksum != null) {
            bytes + checksum.digest(bytes)
        } else bytes
        return codec.encode(checked)
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
        val input = codec.decode(string)
        val bytes = if (checksum != null) {
            require(input.size >= checksum.size)
            val bytes = input.sliceArray(0..<input.size - checksum.size)
            val actual =
                input.sliceArray(input.size - checksum.size..<input.size)
            val expected = checksum.digest(bytes)
            require(actual.contentEquals(expected)) {
                "Checksum mismatch."
            }
            bytes
        } else input
        return binaryFormat.decodeFromByteArray(deserializer, bytes)
    }
}
