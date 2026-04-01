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

    // Bitmask state for CLASSES only
    private lateinit var booleanValues: BooleanArray
    private var booleanLookup: IntArray = intArrayOf()  // fieldIndex → bitmask position, or -1
    private lateinit var nullValues: BooleanArray
    private var nullableLookup: IntArray = intArrayOf()  // fieldIndex → bitmask position, or -1

    // Bitmap state for nullable/boolean LIST elements
    private var isNullableCollection: Boolean = false
    private var isBooleanCollection: Boolean = false
    private val collectionBitmapValues: MutableList<Boolean> = mutableListOf()

    // Buffer for field data
    private val dataBuffer = ByteArrayOutputStream()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (inStructure) {
            val childEncoder = PackedEncoder(dataBuffer, config, serializersModule)
            childEncoder.initializeStructure(descriptor)
            return childEncoder
        }
        initializeStructure(descriptor)
        return this
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        val target = if (inStructure) dataBuffer else output
        PackedUtils.writeVarInt(collectionSize, target)
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
            val boolIdx = (0 until descriptor.elementsCount).filter {
                descriptor.getElementDescriptor(it).kind == PrimitiveKind.BOOLEAN
            }
            booleanValues = BooleanArray(boolIdx.size)
            booleanLookup = IntArray(descriptor.elementsCount) { -1 }
            boolIdx.forEachIndexed { pos, fieldIdx -> booleanLookup[fieldIdx] = pos }

            val nullableIdx = (0 until descriptor.elementsCount).filter {
                descriptor.getElementDescriptor(it).isNullable
            }
            nullValues = BooleanArray(nullableIdx.size)
            nullableLookup = IntArray(descriptor.elementsCount) { -1 }
            nullableIdx.forEachIndexed { pos, fieldIdx -> nullableLookup[fieldIdx] = pos }

            isNullableCollection = false
            isBooleanCollection = false
        } else {
            booleanValues = BooleanArray(0)
            booleanLookup = intArrayOf()
            nullValues = BooleanArray(0)
            nullableLookup = intArrayOf()

            val isList = kind is StructureKind.LIST
            val elemDesc = descriptor.getElementDescriptor(0)
            isNullableCollection = isList && elemDesc.isNullable
            isBooleanCollection = isList && !isNullableCollection && elemDesc.kind == PrimitiveKind.BOOLEAN
        }

        collectionBitmapValues.clear()
        dataBuffer.reset()
    }

    private fun getBuffer(): ByteArrayOutputStream = if (inStructure) dataBuffer else output

    override fun encodeBoolean(value: Boolean) {
        when {
            inStructure && !isCollection       -> encodeBooleanElement(currentDescriptor, currentIndex, value)
            inStructure && isBooleanCollection -> collectionBitmapValues.add(value)
            else                               -> getBuffer().write(if (value) 1 else 0)
        }
    }

    override fun encodeByte(value: Byte) { getBuffer().write(value.toInt() and 0xFF) }

    override fun encodeShort(value: Short) { PackedUtils.writeShort(value, getBuffer()) }

    override fun encodeInt(value: Int) {
        if (inStructure && !isCollection) encodeIntElement(currentDescriptor, currentIndex, value)
        else PackedUtils.writeInt(value, getBuffer())
    }

    override fun encodeLong(value: Long) {
        if (inStructure && !isCollection) encodeLongElement(currentDescriptor, currentIndex, value)
        else PackedUtils.writeLong(value, getBuffer())
    }

    override fun encodeFloat(value: Float) {
        PackedUtils.writeInt(java.lang.Float.floatToRawIntBits(value), getBuffer())
    }

    override fun encodeDouble(value: Double) {
        PackedUtils.writeLong(java.lang.Double.doubleToRawLongBits(value), getBuffer())
    }

    override fun encodeChar(value: Char) { writeUtf8Char(value, getBuffer()) }

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
            if (isNullableCollection) collectionBitmapValues.add(true)
            else PackedUtils.writeVarLong(1L, dataBuffer)
        } else if (inStructure) {
            error("encodeNull should not be used inside Classes; use bitmask")
        } else {
            PackedUtils.writeVarLong(1L, output)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeNotNullMark() {
        if (inStructure && isCollection) {
            if (isNullableCollection) collectionBitmapValues.add(false)
            else PackedUtils.writeVarLong(0L, dataBuffer)
        } else if (!inStructure) {
            PackedUtils.writeVarLong(0L, output)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeInline(descriptor: SerialDescriptor): Encoder = this

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!inStructure) return

        // 1. Collections: optional bitmap header (null markers or bool values), then data
        if (isCollection) {
            if (collectionBitmapValues.isNotEmpty()) {
                val n = collectionBitmapValues.size
                val bitmap = ByteArray((n + 7) / 8)
                collectionBitmapValues.forEachIndexed { i, v ->
                    if (v) bitmap[i / 8] = (bitmap[i / 8].toInt() or (1 shl (i % 8))).toByte()
                }
                output.write(bitmap)
            }
            dataBuffer.writeTo(output)
            inStructure = false
            return
        }

        // 2. Classes: schema-derived fixed-width bitmask (8 bits/byte, no VarLong overhead), then data
        val totalFlagsCount = booleanValues.size + nullValues.size
        if (totalFlagsCount > 0) {
            val combined = BooleanArray(totalFlagsCount)
            booleanValues.copyInto(combined, 0)
            nullValues.copyInto(combined, booleanValues.size)
            val bitmap = ByteArray((totalFlagsCount + 7) / 8)
            combined.forEachIndexed { i, v ->
                if (v) bitmap[i / 8] = (bitmap[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
            output.write(bitmap)
        }

        dataBuffer.writeTo(output)
        inStructure = false
        currentIndex = -1
        booleanLookup = intArrayOf()
        nullableLookup = intArrayOf()
    }

    private fun booleanPos(index: Int): Int =
        if (index < booleanLookup.size) booleanLookup[index] else -1

    private fun nullablePos(index: Int): Int =
        if (index < nullableLookup.size) nullableLookup[index] else -1

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        val pos = booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        booleanValues[pos] = value
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        when (resolveIntEncoding(descriptor.getElementAnnotations(index), config)) {
            IntEncoding.ZIGZAG -> PackedUtils.writeVarInt(PackedUtils.zigZagEncodeInt(value), dataBuffer)
            IntEncoding.VARINT -> PackedUtils.writeVarInt(value, dataBuffer)
            IntEncoding.FIXED  -> PackedUtils.writeInt(value, dataBuffer)
        }
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        when (resolveIntEncoding(descriptor.getElementAnnotations(index), config)) {
            IntEncoding.ZIGZAG -> PackedUtils.writeVarLong(PackedUtils.zigZagEncodeLong(value), dataBuffer)
            IntEncoding.VARINT -> PackedUtils.writeVarLong(value, dataBuffer)
            IntEncoding.FIXED  -> PackedUtils.writeLong(value, dataBuffer)
        }
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        dataBuffer.write(value.toInt() and 0xFF)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        PackedUtils.writeShort(value, dataBuffer)
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        writeUtf8Char(value, dataBuffer)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        PackedUtils.writeInt(java.lang.Float.floatToRawIntBits(value), dataBuffer)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        PackedUtils.writeLong(java.lang.Double.doubleToRawLongBits(value), dataBuffer)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        PackedUtils.writeVarInt(bytes.size, dataBuffer)
        dataBuffer.write(bytes)
    }

    @ExperimentalSerializationApi
    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
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
            if (value == null) {
                encodeNull()
            } else {
                encodeNotNullMark()
                encodeSerializableElement(descriptor, index, serializer, value)
            }
            return
        }

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
        val cp = value.code
        when {
            cp < 0x80  -> out.write(cp)
            cp < 0x800 -> { out.write(0xC0 or (cp shr 6)); out.write(0x80 or (cp and 0x3F)) }
            else       -> { out.write(0xE0 or (cp shr 12)); out.write(0x80 or ((cp shr 6) and 0x3F)); out.write(0x80 or (cp and 0x3F)) }
        }
    }
}
