package com.eignex.kencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class BitPackingTest {

    @Test
    fun `short roundtrip`() {
        val out = java.io.ByteArrayOutputStream()
        PackedUtils.writeShort(0x1234.toShort(), out)
        val bytes = out.toByteArray()
        val decoded = PackedUtils.readShort(bytes, 0)
        assertEquals(0x1234.toShort(), decoded)
    }

    @Test
    fun `short roundtrip offset`() {
        val out = java.io.ByteArrayOutputStream()
        out.write(1)
        PackedUtils.writeShort(0x1234.toShort(), out)
        val bytes = out.toByteArray()
        val decoded = PackedUtils.readShort(bytes, 1)
        assertEquals(0x1234.toShort(), decoded)
    }

    @Test
    fun `int roundtrip`() {
        val out = java.io.ByteArrayOutputStream()
        PackedUtils.writeInt(0x01020304, out)
        val bytes = out.toByteArray()
        val decoded = PackedUtils.readInt(bytes, 0)
        assertEquals(0x01020304, decoded)
    }

    @Test
    fun `int roundtrip offset`() {
        val out = java.io.ByteArrayOutputStream()
        out.write(1)
        PackedUtils.writeInt(0x01020304, out)
        val bytes = out.toByteArray()
        val decoded = PackedUtils.readInt(bytes, 1)
        assertEquals(0x01020304, decoded)
    }

    @Test
    fun `long roundtrip`() {
        val value = 0x0102030405060708L
        val out = java.io.ByteArrayOutputStream()
        PackedUtils.writeLong(value, out)
        val bytes = out.toByteArray()
        val decoded = PackedUtils.readLong(bytes, 0)
        assertEquals(value, decoded)
    }

    @Test
    fun `long roundtrip offset`() {
        val value = 0x0102030405060708L
        val out = java.io.ByteArrayOutputStream()
        out.write(1)
        PackedUtils.writeLong(value, out)
        val bytes = out.toByteArray()
        val decoded = PackedUtils.readLong(bytes, 1)
        assertEquals(value, decoded)
    }

    @Test
    fun `zigzag int roundtrip`() {
        val values = listOf(0, 1, -1, 2, -2, Int.MAX_VALUE, Int.MIN_VALUE)
        for (v in values) {
            val enc = PackedUtils.zigZagEncodeInt(v)
            val dec = PackedUtils.zigZagDecodeInt(enc)
            assertEquals(v, dec, "Int ZigZag failed for $v")
        }
    }

    @Test
    fun `zigzag long roundtrip`() {
        val values = listOf(0L, 1L, -1L, 2L, -2L, Long.MAX_VALUE, Long.MIN_VALUE)
        for (v in values) {
            val enc = PackedUtils.zigZagEncodeLong(v)
            val dec = PackedUtils.zigZagDecodeLong(enc)
            assertEquals(v, dec, "Long ZigZag failed for $v")
        }
    }

    @Test
    fun `varint roundtrip`() {
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
            PackedUtils.writeVarInt(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = PackedUtils.decodeVarInt(bytes, 0)
            assertEquals(v, decoded, "VarInt failed for $v")
            assertEquals(bytes.size, consumed, "VarInt consumed mismatch for $v")
        }
    }


    @Test
    fun `varint roundtrip offset`() {
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
            out.write(1)
            PackedUtils.writeVarInt(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = PackedUtils.decodeVarInt(bytes, 1)
            assertEquals(v, decoded, "VarInt failed for $v")
            assertEquals(bytes.size, consumed+1, "VarInt consumed mismatch for $v")
        }
    }

    @Test
    fun `varlong roundtrip`() {
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
            PackedUtils.writeVarLong(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = PackedUtils.decodeVarLong(bytes, 0)
            assertEquals(v, decoded, "VarLong failed for $v")
            assertEquals(bytes.size, consumed, "VarLong consumed mismatch for $v")
        }
    }

    @Test
    fun `varlong roundtrip offset`() {
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
            out.write(1)
            PackedUtils.writeVarLong(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = PackedUtils.decodeVarLong(bytes, 1)
            assertEquals(v, decoded, "VarLong failed for $v")
            assertEquals(bytes.size, consumed+1, "VarLong consumed mismatch for $v")
        }
    }

    @Test
    fun `flags roundtrip`() {
        val flags = booleanArrayOf(true, false, true, true, false)
        val longFlags = PackedUtils.packFlagsToLong(*flags.toTypedArray().toBooleanArray())
        val unpacked = PackedUtils.unpackFlagsFromLong(longFlags, flags.size)
        assertContentEquals(flags, unpacked)
    }

    private fun Array<Boolean>.toBooleanArray(): BooleanArray {
        val result = BooleanArray(size)
        for (i in indices) result[i] = this[i]
        return result
    }
}
