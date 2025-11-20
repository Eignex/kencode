package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalSerializationApi::class)
class BitPackedEncoder(
    private val output: ByteArrayOutputStream
) : Encoder, CompositeEncoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var inStructure: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    private var booleanIndices: IntArray = intArrayOf()
    private lateinit var booleanValues: BooleanArray

    // Buffer for non-boolean field data
    private val dataBuffer = ByteArrayOutputStream()

    // ---------------------------------------------------------------------
    // Encoder (single-value) API
    // ---------------------------------------------------------------------

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (inStructure) error("Nested objects are not supported")
        inStructure = true
        currentDescriptor = descriptor

        val boolIdx = (0 until descriptor.elementsCount)
            .filter { descriptor.getElementDescriptor(it).kind == PrimitiveKind.BOOLEAN }

        booleanIndices = boolIdx.toIntArray()
        booleanValues = BooleanArray(booleanIndices.size)
        dataBuffer.reset()

        return this
    }

    override fun encodeBoolean(value: Boolean) {
        val idx = currentIndex
        if (idx < 0) error("Boolean outside of structure is not supported")
        encodeBooleanElement(currentDescriptor, idx, value)
    }

    override fun encodeByte(value: Byte) {
        val idx = currentIndex
        if (idx < 0) error("Byte outside of structure is not supported")
        encodeByteElement(currentDescriptor, idx, value)
    }

    override fun encodeShort(value: Short) {
        val idx = currentIndex
        if (idx < 0) error("Short outside of structure is not supported")
        encodeShortElement(currentDescriptor, idx, value)
    }

    override fun encodeInt(value: Int) {
        val idx = currentIndex
        if (idx < 0) error("Int outside of structure is not supported")
        encodeIntElement(currentDescriptor, idx, value)
    }

    override fun encodeLong(value: Long) {
        val idx = currentIndex
        if (idx < 0) error("Long outside of structure is not supported")
        encodeLongElement(currentDescriptor, idx, value)
    }

    override fun encodeFloat(value: Float) {
        val idx = currentIndex
        if (idx < 0) error("Float outside of structure is not supported")
        encodeFloatElement(currentDescriptor, idx, value)
    }

    override fun encodeDouble(value: Double) {
        val idx = currentIndex
        if (idx < 0) error("Double outside of structure is not supported")
        encodeDoubleElement(currentDescriptor, idx, value)
    }

    override fun encodeChar(value: Char) {
        val idx = currentIndex
        if (idx < 0) error("Char outside of structure is not supported")
        encodeCharElement(currentDescriptor, idx, value)
    }

    override fun encodeString(value: String) {
        val idx = currentIndex
        if (idx < 0) error("String outside of structure is not supported")
        encodeStringElement(currentDescriptor, idx, value)
    }

    @ExperimentalSerializationApi
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        error("Enums are not supported in this format")
    }

    @ExperimentalSerializationApi
    override fun encodeNull() {
        error("Null is not supported in this format")
    }

    @ExperimentalSerializationApi
    override fun encodeNotNullMark() {
        // null not supported
    }

    @ExperimentalSerializationApi
    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        if (!inStructure || currentIndex < 0) {
            error("Top-level inline or inline outside element is not supported")
        }
        return this
    }

    // ---------------------------------------------------------------------
    // CompositeEncoder API
    // ---------------------------------------------------------------------

    override fun endStructure(descriptor: SerialDescriptor) {
        check(inStructure && descriptor == currentDescriptor)

        val flagsInt =
            if (booleanIndices.isEmpty()) 0
            else BitPacking.packFlagsToInt(*booleanValues)

        // Write varint flags directly into final output without temp ByteArray
        BitPacking.writeVarInt(flagsInt, output)

        // Then write the accumulated non-boolean field data
        output.write(dataBuffer.toByteArray())

        inStructure = false
        currentIndex = -1
        booleanIndices = intArrayOf()
    }

    private fun booleanPos(index: Int): Int {
        for (i in booleanIndices.indices) {
            if (booleanIndices[i] == index) return i
        }
        return -1
    }

    override fun encodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Boolean
    ) {
        val pos = booleanPos(index)
        if (pos < 0) error("Boolean element at index $index not registered as boolean")
        booleanValues[pos] = value
    }

    // ---------------------------------------------------------------------
    // Helpers for annotations and fixed-size primitives
    // ---------------------------------------------------------------------

    private fun List<Annotation>.hasVarInt(): Boolean = any { it is VarInt }
    private fun List<Annotation>.hasZigZag(): Boolean = any { it is ZigZag }

    private fun writeFixedIntLE(value: Int) {
        dataBuffer.write(value and 0xFF)
        dataBuffer.write((value ushr 8) and 0xFF)
        dataBuffer.write((value ushr 16) and 0xFF)
        dataBuffer.write((value ushr 24) and 0xFF)
    }

    private fun writeFixedLongLE(value: Long) {
        var v = value
        repeat(8) {
            dataBuffer.write((v and 0xFF).toInt())
            v = v ushr 8
        }
    }

    // ---------------------------------------------------------------------
    // Primitive element encoders
    // ---------------------------------------------------------------------

    override fun encodeIntElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Int
    ) {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasZigZag()
        val varInt = anns.hasVarInt() || zigZag

        if (varInt) {
            val v = if (zigZag) BitPacking.zigZagEncodeInt(value) else value
            // Directly write varint into dataBuffer
            BitPacking.writeVarInt(v, dataBuffer)
        } else {
            writeFixedIntLE(value)
        }
    }

    override fun encodeLongElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Long
    ) {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasZigZag()
        val varInt = anns.hasVarInt() || zigZag

        if (varInt) {
            val v = if (zigZag) BitPacking.zigZagEncodeLong(value) else value
            // Directly write varint into dataBuffer
            BitPacking.writeVarLong(v, dataBuffer)
        } else {
            writeFixedLongLE(value)
        }
    }

    override fun encodeByteElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Byte
    ) {
        dataBuffer.write(value.toInt() and 0xFF)
    }

    override fun encodeShortElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Short
    ) {
        val v = value.toInt()
        dataBuffer.write(v and 0xFF)
        dataBuffer.write((v ushr 8) and 0xFF)
    }

    override fun encodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Char
    ) {
        val v = value.code
        dataBuffer.write(v and 0xFF)
        dataBuffer.write((v ushr 8) and 0xFF)
    }

    override fun encodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Float
    ) {
        writeFixedIntLE(java.lang.Float.floatToRawIntBits(value))
    }

    override fun encodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Double
    ) {
        writeFixedLongLE(java.lang.Double.doubleToRawLongBits(value))
    }

    override fun encodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: String
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        // Write length as varint directly to dataBuffer
        BitPacking.writeVarInt(bytes.size, dataBuffer)
        dataBuffer.write(bytes)
    }

    // ---------------------------------------------------------------------
    // Inline / nested serializers
    // ---------------------------------------------------------------------

    @ExperimentalSerializationApi
    override fun encodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Encoder {
        currentIndex = index
        return this
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        currentIndex = index

        val kind = serializer.descriptor.kind
        if (kind is StructureKind.CLASS ||
            kind is StructureKind.OBJECT ||
            kind is StructureKind.LIST ||
            kind is StructureKind.MAP ||
            kind is PolymorphicKind
        ) {
            error("Nested objects/collections are not supported")
        }

        serializer.serialize(this, value)
        currentIndex = -1
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        if (value == null) {
            error("Null values are not supported in this format")
        }
        encodeSerializableElement(descriptor, index, serializer, value)
    }
}
