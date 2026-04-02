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
