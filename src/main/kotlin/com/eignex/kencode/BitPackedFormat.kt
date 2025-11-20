package com.eignex.kencode

import kotlinx.serialization.BinaryFormat
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream

@ExperimentalSerializationApi
object BitPackedFormat : BinaryFormat {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun <T> encodeToByteArray(
        serializer: SerializationStrategy<T>,
        value: T
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val encoder = BitPackedEncoder( out)
        encoder.encodeSerializableValue(serializer, value)
        return out.toByteArray()
    }

    override fun <T> decodeFromByteArray(
        deserializer: DeserializationStrategy<T>,
        bytes: ByteArray
    ): T {
        val decoder = BitPackedDecoder( bytes)
        return decoder.decodeSerializableValue(deserializer)
    }
}
