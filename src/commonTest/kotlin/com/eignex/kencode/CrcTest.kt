package com.eignex.kencode

import kotlin.test.*

class CrcTest {

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { i ->
        s.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }

    private fun Checksum.digest(s: String): ByteArray =
        digest(s.encodeToByteArray())

    @Test
    fun `crc8 size is one byte`() = assertEquals(1, Crc8.size)

    @Test
    fun `crc8 smbus check value 123456789`() =
        assertContentEquals(hex("F4"), Crc8.digest("123456789"))

    @Test
    fun `crc8 smbus empty input`() =
        assertContentEquals(hex("00"), Crc8.digest(""))

    @Test
    fun `crc8 rohc check value 123456789`() {
        val rohc = Crc8(
            poly = 0x07, init = 0xFF, refin = true, refout = true, xorOut = 0x00
        )
        assertContentEquals(hex("D0"), rohc.digest("123456789"))
    }

    @Test
    fun `crc16 size is two bytes`() = assertEquals(2, Crc16.size)

    @Test
    fun `crc16 x25 check value 123456789`() =
        assertContentEquals(hex("906E"), Crc16.digest("123456789"))

    @Test
    fun `crc16 x25 empty input`() =
        assertContentEquals(hex("0000"), Crc16.digest(""))

    @Test
    fun `crc16 ccitt-false check value 123456789`() {
        val ccittFalse = Crc16(
            poly = 0x1021,
            init = 0xFFFF,
            refin = false,
            refout = false,
            xorOut = 0x0000
        )
        assertContentEquals(hex("29B1"), ccittFalse.digest("123456789"))
    }

    @Test
    fun `crc32 size is four bytes`() = assertEquals(4, Crc32.size)

    @Test
    fun `crc32 iso-hdlc check value 123456789`() =
        assertContentEquals(hex("CBF43926"), Crc32.digest("123456789"))

    @Test
    fun `crc32 iso-hdlc empty input`() =
        assertContentEquals(hex("00000000"), Crc32.digest(""))

    @Test
    fun `crc32 mpeg-2 check value 123456789`() {
        val mpeg2 = Crc32(
            poly = 0x04C11DB7,
            init = 0xFFFFFFFF.toInt(),
            refin = false,
            refout = false,
            xorOut = 0x00000000
        )
        assertContentEquals(hex("0376E6E7"), mpeg2.digest("123456789"))
    }
}
