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
    private val output: ByteArrayOutputStream,
    private val config: PackedConfiguration = PackedConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : Encoder, CompositeEncoder {

    private var inStructure: Boolean = false
    private var isCollection: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    // Bitmask arrays for CLASSES only
    private var booleanIndices: IntArray = intArrayOf()
    private lateinit var booleanValues: BooleanArray
    private var nullableIndices: IntArray = intArrayOf()
    private lateinit var nullValues: BooleanArray

    // Buffer for field data
    private val dataBuffer = ByteArrayOutputStream()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (inStructure) {
            val childEncoder =
                PackedEncoder(dataBuffer, config, serializersModule)
            childEncoder.initializeStructure(descriptor)
            return childEncoder
        }
        initializeStructure(descriptor)
        return this
    }

    override fun beginCollection(
        descriptor: SerialDescriptor,
        collectionSize: Int
    ): CompositeEncoder {
        // Write size to the current context's buffer
        val target = if (inStructure) dataBuffer else output
        PackedUtils.writeVarInt(collectionSize, target)

        // Spawn new encoder for the collection items
        val childEncoder = PackedEncoder(target, config, serializersModule)
        childEncoder.initializeStructure(descriptor)
        return childEncoder
    }

    private fun initializeStructure(descriptor: SerialDescriptor) {
        inStructure = true
        currentDescriptor = descriptor

        val kind = descriptor.kind
        isCollection = kind is StructureKind.LIST || kind is StructureKind.MAP

        if (!isCollection) {
            // Calculate indices for Class bitmasks
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
        } else {
            // Collections do not use bitmasks
            booleanIndices = intArrayOf()
            booleanValues = BooleanArray(0)
            nullableIndices = intArrayOf()
            nullValues = BooleanArray(0)
        }

        dataBuffer.reset()
    }

    private fun getBuffer(): ByteArrayOutputStream =
        if (inStructure) dataBuffer else output

    override fun encodeBoolean(value: Boolean) {
        if (inStructure && !isCollection) {
            encodeBooleanElement(currentDescriptor, currentIndex, value)
        } else {
            // Top-level or Collection elements are written directly
            getBuffer().write(if (value) 1 else 0)
        }
    }

    override fun encodeByte(value: Byte) {
        getBuffer().write(value.toInt() and 0xFF)
    }

    override fun encodeShort(value: Short) {
        PackedUtils.writeShort(value, getBuffer())
    }

    override fun encodeInt(value: Int) {
        if (inStructure && !isCollection) {
            encodeIntElement(currentDescriptor, currentIndex, value)
        } else {
            PackedUtils.writeInt(value, getBuffer())
        }
    }

    override fun encodeLong(value: Long) {
        if (inStructure && !isCollection) {
            encodeLongElement(currentDescriptor, currentIndex, value)
        } else {
            PackedUtils.writeLong(value, getBuffer())
        }
    }

    override fun encodeFloat(value: Float) {
        PackedUtils.writeInt(
            java.lang.Float.floatToRawIntBits(value),
            getBuffer()
        )
    }

    override fun encodeDouble(value: Double) {
        PackedUtils.writeLong(
            java.lang.Double.doubleToRawLongBits(value),
            getBuffer()
        )
    }

    override fun encodeChar(value: Char) {
        writeUtf8Char(value, getBuffer())
    }

    override fun encodeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val buf = getBuffer()
        PackedUtils.writeVarInt(bytes.size, buf)
        buf.write(bytes)
    }

    @ExperimentalSerializationApi
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        PackedUtils.writeVarInt(index, getBuffer())
    }

    @ExperimentalSerializationApi
    override fun encodeNull() {
        if (inStructure && isCollection) {
            // Inline null marker for collections (1 = null)
            PackedUtils.writeVarLong(1L, dataBuffer)
        } else if (inStructure) {
            error("encodeNull should not be used inside Classes; use bitmask")
        } else {
            PackedUtils.writeVarLong(1L, output)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeNotNullMark() {
        if (inStructure && isCollection) {
            PackedUtils.writeVarLong(0L, dataBuffer)
        } else if (!inStructure) {
            PackedUtils.writeVarLong(0L, output)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!inStructure) return

        // 1. Collections: No header, just data
        if (isCollection) {
            output.write(dataBuffer.toByteArray())
            inStructure = false
            return
        }

        // 2. Classes: Write Bitmask Header -> Data
        val totalFlagsCount = booleanValues.size + nullValues.size
        if (totalFlagsCount > 0) {
            val combined = BooleanArray(totalFlagsCount)
            if (booleanValues.isNotEmpty()) booleanValues.copyInto(combined, 0)
            if (nullValues.isNotEmpty()) nullValues.copyInto(
                combined,
                booleanValues.size
            )

            if (totalFlagsCount > 64) {
                val flagBytes = PackedUtils.packFlags(combined)
                PackedUtils.writeVarInt(flagBytes.size, output)
                output.write(flagBytes)
            } else {
                val flagsLong = PackedUtils.packFlagsToLong(*combined)
                PackedUtils.writeVarLong(flagsLong, output)
            }
        }

        output.write(dataBuffer.toByteArray())

        inStructure = false
        currentIndex = -1
        booleanIndices = intArrayOf()
        nullableIndices = intArrayOf()
    }

    private fun booleanPos(index: Int): Int {
        for (i in booleanIndices.indices) if (booleanIndices[i] == index) return i
        return -1
    }

    private fun nullablePos(index: Int): Int {
        for (i in nullableIndices.indices) if (nullableIndices[i] == index) return i
        return -1
    }

    override fun encodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Boolean
    ) {
        val pos = booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        booleanValues[pos] = value
    }

    override fun encodeIntElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Int
    ) {
        val anns = descriptor.getElementAnnotations(index)
        val hasFixedInt = anns.hasFixedInt()
        val hasVarInt = anns.hasVarInt()
        val hasVarUInt = anns.hasVarUInt()

        val isVar = when {
            hasFixedInt -> false
            hasVarInt || hasVarUInt -> true
            else -> config.defaultVarInt || config.defaultZigZag
        }

        val zigZag = when {
            hasVarInt -> true
            hasVarUInt || hasFixedInt -> false
            else -> config.defaultZigZag
        }

        if (isVar) {
            val v = if (zigZag) PackedUtils.zigZagEncodeInt(value) else value
            PackedUtils.writeVarInt(v, dataBuffer)
        } else {
            PackedUtils.writeInt(value, dataBuffer)
        }
    }

    override fun encodeLongElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Long
    ) {
        val anns = descriptor.getElementAnnotations(index)
        val hasFixedInt = anns.hasFixedInt()
        val hasVarInt = anns.hasVarInt()
        val hasVarUInt = anns.hasVarUInt()

        val isVar = when {
            hasFixedInt -> false
            hasVarInt || hasVarUInt -> true
            else -> config.defaultVarInt || config.defaultZigZag
        }

        val zigZag = when {
            hasVarInt -> true
            hasVarUInt || hasFixedInt -> false
            else -> config.defaultZigZag
        }

        if (isVar) {
            val v = if (zigZag) PackedUtils.zigZagEncodeLong(value) else value
            PackedUtils.writeVarLong(v, dataBuffer)
        } else {
            PackedUtils.writeLong(value, dataBuffer)
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
        PackedUtils.writeShort(value, dataBuffer)
    }

    override fun encodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Char
    ) {
        writeUtf8Char(value, dataBuffer)
    }

    override fun encodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Float
    ) {
        PackedUtils.writeInt(
            java.lang.Float.floatToRawIntBits(value),
            dataBuffer
        )
    }

    override fun encodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: Double
    ) {
        PackedUtils.writeLong(
            java.lang.Double.doubleToRawLongBits(value),
            dataBuffer
        )
    }

    override fun encodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
        value: String
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        PackedUtils.writeVarInt(bytes.size, dataBuffer)
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
        if (isCollection) {
            // Collections use inline markers
            if (value == null) {
                encodeNull()
            } else {
                encodeNotNullMark()
                encodeSerializableElement(descriptor, index, serializer, value)
            }
            return
        }

        // Classes use bitmask
        val pos = nullablePos(index)
        if (value == null) {
            if (pos == -1) error("Element $index is not declared nullable")
            nullValues[pos] = true
            return
        }
        if (pos != -1) nullValues[pos] = false
        encodeSerializableElement(descriptor, index, serializer, value)
    }

    private fun writeUtf8Char(value: Char, out: ByteArrayOutputStream) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        out.write(bytes)
    }
}
