package com.eignex.kencode

import kotlin.test.*

class BitPackingTest {

    @Test
    fun `packFlags simple bit pattern`() {
        val flags = booleanArrayOf(true, false, true)
        val packed = PackedUtils.packFlags(flags)

        assertEquals(1, packed.size, "Should pack into 1 byte")
        assertEquals(5.toByte(), packed[0], "Bits should match 101 binary")
    }

    @Test
    fun `packFlags multi byte`() {
        val flags = BooleanArray(16)
        flags[0] = true
        flags[10] = true

        val packed = PackedUtils.packFlags(flags)

        assertEquals(2, packed.size, "Should require 2 bytes")
        assertEquals(1.toByte(), packed[0], "Byte 0 should have bit 0 set")
        assertEquals(4.toByte(), packed[1], "Byte 1 should have bit 2 set (index 10)")
    }

    @Test
    fun `packFlags all false optimizes to empty`() {
        val flags = booleanArrayOf(false, false, false, false)
        val packed = PackedUtils.packFlags(flags)

        assertEquals(0, packed.size, "All false should result in 0 bytes")
    }

    @Test
    fun `packFlags empty input`() {
        val flags = BooleanArray(0)
        val packed = PackedUtils.packFlags(flags)

        assertEquals(0, packed.size)
    }

    @Test
    fun `unpackFlags simple`() {
        val input = byteArrayOf(5)
        val unpacked = PackedUtils.unpackFlags(input, 0, 1)

        assertEquals(8, unpacked.size, "Unpacking 1 byte should yield 8 booleans")
        assertTrue(unpacked[0])
        assertFalse(unpacked[1])
        assertTrue(unpacked[2])
        assertFalse(unpacked[3])
    }

    @Test
    fun `unpackFlags with offset`() {
        val input = byteArrayOf(0xFF.toByte(), 5, 0xFF.toByte())
        val unpacked = PackedUtils.unpackFlags(input, 1, 1)

        assertEquals(8, unpacked.size)
        assertTrue(unpacked[0])
        assertFalse(unpacked[1])
        assertTrue(unpacked[2])
        assertFalse(unpacked[3])
    }

    @Test
    fun `pack and unpack roundtrip exact boundary`() {
        val flags = BooleanArray(16)
        flags[0] = true
        flags[7] = true
        flags[8] = true
        flags[15] = true

        val packed = PackedUtils.packFlags(flags)
        assertEquals(2, packed.size)

        val unpacked = PackedUtils.unpackFlags(packed, 0, packed.size)
        assertContentEquals(flags, unpacked)
    }

    @Test
    fun `pack and unpack roundtrip padded boundary`() {

        val flags = BooleanArray(10)
        flags[0] = true
        flags[9] = true

        val packed = PackedUtils.packFlags(flags)
        val unpacked = PackedUtils.unpackFlags(packed, 0, packed.size)

        assertEquals(16, unpacked.size)

        assertTrue(unpacked[0])
        assertTrue(unpacked[9])
        assertFalse(unpacked[1])

        for (i in 10 until 16) {
            assertFalse(unpacked[i], "Padding bit $i should be false")
        }
    }

    @Test
    fun `unpackFlags throws on OOB`() {
        val input = byteArrayOf(1, 2)
        assertFailsWith<IllegalArgumentException> {
            PackedUtils.unpackFlags(input, 0, 3)
        }
        assertFailsWith<IllegalArgumentException> {
            PackedUtils.unpackFlags(input, 2, 1)
        }
    }

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
        val values =
            listOf(0L, 1L, -1L, 2L, -2L, Long.MAX_VALUE, Long.MIN_VALUE)
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
            assertEquals(
                bytes.size,
                consumed,
                "VarInt consumed mismatch for $v"
            )
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
            assertEquals(
                bytes.size,
                consumed + 1,
                "VarInt consumed mismatch for $v"
            )
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
            assertEquals(
                bytes.size,
                consumed,
                "VarLong consumed mismatch for $v"
            )
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
            assertEquals(
                bytes.size,
                consumed + 1,
                "VarLong consumed mismatch for $v"
            )
        }
    }

    @Test
    fun `flags roundtrip`() {
        val flags = booleanArrayOf(true, false, true, true, false)
        val longFlags =
            PackedUtils.packFlagsToLong(*flags.toTypedArray().toBooleanArray())
        val unpacked = PackedUtils.unpackFlagsFromLong(longFlags, flags.size)
        assertContentEquals(flags, unpacked)
    }

    private fun Array<Boolean>.toBooleanArray(): BooleanArray {
        val result = BooleanArray(size)
        for (i in indices) result[i] = this[i]
        return result
    }
}
