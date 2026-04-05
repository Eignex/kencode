package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
class PackedEncoder internal constructor(
    private val output: ByteOutput,
    private val config: PackedConfiguration = PackedConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    headerCtx: HeaderContext? = null,
) : Encoder, CompositeEncoder {

    private val ctx: HeaderContext = headerCtx ?: HeaderContext()
    private val isRoot: Boolean = headerCtx == null

    private var inStructure: Boolean = false
    private var isCollection: Boolean = false
    // True when the current structure uses the shared merged-header path (CLASS/OBJECT only).
    private var isMergedKind: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    // Schema metadata: non-null for all non-collection structures.
    private lateinit var bitmask: ClassBitmask

    // Live bitmask values accumulated during encoding; written in endStructure.
    private lateinit var booleanValues: BooleanArray
    private lateinit var nullValues: BooleanArray
    // Start index in the shared HeaderContext for this class's reserved bits.
    private var headerStart: Int = -1

    // Bitmap state for nullable/boolean LIST elements.
    private var isNullableCollection: Boolean = false
    private var isBooleanCollection: Boolean = false
    private val collectionBitmapValues: MutableList<Boolean> = mutableListOf()

    // Buffer for field data.
    private val dataBuffer = ByteOutput()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (inStructure) {
            val childCtx = if (shouldMergeChildCtx(isMergedKind, isCollection,
                    currentDescriptor, currentIndex, descriptor)) ctx else null
            val childEncoder = PackedEncoder(dataBuffer, config, serializersModule, childCtx)
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
            bitmask = ClassBitmask(descriptor)
            booleanValues = BooleanArray(bitmask.boolCount)
            nullValues = BooleanArray(bitmask.nullCount)

            isNullableCollection = false
            isBooleanCollection = false

            isMergedKind = kind is StructureKind.CLASS || kind is StructureKind.OBJECT
            if (isMergedKind) {
                headerStart = ctx.reserve(bitmask.totalCount)
            }
        } else {
            val isList = kind is StructureKind.LIST
            val elemDesc = descriptor.getElementDescriptor(0)
            isNullableCollection = isList && elemDesc.isNullable
            isBooleanCollection = isList && !isNullableCollection && elemDesc.kind == PrimitiveKind.BOOLEAN
        }

        collectionBitmapValues.clear()
        dataBuffer.reset()
    }

    private fun getBuffer(): ByteOutput = if (inStructure) dataBuffer else output

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

    override fun encodeFloat(value: Float) { PackedUtils.writeInt(value.toBits(), getBuffer()) }

    override fun encodeDouble(value: Double) { PackedUtils.writeLong(value.toBits(), getBuffer()) }

    override fun encodeChar(value: Char) { writeUtf8Char(value, getBuffer()) }

    override fun encodeString(value: String) {
        val bytes = value.encodeToByteArray()
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

        // Collections: optional bitmap header then data.
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

        if (isMergedKind) {
            // CLASS/OBJECT: fill reserved bits; root writes the single merged header.
            ctx.set(headerStart, booleanValues, nullValues)
            if (isRoot) {
                val headerBytes = ctx.toByteArray()
                if (headerBytes.isNotEmpty()) output.write(headerBytes)
            }
        } else {
            // Polymorphic/enum/other: write a local inline bitmask.
            bitmask.writeInlineBitmask(booleanValues, nullValues, output)
        }

        dataBuffer.writeTo(output)
        inStructure = false
        currentIndex = -1
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        val pos = bitmask.booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        booleanValues[pos] = value
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        when (resolveIntEncoding(descriptor.getElementAnnotations(index), config)) {
            IntPacking.ZIGZAG -> PackedUtils.writeVarInt(PackedUtils.zigZagEncodeInt(value), dataBuffer)
            IntPacking.VARINT -> PackedUtils.writeVarInt(value, dataBuffer)
            IntPacking.FIXED  -> PackedUtils.writeInt(value, dataBuffer)
        }
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        when (resolveIntEncoding(descriptor.getElementAnnotations(index), config)) {
            IntPacking.ZIGZAG -> PackedUtils.writeVarLong(PackedUtils.zigZagEncodeLong(value), dataBuffer)
            IntPacking.VARINT -> PackedUtils.writeVarLong(value, dataBuffer)
            IntPacking.FIXED  -> PackedUtils.writeLong(value, dataBuffer)
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
        PackedUtils.writeInt(value.toBits(), dataBuffer)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        PackedUtils.writeLong(value.toBits(), dataBuffer)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        val bytes = value.encodeToByteArray()
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
            if (value == null) encodeNull() else { encodeNotNullMark(); encodeSerializableElement(descriptor, index, serializer, value) }
            return
        }

        val pos = bitmask.nullablePos(index)
        if (value == null) {
            if (pos == -1) error("Element $index is not declared nullable")
            nullValues[pos] = true
            return
        }
        if (pos != -1) nullValues[pos] = false
        encodeSerializableElement(descriptor, index, serializer, value)
    }

    private fun writeUtf8Char(value: Char, out: ByteOutput) {
        val cp = value.code
        when {
            cp < 0x80  -> out.write(cp)
            cp < 0x800 -> { out.write(0xC0 or (cp shr 6)); out.write(0x80 or (cp and 0x3F)) }
            else       -> { out.write(0xE0 or (cp shr 12)); out.write(0x80 or ((cp shr 6) and 0x3F)); out.write(0x80 or (cp and 0x3F)) }
        }
    }
}
