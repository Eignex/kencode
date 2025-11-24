package com.eignex.kencode

import PackedFormat
import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
open class EncodedFormat(
    val codec: ByteEncoding = Base62,
    val checksum: Checksum? = null,
    val binaryFormat: BinaryFormat = PackedFormat
) : StringFormat {

    companion object Default : EncodedFormat()

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun <T> encodeToString(
        serializer: SerializationStrategy<T>,
        value: T
    ): String {
        val bytes = binaryFormat.encodeToByteArray(serializer, value)
        val checked = if (checksum != null) {
            bytes + checksum.digest(bytes)
        } else bytes
        return codec.encode(checked)
    }

    override fun <T> decodeFromString(
        deserializer: DeserializationStrategy<T>,
        string: String
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
