package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/**
 * Schema-derived boolean/nullable/enum metadata for a single class descriptor.
 *
 * Encapsulates the reverse-lookup arrays that both [PackedEncoder] and [PackedDecoder]
 * need to map a field index to its position in the bitmask, and the write path for inline
 * (non-merged) bitmasks used by polymorphic/enum structures.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class ClassBitmask(descriptor: SerialDescriptor) {
    val boolCount: Int
    val nullCount: Int
    val enumBitCount: Int

    /** Maps field index -> boolean-bit position, or -1 if not a boolean field. */
    val booleanLookup: IntArray

    /** Maps field index -> nullable-bit position, or -1 if not a nullable field. */
    val nullableLookup: IntArray

    /** Maps field index -> first enum bit, or -1 if not an enum field. */
    private val enumLookup: IntArray

    init {
        val n = descriptor.elementsCount
        var bc = 0
        var nc = 0
        booleanLookup = IntArray(n) { i ->
            val e = descriptor.getElementDescriptor(i)
            if (e.kind == PrimitiveKind.BOOLEAN) bc++ else -1
        }
        nullableLookup = IntArray(n) { i ->
            if (descriptor.getElementDescriptor(i).isNullable) nc++ else -1
        }
        boolCount = bc
        nullCount = nc
        var enumBits = 0
        enumLookup = IntArray(n) { i ->
            val e = descriptor.getElementDescriptor(i)
            if (e.kind == SerialKind.ENUM) {
                enumBits.also { enumBits += enumBitWidth(e) }
            } else {
                -1
            }
        }
        enumBitCount = enumBits
    }

    val totalCount: Int get() = boolCount + nullCount + enumBitCount

    fun booleanPos(fieldIdx: Int): Int = booleanLookup.getOrElse(fieldIdx) { -1 }

    fun nullablePos(fieldIdx: Int): Int = nullableLookup.getOrElse(fieldIdx) { -1 }

    fun enumPos(fieldIdx: Int): Int = enumLookup.getOrElse(fieldIdx) { -1 }

    /**
     * Packs booleans, null markers, then enum bits into a fixed-width byte array.
     * Used for inline (non-merged) bitmasks in polymorphic and other non-CLASS structures.
     */
    fun writeInlineBitmask(
        booleanValues: BooleanArray,
        nullValues: BooleanArray,
        out: ByteOutput,
        enumValues: BooleanArray = booleanArrayOf(),
    ) {
        val bits = BitOutput()
        booleanValues.forEach(bits::writeBit)
        nullValues.forEach(bits::writeBit)
        enumValues.forEach(bits::writeBit)
        bits.writeTo(out)
    }
}

/**
 * Returns true when the [HeaderContext] should be shared with the child encoder/decoder.
 * Sharing applies only when the parent is a merged-kind (CLASS/OBJECT) structure, not a collection,
 * and the child descriptor is a non-nullable, non-inline CLASS/OBJECT.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun shouldMergeChildCtx(
    parentIsMergedKind: Boolean,
    parentIsCollection: Boolean,
    parentDescriptor: SerialDescriptor,
    fieldIndex: Int,
    childDescriptor: SerialDescriptor,
): Boolean = parentIsMergedKind && !parentIsCollection && fieldIndex >= 0 &&
    !parentDescriptor.getElementDescriptor(fieldIndex).isNullable && !childDescriptor.isInline &&
    childDescriptor.isClassLike

/**
 * Accumulates bitmask bits across an entire nested-class hierarchy so that the
 * encoder can write (and the decoder can read) a single header at the root.
 */
internal class HeaderContext {
    private val bits = mutableListOf<Boolean>()
    private var readCursor = 0

    /** Reserves slots and returns the start index for a later set call. */
    fun reserve(count: Int): Int = bits.size.also { repeat(count) { bits.add(false) } }

    /** Fills the slots starting at [start] with booleans, null markers, then enum bits. */
    fun set(start: Int, booleans: BooleanArray, nulls: BooleanArray, enums: BooleanArray = booleanArrayOf()) {
        var i = start
        booleans.forEach { bits[i++] = it }
        nulls.forEach { bits[i++] = it }
        enums.forEach { bits[i++] = it }
    }

    /** Packs all reserved bits into a byte array. */
    fun toByteArray(): ByteArray {
        val out = ByteOutput()
        BitOutput().apply { bits.forEach(::writeBit) }.writeTo(out)
        return out.toByteArray()
    }

    /** Reads [count] bits from [input] at [offset] and appends them to this context. */
    fun load(input: ByteArray, offset: Int, count: Int): Int {
        val flags = BitInput(input, offset, count.toLong())
        repeat(count) { bits.add(flags.readBit()) }
        return flags.byteCount
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
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun countAllBits(descriptor: SerialDescriptor): Int {
    if (descriptor.isCollectionLike) return 0
    var count = 0
    for (i in 0 until descriptor.elementsCount) {
        val elem = descriptor.getElementDescriptor(i)
        if (elem.kind == PrimitiveKind.BOOLEAN) count++
        if (elem.kind == SerialKind.ENUM) count += enumBitWidth(elem)
        if (elem.isNullable) {
            count++
        } else if (!elem.isInline && elem.isClassLike) {
            count += countAllBits(elem)
        }
    }
    return count
}

/** Inline values that need their own flag or structure state instead of their parent's. */
@OptIn(ExperimentalSerializationApi::class)
internal fun needsInlineBoundary(descriptor: SerialDescriptor): Boolean {
    var payload = descriptor
    while (payload.isInline) payload = payload.getElementDescriptor(0)
    return payload.isNullable || payload.kind == PrimitiveKind.BOOLEAN ||
        payload.kind is StructureKind || payload.kind is PolymorphicKind || payload.kind == SerialKind.CONTEXTUAL
}

/** CLASS and OBJECT structures, the kinds whose bits go into the shared merged header. */
internal val SerialDescriptor.isClassLike: Boolean
    get() = kind is StructureKind.CLASS || kind is StructureKind.OBJECT

internal val SerialDescriptor.isCollectionLike: Boolean
    get() = kind is StructureKind.LIST || kind is StructureKind.MAP

/** True when element [index] resolves its serializer at runtime, so it cannot join the parent's header. */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.isContextualElement(index: Int): Boolean =
    index >= 0 && getElementDescriptor(index).kind == SerialKind.CONTEXTUAL

/** How a structure lays out its elements, derived from its descriptor. Shared by encoder and decoder. */
@OptIn(ExperimentalSerializationApi::class)
internal class StructureLayout private constructor(
    val isCollection: Boolean,
    // True when the structure uses the shared merged-header path (CLASS/OBJECT only).
    val isMergedKind: Boolean,
    // LIST elements are preceded by a null bitmap, then packed boolean/enum values.
    val isNullableCollection: Boolean,
    val isPackedCollection: Boolean,
    val collectionValueWidth: Int,
) {
    companion object {
        /** Layout outside any structure. */
        val NONE = StructureLayout(false, false, false, false, 0)

        fun of(descriptor: SerialDescriptor): StructureLayout {
            if (!descriptor.isCollectionLike) {
                return StructureLayout(false, descriptor.isClassLike, false, false, 0)
            }
            val isList = descriptor.kind is StructureKind.LIST
            val elem = descriptor.getElementDescriptor(0)
            return StructureLayout(
                isCollection = true,
                isMergedKind = false,
                isNullableCollection = isList && elem.isNullable,
                isPackedCollection = isList && (elem.kind == PrimitiveKind.BOOLEAN || elem.kind == SerialKind.ENUM),
                collectionValueWidth = when (elem.kind) {
                    PrimitiveKind.BOOLEAN -> 1
                    SerialKind.ENUM -> enumBitWidth(elem)
                    else -> 0
                },
            )
        }
    }
}

/** Shared by an inline decoder and its parent so primitive reads advance the same cursor. */
internal class PackedPosition(var value: Int = 0)
