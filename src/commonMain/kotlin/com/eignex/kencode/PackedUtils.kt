package com.eignex.kencode

private fun requireAvailable(data: ByteArray, offset: Int, needed: Int, what: String) {
    require(offset >= 0 && needed >= 0 && offset + needed <= data.size) {
        "Unexpected EOF while decoding $what: need $needed bytes " + "from offset=$offset, size=${data.size}"
    }
}

internal fun zigZagEncodeInt(value: Int): Int = (value shl 1) xor (value shr 31)

internal fun zigZagDecodeInt(value: Int): Int = (value ushr 1) xor -(value and 1)

internal fun zigZagEncodeLong(value: Long): Long = (value shl 1) xor (value shr 63)

internal fun zigZagDecodeLong(value: Long): Long = (value ushr 1) xor -(value and 1L)

internal fun writePackedInt(value: Int, packing: IntPacking, out: ByteOutput) = when (packing) {
    IntPacking.SIGNED -> writeVarInt(zigZagEncodeInt(value), out)
    IntPacking.DEFAULT -> writeVarInt(value, out)
    IntPacking.FIXED -> writeInt(value, out)
}

internal fun writePackedLong(value: Long, packing: IntPacking, out: ByteOutput) = when (packing) {
    IntPacking.SIGNED -> writeVarLong(zigZagEncodeLong(value), out)
    IntPacking.DEFAULT -> writeVarLong(value, out)
    IntPacking.FIXED -> writeLong(value, out)
}

internal fun writeShort(value: Short, out: ByteOutput) {
    val v = value.toInt() and 0xFFFF
    out.write((v ushr 8) and 0xFF)
    out.write(v and 0xFF)
}

internal fun writeInt(value: Int, out: ByteOutput) {
    out.write((value ushr 24) and 0xFF)
    out.write((value ushr 16) and 0xFF)
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

internal fun writeLong(value: Long, out: ByteOutput) {
    out.write(((value ushr 56) and 0xFF).toInt())
    out.write(((value ushr 48) and 0xFF).toInt())
    out.write(((value ushr 40) and 0xFF).toInt())
    out.write(((value ushr 32) and 0xFF).toInt())
    out.write(((value ushr 24) and 0xFF).toInt())
    out.write(((value ushr 16) and 0xFF).toInt())
    out.write(((value ushr 8) and 0xFF).toInt())
    out.write((value and 0xFF).toInt())
}

internal fun readShort(data: ByteArray, offset: Int): Short {
    requireAvailable(data, offset, 2, "Short")
    val b0 = data[offset].toInt() and 0xFF
    val b1 = data[offset + 1].toInt() and 0xFF
    return ((b0 shl 8) or b1).toShort()
}

internal fun readInt(data: ByteArray, offset: Int): Int {
    requireAvailable(data, offset, 4, "Int")
    var v = 0
    for (i in 0 until 4) {
        v = (v shl 8) or (data[offset + i].toInt() and 0xFF)
    }
    return v
}

internal fun readLong(data: ByteArray, offset: Int): Long {
    requireAvailable(data, offset, 8, "Long")
    var v = 0L
    for (i in 0 until 8) {
        v = (v shl 8) or (data[offset + i].toLong() and 0xFFL)
    }
    return v
}

internal fun writeVarInt(value: Int, out: ByteOutput) {
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

internal fun writeVarLong(value: Long, out: ByteOutput) {
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

internal fun readVarInt(input: ByteArray, cursor: PackedPosition): Int {
    var result = 0
    var shift = 0
    while (true) {
        require(cursor.value < input.size) { "Unexpected EOF while decoding VarInt" }
        val b = input[cursor.value++].toInt() and 0xFF
        requireVarIntByte(b, shift)
        result = result or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
    }
}

internal fun readVarLong(input: ByteArray, cursor: PackedPosition): Long {
    var result = 0L
    var shift = 0
    while (true) {
        require(cursor.value < input.size) { "Unexpected EOF while decoding VarLong" }
        val b = input[cursor.value++].toInt() and 0xFF
        requireVarLongByte(b, shift)
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
    }
}

/** Rejects varint bytes the encoder never writes, so each value has one encoding. */
internal fun requireVarIntByte(b: Int, shift: Int) {
    require(shift == 0 || b != 0) { "Non-canonical VarInt" }
    require(shift < 28 || (b and 0xF0) == 0) { "VarInt too long" }
}

internal fun requireVarLongByte(b: Int, shift: Int) {
    require(shift == 0 || b != 0) { "Non-canonical VarLong" }
    require(shift < 63 || (b and 0xFE) == 0) { "VarLong too long" }
}
