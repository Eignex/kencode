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
class PackedEncoder(
    private val output: ByteArrayOutputStream
) : Encoder, CompositeEncoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var inStructure: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    private var booleanIndices: IntArray = intArrayOf()
    private lateinit var booleanValues: BooleanArray

    private var nullableIndices: IntArray = intArrayOf()
    private lateinit var nullValues: BooleanArray

    // Buffer for non-boolean field data (for structures)
    private val dataBuffer = ByteArrayOutputStream()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (inStructure) error("Nested objects are not supported")
        inStructure = true
        currentDescriptor = descriptor

        val boolIdx = (0 until descriptor.elementsCount).filter {
            descriptor.getElementDescriptor(it).kind == PrimitiveKind.BOOLEAN
        }
        booleanIndices = boolIdx.toIntArray()
        booleanValues = BooleanArray(booleanIndices.size)

        val nullableIdx = (0 until descriptor.elementsCount).filter {
            descriptor.getElementDescriptor(it).isNullable
        }
        nullableIndices = nullableIdx.toIntArray()
        nullValues = BooleanArray(nullableIndices.size)

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
            PackedUtils.writeShort(value, output)
        }
    }

    override fun encodeInt(value: Int) {
        if (inStructure) {
            encodeIntElement(currentDescriptor, currentIndex, value)
        } else {
            PackedUtils.writeInt(value, output)
        }
    }

    override fun encodeLong(value: Long) {
        if (inStructure) {
            encodeLongElement(currentDescriptor, currentIndex, value)
        } else {
            PackedUtils.writeLong(value, output)
        }
    }

    override fun encodeFloat(value: Float) {
        if (inStructure) {
            encodeFloatElement(currentDescriptor, currentIndex, value)
        } else {
            PackedUtils.writeInt(
                java.lang.Float.floatToRawIntBits(value), output
            )
        }
    }

    override fun encodeDouble(value: Double) {
        if (inStructure) {
            encodeDoubleElement(currentDescriptor, currentIndex, value)
        } else {
            PackedUtils.writeLong(
                java.lang.Double.doubleToRawLongBits(value), output
            )
        }
    }

    override fun encodeChar(value: Char) {
        if (inStructure) {
            encodeCharElement(currentDescriptor, currentIndex, value)
        } else {
            writeUtf8Char(value, output)
        }
    }

    override fun encodeString(value: String) {
        if (inStructure) {
            encodeStringElement(currentDescriptor, currentIndex, value)
        } else {
            val bytes = value.toByteArray(Charsets.UTF_8)
            PackedUtils.writeVarInt(bytes.size, output)
            output.write(bytes)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        if (inStructure) {
            PackedUtils.writeVarInt(index, dataBuffer)
        } else {
            PackedUtils.writeVarInt(index, output)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeNull() {
        if (inStructure) {
            error("encodeNull should not be used inside structures; nullable elements are encoded via the null bitmask")
        } else {
            PackedUtils.writeVarLong(1L, output)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeNotNullMark() {
        if (!inStructure) {
            PackedUtils.writeVarLong(0L, output)
        }
        // Inside structures, nullability is handled via the per-field null bitmask; no-op here.
    }

    @ExperimentalSerializationApi
    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        check(inStructure && descriptor == currentDescriptor)

        val totalFlagsCount = booleanValues.size + nullValues.size
        val flagsLong = if (totalFlagsCount == 0) {
            0
        } else {
            val combined = BooleanArray(totalFlagsCount)
            if (booleanValues.isNotEmpty()) {
                booleanValues.copyInto(combined, 0)
            }
            if (nullValues.isNotEmpty()) {
                nullValues.copyInto(combined, booleanValues.size)
            }
            PackedUtils.packFlagsToLong(*combined)
        }

        // Write varlong flags directly into final output
        PackedUtils.writeVarLong(flagsLong, output)

        // Then write the accumulated non-boolean field data
        output.write(dataBuffer.toByteArray())

        inStructure = false
        currentIndex = -1
        booleanIndices = intArrayOf()
        nullableIndices = intArrayOf()
    }

    private fun booleanPos(index: Int): Int {
        for (i in booleanIndices.indices) {
            if (booleanIndices[i] == index) return i
        }
        return -1
    }

    private fun nullablePos(index: Int): Int {
        for (i in nullableIndices.indices) {
            if (nullableIndices[i] == index) return i
        }
        return -1
    }

    override fun encodeBooleanElement(
        descriptor: SerialDescriptor, index: Int, value: Boolean
    ) {
        val pos = booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        booleanValues[pos] = value
    }

    override fun encodeIntElement(
        descriptor: SerialDescriptor, index: Int, value: Int
    ) {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasVarInt()
        val varInt = anns.hasVarUInt() || zigZag

        if (varInt) {
            val v = if (zigZag) PackedUtils.zigZagEncodeInt(value) else value
            PackedUtils.writeVarInt(v, dataBuffer)
        } else {
            PackedUtils.writeInt(value, dataBuffer)
        }
    }

    override fun encodeLongElement(
        descriptor: SerialDescriptor, index: Int, value: Long
    ) {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasVarInt()
        val varInt = anns.hasVarUInt() || zigZag

        if (varInt) {
            val v = if (zigZag) PackedUtils.zigZagEncodeLong(value) else value
            PackedUtils.writeVarLong(v, dataBuffer)
        } else {
            PackedUtils.writeLong(value, dataBuffer)
        }
    }

    override fun encodeByteElement(
        descriptor: SerialDescriptor, index: Int, value: Byte
    ) {
        dataBuffer.write(value.toInt() and 0xFF)
    }

    override fun encodeShortElement(
        descriptor: SerialDescriptor, index: Int, value: Short
    ) {
        PackedUtils.writeShort(value, dataBuffer)
    }

    override fun encodeCharElement(
        descriptor: SerialDescriptor, index: Int, value: Char
    ) {
        writeUtf8Char(value, dataBuffer)
    }

    override fun encodeFloatElement(
        descriptor: SerialDescriptor, index: Int, value: Float
    ) {
        PackedUtils.writeInt(
            java.lang.Float.floatToRawIntBits(value), dataBuffer
        )
    }

    override fun encodeDoubleElement(
        descriptor: SerialDescriptor, index: Int, value: Double
    ) {
        PackedUtils.writeLong(
            java.lang.Double.doubleToRawLongBits(value), dataBuffer
        )
    }

    override fun encodeStringElement(
        descriptor: SerialDescriptor, index: Int, value: String
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        PackedUtils.writeVarInt(bytes.size, dataBuffer)
        dataBuffer.write(bytes)
    }

    @ExperimentalSerializationApi
    override fun encodeInlineElement(
        descriptor: SerialDescriptor, index: Int
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

        if (!serializer.descriptor.isInline) {
            val kind = serializer.descriptor.kind
            if (kind is StructureKind.CLASS || kind is StructureKind.OBJECT || kind is StructureKind.LIST || kind is StructureKind.MAP || kind is PolymorphicKind) {
                error("Nested objects/collections are not supported")
            }
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
        val pos = nullablePos(index)
        if (value == null) {
            if (pos == -1) {
                error("Element $index is not declared nullable in descriptor")
            }
            nullValues[pos] = true
            // No payload written
            return
        }

        if (pos != -1) {
            nullValues[pos] = false
        }
        encodeSerializableElement(descriptor, index, serializer, value)
    }

    private fun writeUtf8Char(value: Char, out: ByteArrayOutputStream) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        out.write(bytes)
    }
}
