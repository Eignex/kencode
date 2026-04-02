package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*

/**
 * Schema-derived boolean/nullable metadata for a single class descriptor.
 *
 * Encapsulates the two reverse-lookup arrays that both [PackedEncoder] and [PackedDecoder]
 * need to map a field index to its position in the bitmask, and the write path for inline
 * (non-merged) bitmasks used by polymorphic/enum structures.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class ClassBitmask(descriptor: SerialDescriptor) {
    val boolCount: Int
    val nullCount: Int
    /** Maps field index → boolean-bit position, or -1 if not a boolean field. */
    val booleanLookup: IntArray
    /** Maps field index → nullable-bit position, or -1 if not a nullable field. */
    val nullableLookup: IntArray

    init {
        val n = descriptor.elementsCount
        var bc = 0; var nc = 0
        val bl = IntArray(n) { -1 }
        val nl = IntArray(n) { -1 }
        for (i in 0 until n) {
            val e = descriptor.getElementDescriptor(i)
            if (e.kind == PrimitiveKind.BOOLEAN) bl[i] = bc++
            if (e.isNullable)                    nl[i] = nc++
        }
        boolCount = bc; nullCount = nc
        booleanLookup = bl; nullableLookup = nl
    }

    val totalCount: Int get() = boolCount + nullCount

    fun booleanPos(fieldIdx: Int): Int =
        if (fieldIdx < booleanLookup.size) booleanLookup[fieldIdx] else -1

    fun nullablePos(fieldIdx: Int): Int =
        if (fieldIdx < nullableLookup.size) nullableLookup[fieldIdx] else -1

    /**
     * Packs [booleanValues] (length [boolCount]) followed by [nullValues] (length [nullCount])
     * into a fixed-width schema-derived byte array and writes it to [out].
     * Used for inline (non-merged) bitmasks in polymorphic and other non-CLASS structures.
     */
    fun writeInlineBitmask(booleanValues: BooleanArray, nullValues: BooleanArray, out: ByteOutput) {
        val n = totalCount
        if (n == 0) return
        val bytes = ByteArray((n + 7) / 8)
        booleanValues.forEachIndexed { i, v ->
            if (v) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
        }
        nullValues.forEachIndexed { i, v ->
            val bit = boolCount + i
            if (v) bytes[bit / 8] = (bytes[bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        }
        out.write(bytes)
    }
}

/**
 * Returns true when the [HeaderContext] should be shared with the child encoder/decoder for
 * the field at [fieldIndex] in [parentDescriptor].
 *
 * Sharing applies only when:
 * - the parent is a merged-kind (CLASS/OBJECT) structure and not a collection,
 * - the child descriptor is a non-nullable, non-inline CLASS/OBJECT.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun shouldMergeChildCtx(
    parentIsMergedKind: Boolean,
    parentIsCollection: Boolean,
    parentDescriptor: SerialDescriptor,
    fieldIndex: Int,
    childDescriptor: SerialDescriptor,
): Boolean = parentIsMergedKind &&
    !parentIsCollection &&
    fieldIndex >= 0 &&
    !parentDescriptor.getElementDescriptor(fieldIndex).isNullable &&
    !childDescriptor.isInline &&
    (childDescriptor.kind is StructureKind.CLASS || childDescriptor.kind is StructureKind.OBJECT)

/**
 * Accumulates bitmask bits across an entire nested-class hierarchy so that the
 * encoder can write (and the decoder can read) a single header at the root.
 *
 * Encoder usage:
 *   - Each class calls [reserve] during `initializeStructure` to claim contiguous slots.
 *   - Each class calls [set] during `endStructure` to fill in the actual values.
 *   - The root class calls [toByteArray] to obtain the merged header bytes.
 *
 * Decoder usage:
 *   - The root class calls [load] once to populate all bits from the wire bytes.
 *   - Each nested class calls [read] for each of its own bits in declaration order.
 */
internal class HeaderContext {
    private val bits: MutableList<Boolean> = mutableListOf()
    private var readCursor: Int = 0

    /** Reserves [count] slots and returns the start index for a later [set] call. */
    fun reserve(count: Int): Int {
        val idx = bits.size
        repeat(count) { bits.add(false) }
        return idx
    }

    /** Fills the slots starting at [start] with [booleans] immediately followed by [nulls]. */
    fun set(start: Int, booleans: BooleanArray, nulls: BooleanArray) {
        var i = start
        booleans.forEach { bits[i++] = it }
        nulls.forEach { bits[i++] = it }
    }

    /** Packs all reserved bits into a byte array (bit 0 = LSB of byte 0). */
    fun toByteArray(): ByteArray {
        val n = bits.size
        if (n == 0) return ByteArray(0)
        val bytes = ByteArray((n + 7) / 8)
        bits.forEachIndexed { i, v ->
            if (v) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
        }
        return bytes
    }

    /**
     * Reads [count] bits from [input] at [offset] and appends them to this context.
     * Returns the number of bytes consumed.
     */
    fun load(input: ByteArray, offset: Int, count: Int): Int {
        if (count == 0) return 0
        val numBytes = (count + 7) / 8
        val flags = PackedUtils.unpackFlags(input, offset, numBytes)
        repeat(count) { bits.add(if (it < flags.size) flags[it] else false) }
        return numBytes
    }

    /** Returns the next bit from the sequential read cursor. */
    fun read(): Boolean {
        require(readCursor < bits.size) {
            "HeaderContext: no more bits to read (cursor=$readCursor, total=${bits.size})"
        }
        return bits[readCursor++]
    }
}

/**
 * Recursively counts the total number of bitmask bits required by [descriptor] and all
 * non-nullable, non-inline nested CLASS/OBJECT fields.
 *
 * - Boolean fields each contribute one bit (the value bit).
 * - Nullable fields each contribute one bit (the null-marker bit); their nested fields
 *   are **not** merged — those classes keep a local header.
 * - Non-nullable, non-inline CLASS/OBJECT fields contribute the result of a recursive call.
 * - Collections, maps, polymorphic types, and inline classes contribute zero bits.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun countAllBits(descriptor: SerialDescriptor): Int {
    if (descriptor.kind is StructureKind.LIST || descriptor.kind is StructureKind.MAP) return 0
    var count = 0
    for (i in 0 until descriptor.elementsCount) {
        val elem = descriptor.getElementDescriptor(i)
        if (elem.kind == PrimitiveKind.BOOLEAN) count++
        if (elem.isNullable) count++
        else if (!elem.isInline &&
            (elem.kind is StructureKind.CLASS || elem.kind is StructureKind.OBJECT)
        ) {
            count += countAllBits(elem)
        }
    }
    return count
}

internal object PackedUtils {

    private fun requireAvailable(
        data: ByteArray, offset: Int, needed: Int, what: String
    ) {
        require(offset >= 0 && needed >= 0 && offset + needed <= data.size) {
            "Unexpected EOF while decoding $what: need $needed bytes " + "from offset=$offset, size=${data.size}"
        }
    }

    fun packFlags(flags: BooleanArray): ByteArray {
        // 1. Find the last byte that actually contains a 'true' value.
        var lastTrueIndex = -1
        for (i in flags.indices) {
            if (flags[i]) lastTrueIndex = i
        }

        if (lastTrueIndex == -1) {
            return ByteArray(0) // All false -> 0 bytes
        }

        // 2. Calculate exact number of bytes needed (1-based)
        // e.g. lastTrueIndex = 0 (1st bit) -> 1 byte
        // e.g. lastTrueIndex = 8 (9th bit) -> 2 bytes
        val numBytes = (lastTrueIndex / 8) + 1
        val bytes = ByteArray(numBytes)

        // 3. Pack bits (8 per byte)
        for (i in 0..lastTrueIndex) {
            if (flags[i]) {
                val byteIndex = i / 8
                val bitIndex = i % 8
                bytes[byteIndex] =
                    (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return bytes
    }

    fun unpackFlags(input: ByteArray, offset: Int, length: Int): BooleanArray {
        // Guard against OOB
        require(offset + length <= input.size) {
            "Unexpected EOF reading flags: need $length bytes"
        }

        // We return an array exactly sized to the bits we have.
        // The Decoder will handle padding this out to the full schema size.
        val totalBits = length * 8
        val result = BooleanArray(totalBits)

        for (i in 0 until totalBits) {
            val byteIndex = i / 8
            val bitIndex = i % 8
            val byteVal = input[offset + byteIndex].toInt()

            val isSet = (byteVal and (1 shl bitIndex)) != 0
            result[i] = isSet
        }

        return result
    }

    fun packFlagsToLong(flags: BooleanArray): Long {
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

    fun writeShort(value: Short, out: ByteOutput) {
        val v = value.toInt() and 0xFFFF
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    fun writeInt(value: Int, out: ByteOutput) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    fun writeLong(value: Long, out: ByteOutput) {
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

    fun writeVarInt(value: Int, out: ByteOutput) {
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

    fun writeVarLong(value: Long, out: ByteOutput) {
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
