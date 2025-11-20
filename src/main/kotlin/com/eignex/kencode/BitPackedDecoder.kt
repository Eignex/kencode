package com.eignex.kencode

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@ExperimentalSerializationApi
class BitPackedDecoder(
    private val input: ByteArray
) : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var inStructure: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1
    private var position: Int = 0

    private var booleanIndices: IntArray = intArrayOf()
    private lateinit var booleanValues: BooleanArray

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (inStructure) error("Nested objects are not supported")
        inStructure = true
        currentDescriptor = descriptor

        val boolIdx = (0 until descriptor.elementsCount)
            .filter { descriptor.getElementDescriptor(it).kind == PrimitiveKind.BOOLEAN }
        booleanIndices = boolIdx.toIntArray()

        booleanValues =
            if (booleanIndices.isEmpty()) {
                BooleanArray(0)
            } else {
                val (flagsInt, bytesRead) = BitPacking.decodeVarInt(input, position)
                position += bytesRead
                BitPacking.unpackFlagsFromInt(flagsInt, booleanIndices.size)
            }

        return this
    }

    override fun decodeBoolean(): Boolean {
        val idx = currentIndex
        if (idx < 0) error("Boolean outside of structure is not supported")
        return decodeBooleanElement(currentDescriptor, idx)
    }

    override fun decodeByte(): Byte {
        val idx = currentIndex
        if (idx < 0) error("Byte outside of structure is not supported")
        return decodeByteElement(currentDescriptor, idx)
    }

    override fun decodeShort(): Short {
        val idx = currentIndex
        if (idx < 0) error("Short outside of structure is not supported")
        return decodeShortElement(currentDescriptor, idx)
    }

    override fun decodeInt(): Int {
        val idx = currentIndex
        if (idx < 0) error("Int outside of structure is not supported")
        return decodeIntElement(currentDescriptor, idx)
    }

    override fun decodeLong(): Long {
        val idx = currentIndex
        if (idx < 0) error("Long outside of structure is not supported")
        return decodeLongElement(currentDescriptor, idx)
    }

    override fun decodeFloat(): Float {
        val idx = currentIndex
        if (idx < 0) error("Float outside of structure is not supported")
        return decodeFloatElement(currentDescriptor, idx)
    }

    override fun decodeDouble(): Double {
        val idx = currentIndex
        if (idx < 0) error("Double outside of structure is not supported")
        return decodeDoubleElement(currentDescriptor, idx)
    }

    override fun decodeChar(): Char {
        val idx = currentIndex
        if (idx < 0) error("Char outside of structure is not supported")
        return decodeCharElement(currentDescriptor, idx)
    }

    override fun decodeString(): String {
        val idx = currentIndex
        if (idx < 0) error("String outside of structure is not supported")
        return decodeStringElement(currentDescriptor, idx)
    }

    @ExperimentalSerializationApi
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        error("Enums are not supported in this format")
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing {
        error("Null is not supported in this format")
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        // Nullable not supported; always treat as non-null (caller then fails if expecting null).
        return true
    }

    @ExperimentalSerializationApi
    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        if (!inStructure || currentIndex < 0) {
            error("Top-level inline or inline outside element is not supported")
        }
        return this
    }

    // ---------------------------------------------------------------------
    // CompositeDecoder API
    // ---------------------------------------------------------------------

    override fun endStructure(descriptor: SerialDescriptor) {
        inStructure = false
        currentIndex = -1
        booleanIndices = intArrayOf()
    }

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        CompositeDecoder.DECODE_DONE

    private fun booleanPos(index: Int): Int {
        for (i in booleanIndices.indices) {
            if (booleanIndices[i] == index) return i
        }
        return -1
    }

    override fun decodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Boolean {
        val pos = booleanPos(index)
        if (pos < 0) error("Boolean element at index $index not registered as boolean")
        return booleanValues[pos]
    }

    private fun readFixedIntLE(): Int {
        val b0 = input[position++].toInt() and 0xFF
        val b1 = input[position++].toInt() and 0xFF
        val b2 = input[position++].toInt() and 0xFF
        val b3 = input[position++].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readFixedLongLE(): Long {
        var result = 0L
        var shift = 0
        repeat(8) {
            val b = input[position++].toInt() and 0xFF
            result = result or ((b.toLong() and 0xFF) shl shift)
            shift += 8
        }
        return result
    }

    private fun List<Annotation>.hasVarInt(): Boolean = any { it is VarInt }
    private fun List<Annotation>.hasZigZag(): Boolean = any { it is ZigZag }

    override fun decodeIntElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Int {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasZigZag()
        val varInt = anns.hasVarInt() || zigZag

        return if (varInt) {
            val (raw, bytesRead) = BitPacking.decodeVarInt(input, position)
            position += bytesRead
            if (zigZag) BitPacking.zigZagDecodeInt(raw) else raw
        } else {
            readFixedIntLE()
        }
    }

    override fun decodeLongElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Long {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasZigZag()
        val varInt = anns.hasVarInt() || zigZag

        return if (varInt) {
            val (raw, bytesRead) = BitPacking.decodeVarLong(input, position)
            position += bytesRead
            if (zigZag) BitPacking.zigZagDecodeLong(raw) else raw
        } else {
            readFixedLongLE()
        }
    }

    override fun decodeByteElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Byte {
        return input[position++]
    }

    override fun decodeShortElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Short {
        val b0 = input[position++].toInt() and 0xFF
        val b1 = input[position++].toInt() and 0xFF
        val v = b0 or (b1 shl 8)
        return v.toShort()
    }

    override fun decodeCharElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Char {
        val b0 = input[position++].toInt() and 0xFF
        val b1 = input[position++].toInt() and 0xFF
        val v = b0 or (b1 shl 8)
        return v.toChar()
    }

    override fun decodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Float {
        val bits = readFixedIntLE()
        return java.lang.Float.intBitsToFloat(bits)
    }

    override fun decodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Double {
        val bits = readFixedLongLE()
        return java.lang.Double.longBitsToDouble(bits)
    }

    override fun decodeStringElement(
        descriptor: SerialDescriptor,
        index: Int
    ): String {
        val (len, bytesRead) = BitPacking.decodeVarInt(input, position)
        position += bytesRead
        val bytes = input.copyOfRange(position, position + len)
        position += len
        return bytes.toString(Charsets.UTF_8)
    }

    @ExperimentalSerializationApi
    override fun decodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Decoder {
        currentIndex = index
        return this
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        currentIndex = index

        val kind = deserializer.descriptor.kind
        if (kind is StructureKind.CLASS ||
            kind is StructureKind.OBJECT ||
            kind is StructureKind.LIST ||
            kind is StructureKind.MAP ||
            kind is PolymorphicKind
        ) {
            error("Nested objects/collections are not supported")
        }

        val value = deserializer.deserialize(this)
        currentIndex = -1
        return value
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T {
        error("Nullable elements are not supported in this format")
    }
}
