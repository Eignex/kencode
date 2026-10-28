package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind

/** Header bit positions of a descriptor's booleans, null markers, and enum ordinals. */
@OptIn(ExperimentalSerializationApi::class)
internal class ClassBitmask(descriptor: SerialDescriptor) {
    val boolCount: Int
    val nullCount: Int
    val enumBitCount: Int

    /** Field index to boolean bit, or -1. */
    val booleanLookup: IntArray

    /** Field index to null-marker bit, or -1. */
    val nullableLookup: IntArray

    /** Field index to first enum bit, or -1. */
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

    /** Rejects boolean or enum bits set on a null element. */
    fun requireNullElementsClear(
        descriptor: SerialDescriptor,
        booleanValues: BooleanArray,
        nullValues: BooleanArray,
        enumValues: BooleanArray,
    ) {
        for (i in 0 until descriptor.elementsCount) {
            val nullPos = nullablePos(i)
            if (nullPos == -1 || !nullValues[nullPos]) continue
            val boolPos = booleanPos(i)
            require(boolPos == -1 || !booleanValues[boolPos]) { "Null element $i has its boolean bit set" }
            val enumStart = enumPos(i)
            if (enumStart == -1) continue
            for (bit in 0 until enumBitWidth(descriptor.getElementDescriptor(i))) {
                require(!enumValues[enumStart + bit]) { "Null element $i has enum bits set" }
            }
        }
    }

    fun booleanPos(fieldIdx: Int): Int = booleanLookup.getOrElse(fieldIdx) { -1 }

    fun nullablePos(fieldIdx: Int): Int = nullableLookup.getOrElse(fieldIdx) { -1 }

    fun enumPos(fieldIdx: Int): Int = enumLookup.getOrElse(fieldIdx) { -1 }

    /** Writes a standalone header for structures outside the merged class header. */
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

/** Whether a child shares its parent's [HeaderContext]: a non-nullable, non-inline class field of a class. */
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

/** Header bits of a class and its merged nested classes, stored once at the root. */
internal class HeaderContext {
    private val bits = mutableListOf<Boolean>()
    private var readCursor = 0

    /** Reserves [count] bits and returns their start index. */
    fun reserve(count: Int): Int = bits.size.also { repeat(count) { bits.add(false) } }

    /** Fills bits from [start]: booleans, null markers, then enum bits. */
    fun set(start: Int, booleans: BooleanArray, nulls: BooleanArray, enums: BooleanArray = booleanArrayOf()) {
        var i = start
        booleans.forEach { bits[i++] = it }
        nulls.forEach { bits[i++] = it }
        enums.forEach { bits[i++] = it }
    }

    fun toByteArray(): ByteArray {
        val out = ByteOutput()
        BitOutput().apply { bits.forEach(::writeBit) }.writeTo(out)
        return out.toByteArray()
    }

    /** Appends [count] bits from [input] at [offset]; returns bytes consumed. */
    fun load(input: ByteArray, offset: Int, count: Int): Int {
        val flags = BitInput(input, offset, count.toLong())
        repeat(count) { bits.add(flags.readBit()) }
        return flags.byteCount
    }

    fun read(): Boolean {
        require(readCursor < bits.size) {
            "HeaderContext: no more bits to read (cursor=$readCursor, total=${bits.size})"
        }
        return bits[readCursor++]
    }
}

/** Header bits for [descriptor] and its merged nested classes. */
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

/** Whether an inline value needs its own encoder/decoder instead of its parent's. */
@OptIn(ExperimentalSerializationApi::class)
internal fun needsInlineBoundary(descriptor: SerialDescriptor): Boolean {
    var payload = descriptor
    while (payload.isInline) payload = payload.getElementDescriptor(0)
    return payload.isNullable || payload.kind == PrimitiveKind.BOOLEAN ||
        payload.kind is StructureKind || payload.kind is PolymorphicKind || payload.kind == SerialKind.CONTEXTUAL
}

/** CLASS or OBJECT, the kinds that share the merged header. */
internal val SerialDescriptor.isClassLike: Boolean
    get() = kind is StructureKind.CLASS || kind is StructureKind.OBJECT

internal val SerialDescriptor.isCollectionLike: Boolean
    get() = kind is StructureKind.LIST || kind is StructureKind.MAP

/** Whether element [index] resolves its serializer at runtime, keeping it out of the parent's header. */
@OptIn(ExperimentalSerializationApi::class)
internal fun SerialDescriptor.isContextualElement(index: Int): Boolean =
    index >= 0 && getElementDescriptor(index).kind == SerialKind.CONTEXTUAL

/** Element layout of a structure, derived from its descriptor. */
@OptIn(ExperimentalSerializationApi::class)
internal class StructureLayout private constructor(
    val isCollection: Boolean,
    val isMergedKind: Boolean,
    // List data is preceded by a null bitmap, then packed boolean/enum values.
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

/** Read state shared by a decoder and its children. */
internal class PackedPosition(var value: Int = 0) {
    /** Collection elements declared so far in this decode. */
    var collectionElements: Long = 0
}
