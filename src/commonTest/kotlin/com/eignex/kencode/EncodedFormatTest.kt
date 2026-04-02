package com.eignex.kencode

import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlin.test.*

class EncodedFormatTest {

    @Serializable
    data class Payload(val x: Int, val s: String)

    private val formatNoChecksum: StringFormat = EncodedFormat(Base62, null)
    private val formatWithChecksum: StringFormat = EncodedFormat(Base62, Crc16)

    @Test
    fun `roundtrip without checksum`() {
        val value = Payload(42, "hello")
        val encoded =
            formatNoChecksum.encodeToString(Payload.serializer(), value)
        val decoded =
            formatNoChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `roundtrip with checksum`() {
        val value = Payload(-5, "world")
        val encoded =
            formatWithChecksum.encodeToString(Payload.serializer(), value)
        val decoded =
            formatWithChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `checksum mismatch throws`() {
        val value = Payload(7, "x")
        val encoded =
            formatWithChecksum.encodeToString(Payload.serializer(), value)
        val tampered = encoded.dropLast(1) + when (encoded.last()) {
            'A' -> 'B'
            else -> 'A'
        }
        assertFailsWith<IllegalArgumentException> {
            formatWithChecksum.decodeFromString(Payload.serializer(), tampered)
        }
    }

    @Test
    fun `compactZeros roundtrip without checksum`() {
        val value = Payload(1, "hi")
        val encoded = formatNoChecksum.encodeToString(Payload.serializer(), value)
        val decoded = formatNoChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
        val uncompressed = EncodedFormat(Base62, compactZeros = false)
            .encodeToString(Payload.serializer(), value)
        assertTrue(encoded.length <= uncompressed.length,
            "compactZeros ($encoded) should be <= uncompressed ($uncompressed)")
    }

    @Test
    fun `compactZeros roundtrip with checksum`() {
        val value = Payload(0, "zero")
        val encoded = formatWithChecksum.encodeToString(Payload.serializer(), value)
        val decoded = formatWithChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `compactZeros all-zero payload roundtrip`() {
        @Serializable data class Z(val a: Int, val b: Int)
        val value = Z(0, 0)
        val encoded = formatNoChecksum.encodeToString(Z.serializer(), value)
        val decoded = formatNoChecksum.decodeFromString(Z.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `compactZeros checksum mismatch throws`() {
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

    @Test
    fun `builder configures custom properties successfully`() {
        val value = Payload(99, "builder validation")

        val customFormat = EncodedFormat {
            codec = Base85
            checksum = Crc32
            binaryFormat = PackedFormat.Default
        }

        val encoded = customFormat.encodeToString(Payload.serializer(), value)

        assertTrue(encoded.isNotEmpty())
        val decoded =
            customFormat.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)

        val tampered =
            encoded.dropLast(1) + if (encoded.last() == 'u') "t" else "u"
        assertFailsWith<IllegalArgumentException> {
            customFormat.decodeFromString(Payload.serializer(), tampered)
        }
    }
}
