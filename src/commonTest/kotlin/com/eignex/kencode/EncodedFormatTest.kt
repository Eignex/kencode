package com.eignex.kencode

import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlin.test.*

class EncodedFormatTest {

    @Serializable
    data class Payload(val x: Int, val s: String)

    private val formatBare: StringFormat = EncodedFormat(Base62, null)
    private val formatChecksum: StringFormat = EncodedFormat(Base62, Crc16.asTransform())
    private val formatCompact: StringFormat = EncodedFormat(Base62, CompactZeros)
    private val formatCompactChecksum: StringFormat = EncodedFormat(Base62, CompactZeros.then(Crc16.asTransform()))

    @Test
    fun `roundtrip without transform`() {
        val value = Payload(42, "hello")
        val encoded = formatBare.encodeToString(Payload.serializer(), value)
        val decoded = formatBare.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `roundtrip with checksum`() {
        val value = Payload(-5, "world")
        val encoded = formatChecksum.encodeToString(Payload.serializer(), value)
        val decoded = formatChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `checksum mismatch throws`() {
        val value = Payload(7, "x")
        val encoded = formatChecksum.encodeToString(Payload.serializer(), value)
        val tampered = encoded.dropLast(1) + when (encoded.last()) {
            'A' -> 'B'
            else -> 'A'
        }
        assertFailsWith<IllegalArgumentException> {
            formatChecksum.decodeFromString(Payload.serializer(), tampered)
        }
    }

    @Test
    fun `compactZeros roundtrip`() {
        val value = Payload(1, "hi")
        val encoded = formatCompact.encodeToString(Payload.serializer(), value)
        val decoded = formatCompact.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
        val uncompressed = formatBare.encodeToString(Payload.serializer(), value)
        assertTrue(
            encoded.length <= uncompressed.length,
            "compactZeros ($encoded) should be <= uncompressed ($uncompressed)"
        )
    }

    @Test
    fun `compactZeros roundtrip with checksum`() {
        val value = Payload(0, "zero")
        val encoded = formatCompactChecksum.encodeToString(Payload.serializer(), value)
        val decoded = formatCompactChecksum.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `compactZeros all-zero payload roundtrip`() {
        @Serializable
        data class Z(val a: Int, val b: Int)

        val value = Z(0, 0)
        val encoded = formatCompact.encodeToString(Z.serializer(), value)
        val decoded = formatCompact.decodeFromString(Z.serializer(), encoded)
        assertEquals(value, decoded)
    }

    @Test
    fun `compactZeros checksum mismatch throws`() {
        val value = Payload(7, "x")
        val encoded = formatCompactChecksum.encodeToString(Payload.serializer(), value)
        val tampered = encoded.dropLast(1) + when (encoded.last()) {
            'A' -> 'B'
            else -> 'A'
        }
        assertFailsWith<IllegalArgumentException> {
            formatCompactChecksum.decodeFromString(Payload.serializer(), tampered)
        }
    }

    @Test
    fun `compactZeros no overhead when no leading zeros`() {
        val value = Payload(-1, "a")
        val compact = formatCompact.encodeToString(Payload.serializer(), value)
        val bare = formatBare.encodeToString(Payload.serializer(), value)
        assertEquals(bare, compact)
    }

    @Test
    fun `builder configures custom properties successfully`() {
        val value = Payload(99, "builder validation")

        val customFormat = EncodedFormat {
            codec = Base85
            transform = Crc32.asTransform()
            binaryFormat = PackedFormat.Default
        }

        val encoded = customFormat.encodeToString(Payload.serializer(), value)

        assertTrue(encoded.isNotEmpty())
        val decoded = customFormat.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)

        val tampered = encoded.dropLast(1) + if (encoded.last() == 'u') "t" else "u"
        assertFailsWith<IllegalArgumentException> {
            customFormat.decodeFromString(Payload.serializer(), tampered)
        }
    }

    @Test
    fun `builder then composition roundtrip`() {
        val value = Payload(0, "composed")

        val format = EncodedFormat {
            transform = CompactZeros.then(Crc16.asTransform())
        }

        val encoded = format.encodeToString(Payload.serializer(), value)
        val decoded = format.decodeFromString(Payload.serializer(), encoded)
        assertEquals(value, decoded)
    }
}
