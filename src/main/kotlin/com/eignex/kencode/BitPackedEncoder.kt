package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.*
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

    // Buffer for non-boolean field data (for structures)
    private val dataBuffer = ByteArrayOutputStream()

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
        if (inStructure) {
            encodeBooleanElement(currentDescriptor, currentIndex, value)
        } else {
            output.write(if (value) 1 else 0)
        }
    }

    override fun encodeByte(value: Byte) {
        if (inStructure) {
            encodeByteElement(currentDescriptor, currentIndex, value)
        } else {
            output.write(value.toInt() and 0xFF)
        }
    }

    override fun encodeShort(value: Short) {
        if (inStructure) {
            encodeShortElement(currentDescriptor, currentIndex, value)
        } else {
            BitPacking.writeShort(value, output)
        }
    }

    override fun encodeInt(value: Int) {
        if (inStructure) {
            encodeIntElement(currentDescriptor, currentIndex, value)
        } else {
            BitPacking.writeInt(value, output)
        }
    }

    override fun encodeLong(value: Long) {
        if (inStructure) {
            encodeLongElement(currentDescriptor, currentIndex, value)
        } else {
            BitPacking.writeLong(value, output)
        }
    }

    override fun encodeFloat(value: Float) {
        if (inStructure) {
            encodeFloatElement(currentDescriptor, currentIndex, value)
        } else {
            BitPacking.writeInt(
                java.lang.Float.floatToRawIntBits(value),
                output
            )
        }
    }

    override fun encodeDouble(value: Double) {
        if (inStructure) {
            encodeDoubleElement(currentDescriptor, currentIndex, value)
        } else {
            BitPacking.writeLong(
                java.lang.Double.doubleToRawLongBits(value),
                output
            )
        }
    }

    override fun encodeChar(value: Char) {
        if (inStructure) {
            encodeCharElement(currentDescriptor, currentIndex, value)
        } else {
            BitPacking.writeShort(value.code.toShort(), output)
        }
    }

    override fun encodeString(value: String) {
        if (inStructure) {
            encodeStringElement(currentDescriptor, currentIndex, value)
        } else {
            val bytes = value.toByteArray(Charsets.UTF_8)
            BitPacking.writeVarInt(bytes.size, output)
            output.write(bytes)
        }
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

    override fun endStructure(descriptor: SerialDescriptor) {
        check(inStructure && descriptor == currentDescriptor)

        val flagsInt =
            if (booleanIndices.isEmpty()) 0
            else BitPacking.packFlagsToInt(*booleanValues)

        // Write varint flags directly into final output
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
        booleanValues[pos] = value
    }

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
            BitPacking.writeVarInt(v, dataBuffer)
        } else {
            BitPacking.writeInt(value, dataBuffer)
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
            BitPacking.writeVarLong(v, dataBuffer)
        } else {
            BitPacking.writeLong(value, dataBuffer)
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
        BitPacking.writeShort(value, dataBuffer)
    }

    override fun encodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Char
    ) {
        BitPacking.writeShort(value.code.toShort(), dataBuffer)
    }

    override fun encodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Float
    ) {
        BitPacking.writeInt(java.lang.Float.floatToRawIntBits(value), dataBuffer)
    }

    override fun encodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Double
    ) {
        BitPacking.writeLong(java.lang.Double.doubleToRawLongBits(value), dataBuffer)
    }

    override fun encodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: String
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        BitPacking.writeVarInt(bytes.size, dataBuffer)
        dataBuffer.write(bytes)
    }

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
