package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@ExperimentalSerializationApi
open class BaseFormat(
    val codec: ByteCodec,
    val checksum: Checksum? = null
) : StringFormat {

    companion object Default : BaseFormat(Base62)

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun <T> encodeToString(
        serializer: SerializationStrategy<T>,
        value: T
    ): String {
        val bytes = BitPackedFormat.encodeToByteArray(serializer, value)
        val checked = if (checksum != null) {
            bytes + checksum.compute(bytes)
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
            val expected = checksum.compute(bytes)
            require(actual.contentEquals(expected)) {
                "Checksum mismatch."
            }
            bytes
        } else input
        return BitPackedFormat.decodeFromByteArray(deserializer, bytes)
    }
}
