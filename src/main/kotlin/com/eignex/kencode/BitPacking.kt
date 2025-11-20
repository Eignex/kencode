package com.eignex.kencode

import java.io.ByteArrayOutputStream

/**
 * Utility for bit-level packing and encoding primitives.
 *
 * Functions include:
 * - Boolean flag packing/unpacking into integers.
 * - ZigZag encoding for signed integers (as used in protobuf and varints).
 * - Varint and varlong (LEB128-style) encoding/decoding.
 *
 * All methods are placeholders and must be implemented.
 */
object BitPacking {
    // flags -> int/long
    fun packFlagsToInt(vararg flags: Boolean): Int {
        var result = 0
        for (i in flags.indices) {
            if (flags[i]) result = result or (1 shl i)
        }
        return result
    }

    fun unpackFlagsFromInt(bits: Int, count: Int): BooleanArray {
        val result = BooleanArray(count)
        for (i in 0 until count) {
            result[i] = (bits and (1 shl i)) != 0
        }
        return result
    }

    // ZigZag for signed integers (for varints etc.)
    fun zigZagEncodeInt(value: Int): Int =
        (value shl 1) xor (value shr 31)

    fun zigZagDecodeInt(value: Int): Int =
        (value ushr 1) xor -(value and 1)

    fun zigZagEncodeLong(value: Long): Long =
        (value shl 1) xor (value shr 63)

    fun zigZagDecodeLong(value: Long): Long =
        (value ushr 1) xor -(value and 1L)

    fun writeVarInt(value: Int, out: ByteArrayOutputStream) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.write(v)
                return
            } else {
                out.write((v and 0x7F) or 0x80)
                v = v ushr 7
            }
        }
    }

    fun writeVarLong(value: Long, out: ByteArrayOutputStream) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            } else {
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }

    fun decodeVarInt(input: ByteArray, offset: Int = 0): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = offset
        while (true) {
            val b = input[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) {
                return result to (pos - offset)
            }
            shift += 7
            if (shift > 35) error("VarInt too long")
        }
    }

    fun decodeVarLong(input: ByteArray, offset: Int = 0): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (true) {
            val b = input[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) {
                return result to (pos - offset)
            }
            shift += 7
            if (shift > 70) error("VarLong too long")
        }
    }
}
