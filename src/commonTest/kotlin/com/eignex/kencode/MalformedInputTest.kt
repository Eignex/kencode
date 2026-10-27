package com.eignex.kencode

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MalformedInputTest {
    @Serializable
    data class Payload(val id: Int, val flag: Boolean, val name: String)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `CompactZeros rejects zero counts that overflow or exceed the limit`() {
        assertFailsWith<IllegalArgumentException> { CompactZeros.decode(bytes(0, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F)) }
        assertFailsWith<IllegalArgumentException> { CompactZeros.decode(bytes(0, 0xFF, 0xFF, 0xFF, 0xFF, 0x07)) }
        assertFailsWith<IllegalArgumentException> { CompactZeros.decode(bytes(0, 0x81, 0x80, 0x04)) }
    }

    @Test
    fun `CompactZeros rejects a sentinel with a zero count`() {
        assertFailsWith<IllegalArgumentException> { CompactZeros.decode(bytes(0, 0, 5)) }
    }

    @Test
    fun `CompactZeros roundtrips zero runs longer than the limit`() {
        for (size in listOf(65_535, 65_536, 65_537, 70_000)) {
            val data = ByteArray(size + 1).also { it[size] = 7 }
            assertContentEquals(data, CompactZeros.decode(CompactZeros.encode(data)))
            val allZero = ByteArray(size)
            assertContentEquals(allZero, CompactZeros.decode(CompactZeros.encode(allZero)))
        }
    }

    @Test
    fun `varints reject bits beyond the target width`() {
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(Int.serializer(), bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x7F))
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                Long.serializer(),
                bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x03),
            )
        }
        assertFailsWith<IllegalArgumentException> { readVarInt(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x1F), PackedPosition()) }
        assertFailsWith<IllegalArgumentException> {
            readVarLong(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x02), PackedPosition())
        }
    }

    @Test
    fun `varints reject overlong encodings`() {
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                Int.serializer(),
                bytes(0x81, 0x00),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                Long.serializer(),
                bytes(0x80, 0x00),
            )
        }
        assertFailsWith<IllegalArgumentException> { readVarInt(bytes(0x80, 0x80, 0x00), PackedPosition()) }
    }

    @Test
    fun `varints still accept every canonical boundary value`() {
        for (value in listOf(0, 1, 127, 128, 16_383, 16_384, Int.MAX_VALUE, Int.MIN_VALUE, -1)) {
            val encoded = PackedFormat.encodeToByteArray(Int.serializer(), value)
            assertEquals(value, PackedFormat.decodeFromByteArray(Int.serializer(), encoded))
        }
        for (value in listOf(0L, 127L, 128L, Long.MAX_VALUE, Long.MIN_VALUE, -1L)) {
            val encoded = PackedFormat.encodeToByteArray(Long.serializer(), value)
            assertEquals(value, PackedFormat.decodeFromByteArray(Long.serializer(), encoded))
        }
    }

    @Test
    fun `packed decoding rejects trailing bytes`() {
        val encoded = PackedFormat.encodeToByteArray(Payload.serializer(), Payload(1, true, "x"))
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(Payload.serializer(), encoded + bytes(9))
        }
        assertFailsWith<IllegalArgumentException> {
            EncodedFormat.decodeFromString(Payload.serializer(), Base62.encode(encoded + bytes(9, 9, 9)))
        }
    }
}
