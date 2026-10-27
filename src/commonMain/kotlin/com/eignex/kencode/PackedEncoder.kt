package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Binary encoder for the [PackedFormat] wire format. Accumulates merged class bitmask
 * headers and per-field data, producing output consumable by [PackedDecoder].
 */
@OptIn(ExperimentalSerializationApi::class)
class PackedEncoder internal constructor(
    private val output: ByteOutput,
    private val config: PackedConfiguration = PackedConfiguration(),
    override val serializersModule: SerializersModule = EmptySerializersModule(),
    headerCtx: HeaderContext? = null,
) : Encoder,
    CompositeEncoder {

    private val ctx: HeaderContext = headerCtx ?: HeaderContext()
    private val isRoot: Boolean = headerCtx == null

    private var inStructure: Boolean = false
    private var layout: StructureLayout = StructureLayout.NONE
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    // Schema metadata: non-null for all non-collection structures.
    private lateinit var bitmask: ClassBitmask

    // Live bitmask values accumulated during encoding; written in endStructure.
    private lateinit var booleanValues: BooleanArray
    private lateinit var nullValues: BooleanArray
    private lateinit var enumValues: BooleanArray

    // Elements written so far. The decoder reads every element in order, so none may be skipped.
    private lateinit var encodedElements: BooleanArray

    // Start index in the shared HeaderContext for this class's reserved bits.
    private var headerStart: Int = -1

    // Null bitmap followed by packed boolean/enum values for LIST elements.
    private lateinit var collectionNullBits: BitOutput
    private lateinit var collectionValueBits: BitOutput

    // Buffer for field data.
    private val dataBuffer = ByteOutput()

    private fun child(headerCtx: HeaderContext? = null) =
        PackedEncoder(getBuffer(), config, serializersModule, headerCtx)

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (inStructure) {
            val shareCtx = shouldMergeChildCtx(
                layout.isMergedKind,
                layout.isCollection,
                currentDescriptor,
                currentIndex,
                descriptor,
            )
            return child(if (shareCtx) ctx else null).also { it.initializeStructure(descriptor) }
        }
        initializeStructure(descriptor)
        return this
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        writeVarInt(collectionSize, getBuffer())
        return child().also { it.initializeStructure(descriptor) }
    }

    private fun initializeStructure(descriptor: SerialDescriptor) {
        inStructure = true
        currentDescriptor = descriptor
        layout = StructureLayout.of(descriptor)

        if (!layout.isCollection) {
            bitmask = ClassBitmask(descriptor)
            booleanValues = BooleanArray(bitmask.boolCount)
            nullValues = BooleanArray(bitmask.nullCount)
            enumValues = BooleanArray(bitmask.enumBitCount)
            encodedElements = BooleanArray(descriptor.elementsCount)
            if (layout.isMergedKind) {
                headerStart = ctx.reserve(bitmask.totalCount)
            }
        } else {
            collectionNullBits = BitOutput()
            collectionValueBits = BitOutput()
        }

        dataBuffer.reset()
    }

    private fun getBuffer(): ByteOutput = if (inStructure) dataBuffer else output

    private val inClass: Boolean get() = inStructure && !layout.isCollection

    override fun encodeBoolean(value: Boolean) {
        when {
            inClass -> encodeBooleanElement(currentDescriptor, currentIndex, value)
            inStructure && layout.isPackedCollection -> collectionValueBits.writeBit(value)
            else -> getBuffer().write(if (value) 1 else 0)
        }
    }

    override fun encodeByte(value: Byte) {
        getBuffer().write(value.toInt() and 0xFF)
    }

    override fun encodeShort(value: Short) {
        writeShort(value, getBuffer())
    }

    override fun encodeInt(value: Int) {
        if (inClass) {
            encodeIntElement(currentDescriptor, currentIndex, value)
        } else {
            writePackedInt(value, config.defaultEncoding, getBuffer())
        }
    }

    override fun encodeLong(value: Long) {
        if (inClass) {
            encodeLongElement(currentDescriptor, currentIndex, value)
        } else {
            writePackedLong(value, config.defaultEncoding, getBuffer())
        }
    }

    override fun encodeFloat(value: Float) {
        writeInt(value.toBits(), getBuffer())
    }

    override fun encodeDouble(value: Double) {
        writeLong(value.toBits(), getBuffer())
    }

    override fun encodeChar(value: Char) {
        writeUtf8Char(value, getBuffer())
    }

    override fun encodeString(value: String) {
        val bytes = value.encodeToByteArray()
        val buf = getBuffer()
        writeVarInt(bytes.size, buf)
        buf.write(bytes)
    }

    @ExperimentalSerializationApi
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        require(
            index in 0 until enumDescriptor.elementsCount,
        ) { "Invalid enum ordinal $index for ${enumDescriptor.serialName}" }
        val width = enumBitWidth(enumDescriptor)
        val pos = if (inClass) bitmask.enumPos(currentIndex) else -1
        when {
            pos != -1 -> repeat(width) { bit -> enumValues[pos + bit] = (index and (1 shl bit)) != 0 }
            inStructure && layout.isPackedCollection -> collectionValueBits.write(index, width)
            else -> for (shift in 0 until width step 8) getBuffer().write(index ushr shift)
        }
    }

    @ExperimentalSerializationApi
    override fun encodeNull() = encodeNullMark(isNull = true)

    @ExperimentalSerializationApi
    override fun encodeNotNullMark() = encodeNullMark(isNull = false)

    private fun encodeNullMark(isNull: Boolean) {
        when {
            inStructure && layout.isNullableCollection -> collectionNullBits.writeBit(isNull)

            // Reached through a nullable serializer passed to encodeSerializableElement,
            // e.g. a generic `T` bound to a nullable type, or Pair/Triple components.
            inClass -> markNull(currentIndex, isNull)

            else -> writeVarLong(if (isNull) 1L else 0L, getBuffer())
        }
    }

    private fun markNull(index: Int, isNull: Boolean) {
        val pos = bitmask.nullablePos(index)
        if (pos != -1) {
            nullValues[pos] = isNull
        } else if (isNull) {
            error("Element $index is not declared nullable")
        }
    }

    @ExperimentalSerializationApi
    override fun encodeInline(descriptor: SerialDescriptor): Encoder =
        if (needsInlineBoundary(descriptor)) child() else this

    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        // ContextualSerializer resolves the real serializer through this call. Its payload
        // cannot contribute to a header derived from the parent's static descriptor.
        val target = if (inStructure && currentDescriptor.isContextualElement(currentIndex)) child() else this
        serializer.serialize(target, value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (!inStructure) return

        if (layout.isCollection) {
            // Collections: optional null bitmap, packed boolean/enum values, then data.
            collectionNullBits.writeTo(output)
            collectionValueBits.writeTo(output)
        } else if (layout.isMergedKind) {
            requireAllElementsEncoded(descriptor)
            // CLASS/OBJECT: fill reserved bits; root writes the single merged header.
            ctx.set(headerStart, booleanValues, nullValues, enumValues)
            if (isRoot) output.write(ctx.toByteArray())
        } else {
            requireAllElementsEncoded(descriptor)
            // Polymorphic/enum/other: write a local inline bitmask.
            bitmask.writeInlineBitmask(booleanValues, nullValues, output, enumValues)
        }

        dataBuffer.writeTo(output)
        inStructure = false
        layout = StructureLayout.NONE
        currentIndex = -1
    }

    // @EncodeDefault(NEVER) makes the plugin skip an element; the layout has no way to mark that.
    private fun requireAllElementsEncoded(descriptor: SerialDescriptor) {
        val skipped = encodedElements.indexOfFirst { !it }
        if (skipped != -1) {
            throw SerializationException(
                "Element '${descriptor.getElementName(skipped)}' of ${descriptor.serialName} was not encoded. " +
                    "PackedFormat encodes every element, so @EncodeDefault(NEVER) is not supported.",
            )
        }
    }

    private fun markEncoded(index: Int) {
        if (inClass && index >= 0) encodedElements[index] = true
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        markEncoded(index)
        if (layout.isCollection) {
            encodeBoolean(value)
            return
        }
        val pos = bitmask.booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        booleanValues[pos] = value
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        markEncoded(index)
        writePackedInt(value, resolveIntEncoding(descriptor.getElementAnnotations(index), config), dataBuffer)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        markEncoded(index)
        writePackedLong(value, resolveIntEncoding(descriptor.getElementAnnotations(index), config), dataBuffer)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        markEncoded(index)
        encodeByte(value)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        markEncoded(index)
        encodeShort(value)
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        markEncoded(index)
        encodeChar(value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        markEncoded(index)
        encodeFloat(value)
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        markEncoded(index)
        encodeDouble(value)
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        markEncoded(index)
        encodeString(value)
    }

    @ExperimentalSerializationApi
    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        markEncoded(index)
        currentIndex = index
        return encodeInline(descriptor.getElementDescriptor(index))
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T,
    ) {
        markEncoded(index)
        currentIndex = index
        serializer.serialize(this, value)
        currentIndex = -1
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?,
    ) {
        markEncoded(index)
        if (layout.isCollection) {
            encodeNullMark(isNull = value == null)
        } else {
            markNull(index, isNull = value == null)
        }
        if (value != null) encodeSerializableElement(descriptor, index, serializer, value)
    }

    private fun writeUtf8Char(value: Char, out: ByteOutput) {
        val cp = value.code
        when {
            cp < 0x80 -> out.write(cp)

            cp < 0x800 -> {
                out.write(0xC0 or (cp shr 6))
                out.write(0x80 or (cp and 0x3F))
            }

            else -> {
                out.write(0xE0 or (cp shr 12))
                out.write(0x80 or ((cp shr 6) and 0x3F))
                out.write(0x80 or (cp and 0x3F))
            }
        }
    }
}
