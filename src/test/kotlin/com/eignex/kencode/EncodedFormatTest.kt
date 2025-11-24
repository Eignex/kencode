package com.eignex.kencode

import kotlinx.serialization.*
import kotlin.test.*

class EncodedFormatTest {

    @Serializable
    data class Payload(val x: Int, val s: String)

    private val formatNoChecksum: StringFormat = EncodedFormat(Base62, null)
    private val formatWithChecksum: StringFormat = EncodedFormat(Base62, Crc16)

    @Test
    fun `roundtrip without checksum`() {
        val value = Payload(42, "hello")
        val encoded = formatNoChecksum.encodeToString(Payload.serializer(), value)
        val decoded = formatNoChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `roundtrip with checksum`() {
        val value = Payload(-5, "world")
        val encoded = formatWithChecksum.encodeToString(Payload.serializer(), value)
        val decoded = formatWithChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `checksum mismatch throws`() {
        val value = Payload(7, "x")
        val encoded = formatWithChecksum.encodeToString(Payload.serializer(), value)
        val tampered = encoded.dropLast(1) + when (encoded.last()) {
            'A' -> 'B'
            else -> 'A'
        }
        assertFailsWith<IllegalArgumentException> {
            formatWithChecksum.decodeFromString(Payload.serializer(), tampered)
        }
    }
}
