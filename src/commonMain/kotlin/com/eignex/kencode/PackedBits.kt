package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor

/** Number of bits needed for an enum ordinal; a single-entry enum needs no bits. */
@OptIn(ExperimentalSerializationApi::class)
internal fun enumBitWidth(descriptor: SerialDescriptor): Int =
    Int.SIZE_BITS - (descriptor.elementsCount - 1).coerceAtLeast(0).countLeadingZeroBits()

/** Appends fixed-width values least-significant bit first, padding only the final byte. */
internal class BitOutput {
    private val bytes = ByteOutput()
    private var pending = 0
    private var used = 0

    fun write(value: Int, bitCount: Int) {
        var remaining = bitCount
        var v = value
        while (remaining > 0) {
            val take = minOf(8 - used, remaining)
            pending = pending or ((v and ((1 shl take) - 1)) shl used)
            used += take
            remaining -= take
            v = v ushr take
            if (used == 8) {
                bytes.write(pending)
                pending = 0
                used = 0
            }
        }
    }

    fun writeBit(value: Boolean) = write(if (value) 1 else 0, 1)

    fun writeTo(output: ByteOutput) {
        bytes.writeTo(output)
        if (used != 0) output.write(pending)
    }
}

/** A bounded view of a packed bit sequence, without expanding it to a boolean array. */
internal class BitInput(private val input: ByteArray, private val offset: Int, private val bitCount: Long) {
    val byteCount: Int
    private var cursor = 0L

    init {
        require(bitCount >= 0 && offset in 0..input.size) { "Invalid packed bit range" }
        val needed = bitCount / 8 + if (bitCount % 8 == 0L) 0 else 1
        require(needed <= input.size - offset) { "Unexpected EOF reading packed values: need $needed bytes" }
        byteCount = needed.toInt()
    }

    fun read(count: Int): Int {
        require(count in 0..31 && count <= bitCount - cursor) { "Not enough packed bits remaining" }
        var value = 0
        var shift = 0
        while (shift < count) {
            val bit = (cursor % 8).toInt()
            val take = minOf(8 - bit, count - shift)
            val byte = input[offset + (cursor / 8).toInt()].toInt() and 0xFF
            value = value or (((byte ushr bit) and ((1 shl take) - 1)) shl shift)
            cursor += take
            shift += take
        }
        return value
    }

    fun readBit(): Boolean = read(1) != 0
}
