package com.eignex.kencode

/**
 * Shared CRC digest engine parameterized by width (up to 32 bits).
 * Uses Long arithmetic throughout to handle both 16-bit and 32-bit cases uniformly.
 */
private class CrcEngine(
    poly: Int,
    init: Int,
    private val refin: Boolean,
    private val refout: Boolean,
    xorOut: Int,
    private val width: Int
) {
    private val mask: Long = if (width == 32) 0xFFFFFFFFL else (1L shl width) - 1L
    private val refPoly: Long = poly.reverseBits(width).toLong() and mask
    private val refInit: Long = init.reverseBits(width).toLong() and mask
    private val xorOutL: Long = xorOut.toLong() and mask

    val size: Int = (width + 7) / 8

    fun digest(data: ByteArray): ByteArray {
        var crc = refInit
        for (b in data) {
            val raw = b.toInt() and 0xFF
            val byte = if (refin) raw else raw.reverseBits8()
            crc = crc xor byte.toLong()
            repeat(8) {
                crc = if ((crc and 1L) != 0L) (crc ushr 1) xor refPoly else crc ushr 1
            }
            crc = crc and mask
        }
        var finalCrc = if (refout) crc else crc.toInt().reverseBits(width).toLong() and mask
        finalCrc = (finalCrc xor xorOutL) and mask
        return ByteArray(size) { i ->
            val shift = 8 * (size - 1 - i)
            ((finalCrc ushr shift) and 0xFFL).toByte()
        }
    }
}

/**
 * CRC-8 implementation with configurable polynomial and parameters.
 *
 * Default configuration implements CRC-8/SMBUS.
 *
 * Parameters:
 * @param poly generator polynomial (high bit implicit).
 * @param init initial CRC register value.
 * @param refin whether to reflect bits of each input byte.
 * @param refout whether to reflect the final CRC value.
 * @param xorOut final XOR mask.
 *
 * Output:
 * - Returned as a single-byte array.
 */
open class Crc8(
    poly: Int = 0x07,
    init: Int = 0x00,
    refin: Boolean = false,
    refout: Boolean = false,
    xorOut: Int = 0x00
) : Checksum {

    companion object Default : Crc8()

    private val engine = CrcEngine(poly, init, refin, refout, xorOut, width = 8)

    override val size: Int get() = engine.size

    override fun digest(data: ByteArray): ByteArray = engine.digest(data)
}

/**
 * CRC-16 implementation with configurable polynomial and parameters.
 *
 * Default configuration implements the X.25 CRC.
 *
 * Parameters:
 * @param poly generator polynomial (high bit implicit).
 * @param init initial CRC register value.
 * @param refin whether to reflect bits of each input byte.
 * @param refout whether to reflect the final CRC value.
 * @param xorOut final XOR mask.
 * @param width number of CRC bits (typically 16).
 *
 * Output:
 * - Returned as big-endian byte array.
 * - Internal representation uses reflected form.
 */
open class Crc16(
    poly: Int = 0x1021,
    init: Int = 0xFFFF,
    refin: Boolean = true,
    refout: Boolean = true,
    xorOut: Int = 0xFFFF,
    width: Int = 16
) : Checksum {

    companion object Default : Crc16()

    private val engine = CrcEngine(poly, init, refin, refout, xorOut, width)

    override val size: Int get() = engine.size

    override fun digest(data: ByteArray): ByteArray = engine.digest(data)
}

/**
 * CRC-32 implementation with configurable polynomial and parameters.
 *
 * Default configuration implements CRC-32/ISO-HDLC.
 *
 * Parameters:
 * @param poly generator polynomial (top bit implicit).
 * @param init initial CRC register value.
 * @param refin whether to reflect input bytes.
 * @param refout whether to reflect output CRC.
 * @param xorOut XOR mask applied to final CRC.
 * @param width number of CRC bits (typically 32).
 *
 * Output:
 * - Returned as big-endian byte array of length 4.
 * - Internal representation uses reflected form.
 */
open class Crc32(
    poly: Int = 0x04C11DB7,
    init: Int = 0xFFFFFFFF.toInt(),
    refin: Boolean = true,
    refout: Boolean = true,
    xorOut: Int = 0xFFFFFFFF.toInt(),
    width: Int = 32
) : Checksum {

    companion object Default : Crc32()

    private val engine = CrcEngine(poly, init, refin, refout, xorOut, width)

    override val size: Int get() = engine.size

    override fun digest(data: ByteArray): ByteArray = engine.digest(data)
}

/** Bit helpers */
private fun Int.reverseBits(width: Int): Int {
    var v = this
    var r = 0
    repeat(width) {
        r = (r shl 1) or (v and 1)
        v = v ushr 1
    }
    return r
}

private fun Int.reverseBits8(): Int {
    var v = this and 0xFF
    v = ((v and 0xF0) ushr 4) or ((v and 0x0F) shl 4)
    v = ((v and 0xCC) ushr 2) or ((v and 0x33) shl 2)
    v = ((v and 0xAA) ushr 1) or ((v and 0x55) shl 1)
    return v and 0xFF
}
