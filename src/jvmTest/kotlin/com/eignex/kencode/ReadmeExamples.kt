package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
class ReadmeExamples {

    // Example from readme

    @Serializable
    data class Payload(

        // only uses as many bytes as needed
        @PackedType(IntPacking.DEFAULT)
        val id: ULong,

        @PackedType(IntPacking.SIGNED) // zig-zag encodes small negatives efficiently
        val delta: Int,

        // these are packed into a bitset along with nullability flags
        val urgent: Boolean,
        val sensitive: Boolean,
        val external: Boolean,
        val handledAt: Instant?,

        // encoded as varint ordinal
        val type: PayloadType
    )

    enum class PayloadType {
        TYPE1, TYPE2, TYPE3
    }

    @Test
    fun `example serialization`() {
        val payload = Payload(
            id = 123u,
            delta = -2,
            urgent = true,
            sensitive = false,
            external = true,
            handledAt = null,
            type = PayloadType.TYPE1
        )
        val message = EncodedFormat.encodeToString(payload)
        println(message)
        // 0fiXYI

        val result = EncodedFormat.decodeFromString<Payload>(message)
        assertEquals(result, payload)
    }

    @Serializable
    data class MapPayload(val map: Map<String, Int>)

    @Test
    fun `protobuf comparison`() {
        val payload = MapPayload(mapOf("k1" to 1285, "k2" to 9681))
        println(EncodedFormat(binaryFormat = ProtoBuf).encodeToString<MapPayload>(payload))
        println(EncodedFormat.encodeToString<MapPayload>(payload))
    }

    @Test
    fun `encoding examples`() {
        val bytes = "any byte data".encodeToByteArray()
        println(Base62.encode(bytes))
        println(Base36.encode(bytes))
        println(Base64.encode(bytes))
        println(Base85.encode(bytes))
        println(BaseRadix(UnicodeRangeAlphabet()).encode(bytes))
    }
}
