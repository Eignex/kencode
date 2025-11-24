package com.eignex.kencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals

class Crc16Test {
    private val crc16 = Crc16() // defaults = CRC-16/X25

    private fun digest(s: String): ByteArray =
        crc16.digest(s.toByteArray())

    private fun Int.toBytesBigEndian(size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) {
            val shift = 8 * (size - 1 - i)
            out[i] = ((this ushr shift) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun `crc16 x25 check value 123456789`() {
        val crc = digest("123456789")
        val expected = 0x906E.toBytesBigEndian(2)
        assertContentEquals(expected, crc)
    }

    @Test
    fun `crc16 x25 empty string`() {
        // X25 params: init=0xFFFF, xorOut=0xFFFF, refin+refout => empty -> 0x0000
        val crc = digest("")
        val expected = 0x0000.toBytesBigEndian(2)
        assertContentEquals(expected, crc)
    }

    @Test
    fun `crc16 size is two bytes`() {
        assertEquals(2, crc16.size)
    }

    @Test
    fun `crc16 is deterministic for same input`() {
        val data = "some test payload"
        val c1 = digest(data)
        val c2 = digest(data)
        assertContentEquals(c1, c2)
    }

    @Test
    fun `crc16 ccitt-false check value 123456789`() {
        // CRC-16/CCITT-FALSE: poly=0x1021, init=0xFFFF, refin=false, refout=false, xorOut=0x0000
        val ccittFalse = Crc16(
            poly = 0x1021,
            init = 0xFFFF,
            refin = false,
            refout = false,
            xorOut = 0x0000,
            width = 16
        )

        val crc = ccittFalse.digest("123456789".toByteArray())
        val expected = 0x29B1.toBytesBigEndian(2)
        assertContentEquals(expected, crc)
    }
}

class Crc32Test {

    private val crc32 = Crc32() // default = CRC-32/ISO-HDLC

    private fun digest(s: String): ByteArray =
        crc32.digest(s.toByteArray())

    private fun Int.toBytesBigEndian(size: Int): ByteArray {
        val out = ByteArray(size)
        for (i in 0 until size) {
            val shift = 8 * (size - 1 - i)
            out[i] = ((this ushr shift) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun `crc32 check value 123456789`() {
        val crc = digest("123456789")
        val expected = 0xCBF43926.toInt().toBytesBigEndian(4)
        assertContentEquals(expected, crc)
    }

    @Test
    fun `crc32 empty string`() {
        // For standard CRC-32 (init=FFFFFFFF, xorOut=FFFFFFFF, refin+refout), empty message -> 0x00000000
        val crc = digest("")
        val expected = 0x00000000.toBytesBigEndian(4)
        assertContentEquals(expected, crc)
    }

    @Test
    fun `crc32 size is four bytes`() {
        assertEquals(4, crc32.size)
    }

    @Test
    fun `crc32 is deterministic for same input`() {
        val data = "some test payload"
        val c1 = digest(data)
        val c2 = digest(data)
        assertContentEquals(c1, c2)
    }

    @Test
    fun `crc32 mpeg-2 check value 123456789`() {
        // CRC-32/MPEG-2: poly=0x04C11DB7, init=0xFFFFFFFF, refin=false, refout=false, xorOut=0x00000000
        val mpeg2 = Crc32(
            poly = 0x04C11DB7,
            init = 0xFFFFFFFF.toInt(),
            refin = false,
            refout = false,
            xorOut = 0x00000000,
            width = 32
        )

        val crc = mpeg2.digest("123456789".toByteArray())
        val expected = 0x0376E6E7.toInt().toBytesBigEndian(4)
        assertContentEquals(expected, crc)
    }
}
