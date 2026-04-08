package com.eignex.kencode

import kotlin.test.*

class BitPackingTest {

    @Test
    fun `packFlags simple bit pattern`() {
        val flags = booleanArrayOf(true, false, true)
        val packed = packFlags(flags)

        assertEquals(1, packed.size)
        assertEquals(5.toByte(), packed[0])
    }

    @Test
    fun `packFlags multi byte`() {
        val flags = BooleanArray(16)
        flags[0] = true
        flags[10] = true

        val packed = packFlags(flags)

        assertEquals(2, packed.size)
        assertEquals(1.toByte(), packed[0])
        assertEquals(4.toByte(), packed[1])
    }

    @Test
    fun `packFlags all false optimizes to empty`() {
        val flags = booleanArrayOf(false, false, false, false)
        val packed = packFlags(flags)

        assertEquals(0, packed.size)
    }

    @Test
    fun `packFlags empty input`() {
        val flags = BooleanArray(0)
        val packed = packFlags(flags)

        assertEquals(0, packed.size)
    }

    @Test
    fun `unpackFlags simple`() {
        val input = byteArrayOf(5)
        val unpacked = unpackFlags(input, 0, 1)

        assertEquals(8, unpacked.size)
        assertTrue(unpacked[0])
        assertFalse(unpacked[1])
        assertTrue(unpacked[2])
        assertFalse(unpacked[3])
    }

    @Test
    fun `unpackFlags with offset`() {
        val input = byteArrayOf(0xFF.toByte(), 5, 0xFF.toByte())
        val unpacked = unpackFlags(input, 1, 1)

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

        val packed = packFlags(flags)
        assertEquals(2, packed.size)

        val unpacked = unpackFlags(packed, 0, packed.size)
        assertContentEquals(flags, unpacked)
    }

    @Test
    fun `pack and unpack roundtrip padded boundary`() {
        val flags = BooleanArray(10)
        flags[0] = true
        flags[9] = true

        val packed = packFlags(flags)
        val unpacked = unpackFlags(packed, 0, packed.size)

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
            unpackFlags(input, 0, 3)
        }
        assertFailsWith<IllegalArgumentException> {
            unpackFlags(input, 2, 1)
        }
    }

    @Test
    fun `short roundtrip`() {
        val out = ByteOutput()
        writeShort(0x1234.toShort(), out)
        val bytes = out.toByteArray()
        val decoded = readShort(bytes, 0)
        assertEquals(0x1234.toShort(), decoded)
    }

    @Test
    fun `short roundtrip offset`() {
        val out = ByteOutput()
        out.write(1)
        writeShort(0x1234.toShort(), out)
        val bytes = out.toByteArray()
        val decoded = readShort(bytes, 1)
        assertEquals(0x1234.toShort(), decoded)
    }

    @Test
    fun `int roundtrip`() {
        val out = ByteOutput()
        writeInt(0x01020304, out)
        val bytes = out.toByteArray()
        val decoded = readInt(bytes, 0)
        assertEquals(0x01020304, decoded)
    }

    @Test
    fun `int roundtrip offset`() {
        val out = ByteOutput()
        out.write(1)
        writeInt(0x01020304, out)
        val bytes = out.toByteArray()
        val decoded = readInt(bytes, 1)
        assertEquals(0x01020304, decoded)
    }

    @Test
    fun `long roundtrip`() {
        val value = 0x0102030405060708L
        val out = ByteOutput()
        writeLong(value, out)
        val bytes = out.toByteArray()
        val decoded = readLong(bytes, 0)
        assertEquals(value, decoded)
    }

    @Test
    fun `long roundtrip offset`() {
        val value = 0x0102030405060708L
        val out = ByteOutput()
        out.write(1)
        writeLong(value, out)
        val bytes = out.toByteArray()
        val decoded = readLong(bytes, 1)
        assertEquals(value, decoded)
    }

    @Test
    fun `zigzag int roundtrip`() {
        val values = listOf(0, 1, -1, 2, -2, Int.MAX_VALUE, Int.MIN_VALUE)
        for (v in values) {
            val enc = zigZagEncodeInt(v)
            val dec = zigZagDecodeInt(enc)
            assertEquals(v, dec, "Int ZigZag failed for $v")
        }
    }

    @Test
    fun `zigzag long roundtrip`() {
        val values =
            listOf(0L, 1L, -1L, 2L, -2L, Long.MAX_VALUE, Long.MIN_VALUE)
        for (v in values) {
            val enc = zigZagEncodeLong(v)
            val dec = zigZagDecodeLong(enc)
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
            val out = ByteOutput()
            writeVarInt(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = decodeVarInt(bytes, 0)
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
            val out = ByteOutput()
            out.write(1)
            writeVarInt(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = decodeVarInt(bytes, 1)
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
            val out = ByteOutput()
            writeVarLong(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = decodeVarLong(bytes, 0)
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
            val out = ByteOutput()
            out.write(1)
            writeVarLong(v, out)
            val bytes = out.toByteArray()
            val (decoded, consumed) = decodeVarLong(bytes, 1)
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
        val longFlags = packFlagsToLong(flags)
        val unpacked = unpackFlagsFromLong(longFlags, flags.size)
        assertContentEquals(flags, unpacked)
    }

    // --- ByteOutput ---

    @Test
    fun `ByteOutput reset clears written bytes`() {
        val out = ByteOutput()
        out.write(1)
        out.write(2)
        out.reset()
        assertEquals(0, out.toByteArray().size)
        out.write(42)
        assertContentEquals(byteArrayOf(42), out.toByteArray())
    }

    @Test
    fun `ByteOutput writeTo transfers all bytes to another ByteOutput`() {
        val src = ByteOutput()
        src.write(10)
        src.write(20)
        src.write(30)
        val dst = ByteOutput()
        src.writeTo(dst)
        assertContentEquals(byteArrayOf(10, 20, 30), dst.toByteArray())
    }

    @Test
    fun `ByteOutput expands capacity when writes exceed initial size`() {
        val out = ByteOutput(initialCapacity = 4)
        val data = ByteArray(100) { it.toByte() }
        out.write(data)
        assertContentEquals(data, out.toByteArray())
    }

    @Test
    fun `comprehensive truncation coverage`() {
        val types: List<Pair<String, (ByteOutput) -> Unit>> = listOf(
            "Short" to {
                writeShort(
                    1102,
                    it
                )
            },
            "Int" to {
                writeInt(
                    110258102,
                    it
                )
            },
            "Long" to {
                writeLong(
                    1102401240912490L,
                    it
                )
            },
            "VarInt" to {
                writeVarInt(
                    110249021,
                    it
                )
            },
            "VarLong" to {
                writeVarLong(
                    112085102501L,
                    it
                )
            }
        )

        types.forEach { (name, writer) ->
            val out = ByteOutput()
            writer(out)
            val full = out.toByteArray()
            // Truncate by 1 byte to trigger requireAvailable
            val truncated = full.copyOfRange(0, full.size - 1)

            assertFailsWith<IllegalArgumentException>("Should fail $name truncation") {
                when (name) {
                    "Short" -> readShort(truncated, 0)
                    "Int" -> readInt(truncated, 0)
                    "Long" -> readLong(truncated, 0)
                    "VarInt" -> decodeVarInt(truncated, 0)
                    "VarLong" -> decodeVarLong(truncated, 0)
                }
            }
        }
    }
}
