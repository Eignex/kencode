package com.eignex.kencode

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class BitPackingTest {

    @Test
    fun short_big_endian_roundtrip() {
        val out = java.io.ByteArrayOutputStream()
        BitPacking.writeShort(0x1234.toShort(), out)
        val bytes = out.toByteArray()

        // Big-endian: high byte first
        assertContentEquals(byteArrayOf(0x12, 0x34), bytes)

        val decoded = BitPacking.readShort(bytes, 0)
        assertEquals(0x1234.toShort(), decoded)
    }

    @Test
    fun int_big_endian_roundtrip() {
        val out = java.io.ByteArrayOutputStream()
        BitPacking.writeInt(0x01020304, out)
        val bytes = out.toByteArray()

        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04),
            bytes
        )

        val decoded = BitPacking.readInt(bytes, 0)
        assertEquals(0x01020304, decoded)
    }

    @Test
    fun long_big_endian_roundtrip() {
        val value = 0x0102030405060708L
        val out = java.io.ByteArrayOutputStream()
        BitPacking.writeLong(value, out)
        val bytes = out.toByteArray()

        assertContentEquals(
            byteArrayOf(
                0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08
            ),
            bytes
        )

        val decoded = BitPacking.readLong(bytes, 0)
        assertEquals(value, decoded)
    }

    @Test
    fun zigzag_int_roundtrip() {
        val values = listOf(0, 1, -1, 2, -2, Int.MAX_VALUE, Int.MIN_VALUE)
        for (v in values) {
            val enc = BitPacking.zigZagEncodeInt(v)
            val dec = BitPacking.zigZagDecodeInt(enc)
            assertEquals(v, dec, "Int ZigZag failed for $v")
        }
    }

    @Test
    fun zigzag_long_roundtrip() {
        val values = listOf(0L, 1L, -1L, 2L, -2L, Long.MAX_VALUE, Long.MIN_VALUE)
        for (v in values) {
            val enc = BitPacking.zigZagEncodeLong(v)
            val dec = BitPacking.zigZagDecodeLong(enc)
            assertEquals(v, dec, "Long ZigZag failed for $v")
        }
    }

    @Test
    fun varint_roundtrip() {
        val values = listOf(
            0,
            1,
            127,
            128,
            300,
            Int.MAX_VALUE
        )
        for (v in values) {
            val out = java.io.ByteArrayOutputStream()
            BitPacking.writeVarInt(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = BitPacking.decodeVarInt(bytes, 0)
            assertEquals(v, decoded, "VarInt failed for $v")
            assertEquals(bytes.size, consumed, "VarInt consumed mismatch for $v")
        }
    }

    @Test
    fun varlong_roundtrip() {
        val values = listOf(
            0L,
            1L,
            127L,
            128L,
            300L,
            Long.MAX_VALUE
        )
        for (v in values) {
            val out = java.io.ByteArrayOutputStream()
            BitPacking.writeVarLong(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = BitPacking.decodeVarLong(bytes, 0)
            assertEquals(v, decoded, "VarLong failed for $v")
            assertEquals(bytes.size, consumed, "VarLong consumed mismatch for $v")
        }
    }

    @Test
    fun pack_unpack_flags_roundtrip() {
        val flags = booleanArrayOf(true, false, true, true, false)
        val intFlags = BitPacking.packFlagsToInt(*flags.toTypedArray().toBooleanArray())
        val unpacked = BitPacking.unpackFlagsFromInt(intFlags, flags.size)
        assertContentEquals(flags, unpacked)
    }

    // Helper to convert List<Boolean> to BooleanArray cleanly
    private fun Array<Boolean>.toBooleanArray(): BooleanArray {
        val result = BooleanArray(size)
        for (i in indices) result[i] = this[i]
        return result
    }
}
