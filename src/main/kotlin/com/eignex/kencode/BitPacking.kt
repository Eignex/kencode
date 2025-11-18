package com.eignex.kencode

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
class BitPacking {

    // flags -> int/long
    fun packFlagsToInt(vararg flags: Boolean): Int = TODO()
    fun unpackFlagsFromInt(bits: Int, count: Int): BooleanArray = TODO()

    // ZigZag for signed integers (for varints etc.)
    fun zigZagEncodeInt(value: Int): Int = TODO()
    fun zigZagDecodeInt(value: Int): Int = TODO()
    fun zigZagEncodeLong(value: Long): Long = TODO()
    fun zigZagDecodeLong(value: Long): Long = TODO()

    // Varints (LEB128-like or protobuf-style)
    fun encodeVarInt(value: Int): ByteArray = TODO()
    fun decodeVarInt(input: ByteArray, offset: Int = 0): Pair<Int /*value*/, Int /*bytesRead*/> = TODO()

    fun encodeVarLong(value: Long): ByteArray = TODO()
    fun decodeVarLong(input: ByteArray, offset: Int = 0): Pair<Long, Int> = TODO()
}
