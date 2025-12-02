package com.eignex.kencode

import java.io.ByteArrayOutputStream

internal object PackedUtils {

    private fun requireAvailable(
        data: ByteArray, offset: Int, needed: Int, what: String
    ) {
        require(offset >= 0 && needed >= 0 && offset + needed <= data.size) {
            "Unexpected EOF while decoding $what: need $needed bytes " + "from offset=$offset, size=${data.size}"
        }
    }

    fun packFlagsToLong(vararg flags: Boolean): Long {
        var result = 0L
        for (i in flags.indices) {
            if (flags[i]) result = result or (1L shl i)
        }
        return result
    }

    fun unpackFlagsFromLong(bits: Long, count: Int): BooleanArray {
        val result = BooleanArray(count)
        for (i in 0 until count) {
            result[i] = (bits and (1L shl i)) != 0L
        }
        return result
    }

    fun zigZagEncodeInt(value: Int): Int = (value shl 1) xor (value shr 31)

    fun zigZagDecodeInt(value: Int): Int = (value ushr 1) xor -(value and 1)

    fun zigZagEncodeLong(value: Long): Long = (value shl 1) xor (value shr 63)

    fun zigZagDecodeLong(value: Long): Long = (value ushr 1) xor -(value and 1L)

    fun writeShort(value: Short, out: ByteArrayOutputStream) {
        val v = value.toInt() and 0xFFFF
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    fun writeInt(value: Int, out: ByteArrayOutputStream) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    fun writeLong(value: Long, out: ByteArrayOutputStream) {
        out.write(((value ushr 56) and 0xFF).toInt())
        out.write(((value ushr 48) and 0xFF).toInt())
        out.write(((value ushr 40) and 0xFF).toInt())
        out.write(((value ushr 32) and 0xFF).toInt())
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    fun readShort(data: ByteArray, offset: Int): Short {
        requireAvailable(data, offset, 2, "Short")
        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        return ((b0 shl 8) or b1).toShort()
    }

    fun readInt(data: ByteArray, offset: Int): Int {
        requireAvailable(data, offset, 4, "Int")
        var v = 0
        for (i in 0 until 4) {
            v = (v shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        return v
    }

    fun readLong(data: ByteArray, offset: Int): Long {
        requireAvailable(data, offset, 8, "Long")
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (data[offset + i].toLong() and 0xFFL)
        }
        return v
    }

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

    fun decodeVarInt(input: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var pos = offset
        while (true) {
            require(pos < input.size) {
                "Unexpected EOF while decoding VarInt"
            }
            val b = input[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) {
                return result to (pos - offset)
            }
            shift += 7
            require(shift <= 35) { "VarInt too long" }
        }
    }

    fun decodeVarLong(input: ByteArray, offset: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = offset
        while (true) {
            require(pos < input.size) {
                "Unexpected EOF while decoding VarLong"
            }
            val b = input[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) {
                return result to (pos - offset)
            }
            shift += 7
            require(shift <= 70) { "VarLong too long" }
        }
    }
}
