package com.eignex.kencode

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Binary decoder for the [PackedFormat] wire format. Reads merged class bitmask headers
 * and per-field data produced by [PackedEncoder].
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
class PackedDecoder internal constructor(
    private val input: ByteArray,
    private val config: PackedConfiguration,
    override val serializersModule: SerializersModule,
    headerCtx: HeaderContext?,
    private val cursor: PackedPosition = PackedPosition(),
) : Decoder,
    CompositeDecoder {

    constructor(
        input: ByteArray,
        config: PackedConfiguration = PackedConfiguration(),
        serializersModule: SerializersModule = EmptySerializersModule(),
    ) : this(input, config, serializersModule, null)

    private val ctx: HeaderContext = headerCtx ?: HeaderContext()
    private val isRoot: Boolean = headerCtx == null

    internal var position: Int
        get() = cursor.value
        set(value) {
            cursor.value = value
        }

    private var inStructure: Boolean = false
    private var isCollection: Boolean = false

    // True when the current structure uses the shared merged-header path (CLASS/OBJECT only).
    private var isMergedKind: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1
    private var nextElementIndex: Int = 0

    // Schema metadata: non-null for all non-collection structures.
    private lateinit var bitmask: ClassBitmask

    // Bitmask state for CLASSES only
    private lateinit var booleanValues: BooleanArray
    private lateinit var nullValues: BooleanArray
    private lateinit var enumValues: BooleanArray

    private var collectionSize = -1
    private var collectionIndex = 0

    // Null bitmap followed by packed boolean/enum values for LIST elements.
    private var isNullableCollection: Boolean = false
    private var isPackedCollection: Boolean = false
    private var collectionValueWidth: Int = 0
    private var collectionNullBitmap: BooleanArray = booleanArrayOf()
    private lateinit var collectionValueBits: BitInput
    private var nullBitmapIndex: Int = 0

    // When a nullable-collection parent intercepts the null check for a complex element it
    // primes the sub-decoder so that NullableSerializer's own decodeNotNullMark call is a no-op.
    internal var skipNextNullMark: Boolean = false

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        inStructure = true
        currentDescriptor = descriptor
        nextElementIndex = 0

        val kind = descriptor.kind
        isCollection = kind is StructureKind.LIST || kind is StructureKind.MAP

        if (isCollection) {
            val isList = kind is StructureKind.LIST
            val elemDesc = descriptor.getElementDescriptor(0)
            isNullableCollection = isList && elemDesc.isNullable
            isPackedCollection =
                isList && (elemDesc.kind == PrimitiveKind.BOOLEAN || elemDesc.kind == SerialKind.ENUM)
            collectionValueWidth = when (elemDesc.kind) {
                PrimitiveKind.BOOLEAN -> 1
                SerialKind.ENUM -> enumBitWidth(elemDesc)
                else -> 0
            }
            return this
        }

        isNullableCollection = false
        isPackedCollection = false

        bitmask = ClassBitmask(descriptor)

        // Only CLASS and OBJECT use the shared merged-header context.
        // Polymorphic, enum, and other kinds read their own inline bitmask as before.
        isMergedKind =
            kind is StructureKind.CLASS || kind is StructureKind.OBJECT
        if (isMergedKind) {
            if (isRoot) {
                val totalBits = countAllBits(descriptor)
                position += ctx.load(input, position, totalBits)
            }
            booleanValues = BooleanArray(bitmask.boolCount) { ctx.read() }
            nullValues = BooleanArray(bitmask.nullCount) { ctx.read() }
            enumValues = BooleanArray(bitmask.enumBitCount) { ctx.read() }
        } else {
            val totalFlags = bitmask.totalCount
            if (totalFlags == 0) {
                booleanValues = BooleanArray(0)
                nullValues = BooleanArray(0)
                enumValues = BooleanArray(0)
            } else {
                val numBytes = (totalFlags + 7) / 8
                val allFlags =
                    unpackFlags(input, position, numBytes)
                position += numBytes
                booleanValues =
                    BooleanArray(bitmask.boolCount) { i -> allFlags[i] }
                nullValues =
                    BooleanArray(bitmask.nullCount) { i -> allFlags[bitmask.boolCount + i] }
                enumValues =
                    BooleanArray(bitmask.enumBitCount) { i -> allFlags[bitmask.boolCount + bitmask.nullCount + i] }
            }
        }

        return this
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val size = readVarInt()
        require(size >= 0) { "Negative collection size" }
        collectionSize = size
        collectionIndex = 0
        nullBitmapIndex = 0
        var valueCount = size
        if (isNullableCollection) {
            val numBytes = size / 8 + if (size % 8 == 0) 0 else 1
            collectionNullBitmap = unpackFlags(input, position, numBytes)
            position += numBytes
            for (i in 0 until size) if (collectionNullBitmap[i]) valueCount--
        }
        if (isPackedCollection) {
            collectionValueBits = BitInput(input, position, valueCount.toLong() * collectionValueWidth)
            position += collectionValueBits.byteCount
        }
        return size
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (isCollection) {
            return if (collectionIndex < collectionSize) collectionIndex++ else CompositeDecoder.DECODE_DONE
        }
        return if (nextElementIndex < descriptor.elementsCount) {
            currentIndex = nextElementIndex++
            currentIndex
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeSequentially(): Boolean = true

    override fun decodeBoolean(): Boolean {
        if (!inStructure || isCollection) {
            if (isCollection && isPackedCollection) return collectionValueBits.read(1) != 0
            require(position < input.size)
            return input[position++].toInt() != 0
        }
        return decodeBooleanElement(currentDescriptor, currentIndex)
    }

    override fun decodeByte(): Byte {
        require(position < input.size)
        return input[position++]
    }

    override fun decodeShort(): Short = readShortPos()

    override fun decodeInt(): Int = if (inStructure && !isCollection) {
        decodeIntElement(currentDescriptor, currentIndex)
    } else {
        when (config.defaultEncoding) {
            IntPacking.SIGNED -> zigZagDecodeInt(readVarInt())
            IntPacking.DEFAULT -> readVarInt()
            IntPacking.FIXED -> readIntPos()
        }
    }

    override fun decodeLong(): Long = if (inStructure && !isCollection) {
        decodeLongElement(currentDescriptor, currentIndex)
    } else {
        when (config.defaultEncoding) {
            IntPacking.SIGNED -> zigZagDecodeLong(readVarLong())
            IntPacking.DEFAULT -> readVarLong()
            IntPacking.FIXED -> readLongPos()
        }
    }

    override fun decodeFloat(): Float = Float.fromBits(readIntPos())

    override fun decodeDouble(): Double = Double.fromBits(readLongPos())

    override fun decodeChar(): Char = readUtf8Char()
    override fun decodeString(): String = readStringInline()

    private fun readStringInline(): String {
        val len = readVarInt()
        require(len >= 0 && position + len <= input.size)
        return input.decodeToString(position, position + len)
            .also { position += len }
    }

    @ExperimentalSerializationApi
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val width = enumBitWidth(enumDescriptor)
        val pos = if (inStructure && !isCollection) bitmask.enumPos(currentIndex) else -1
        val index = when {
            pos != -1 -> {
                var value = 0
                repeat(width) { bit -> if (enumValues[pos + bit]) value = value or (1 shl bit) }
                value
            }

            isCollection && isPackedCollection -> collectionValueBits.read(width)

            else -> {
                val bits = BitInput(input, position, width.toLong())
                position += bits.byteCount
                bits.read(width)
            }
        }
        require(index < enumDescriptor.elementsCount) { "Invalid enum ordinal $index for ${enumDescriptor.serialName}" }
        return index
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? = null

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        require(!inStructure || isCollection) {
            "decodeNotNullMark cannot be called inside classes. Use decodeNullableSerializableElement instead."
        }
        if (isNullableCollection) {
            val isNull =
                if (nullBitmapIndex < collectionNullBitmap.size) collectionNullBitmap[nullBitmapIndex] else false
            nullBitmapIndex++
            return !isNull
        }
        if (skipNextNullMark) {
            skipNextNullMark = false
            return true
        }
        return (readVarLong() and 1L) == 0L
    }

    @ExperimentalSerializationApi
    override fun decodeInline(descriptor: SerialDescriptor): Decoder =
        if (needsInlineBoundary(descriptor)) PackedDecoder(input, config, serializersModule, null, cursor) else this

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        val target = if (inStructure && currentIndex >= 0 &&
            currentDescriptor.getElementDescriptor(currentIndex).kind == SerialKind.CONTEXTUAL
        ) {
            PackedDecoder(input, config, serializersModule, null, cursor)
        } else {
            this
        }
        return deserializer.deserialize(target)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        inStructure = false
        isCollection = false
        isNullableCollection = false
        isPackedCollection = false
        currentIndex = -1
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (isCollection) return decodeBoolean()
        val pos = bitmask.booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        return booleanValues[pos]
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        require(position < input.size)
        return input[position++]
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = readShortPos()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int = when (
        resolveIntEncoding(
            descriptor.getElementAnnotations(index),
            config,
        )
    ) {
        IntPacking.SIGNED -> zigZagDecodeInt(readVarInt())
        IntPacking.DEFAULT -> readVarInt()
        IntPacking.FIXED -> readIntPos()
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long = when (
        resolveIntEncoding(
            descriptor.getElementAnnotations(index),
            config,
        )
    ) {
        IntPacking.SIGNED -> zigZagDecodeLong(readVarLong())
        IntPacking.DEFAULT -> readVarLong()
        IntPacking.FIXED -> readLongPos()
    }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = Float.fromBits(readIntPos())

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = Double.fromBits(readLongPos())

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = readUtf8Char()

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = readStringInline()

    @ExperimentalSerializationApi
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
        currentIndex = index
        return decodeInline(descriptor.getElementDescriptor(index))
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T {
        currentIndex = index

        val kind = deserializer.descriptor.kind
        val isInline = deserializer.descriptor.isInline
        val isStructural = kind is StructureKind || kind is PolymorphicKind

        if (isNullableCollection && deserializer.descriptor.isNullable && !isInline && isStructural) {
            val isNotNull = decodeNotNullMark()
            if (!isNotNull) {
                currentIndex = -1
                @Suppress("UNCHECKED_CAST")
                return decodeNull() as T
            }
            val subDecoder = PackedDecoder(input, config, serializersModule)
            subDecoder.position = this.position
            subDecoder.skipNextNullMark = true
            val value = deserializer.deserialize(subDecoder)
            this.position = subDecoder.position
            currentIndex = -1
            return value
        }

        if (!isInline && isStructural) {
            // Share the HeaderContext only when the current decoder is in merged mode
            // and the child is a non-nullable, non-inline CLASS/OBJECT in a non-collection context.
            val shouldShareCtx = shouldMergeChildCtx(
                isMergedKind,
                isCollection,
                descriptor,
                index,
                deserializer.descriptor,
            )
            val subDecoder = PackedDecoder(
                input,
                config,
                serializersModule,
                if (shouldShareCtx) ctx else null,
            )
            subDecoder.position = this.position
            val value = deserializer.deserialize(subDecoder)
            this.position = subDecoder.position
            currentIndex = -1
            return value
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
        previousValue: T?,
    ): T? {
        require(!isCollection) {
            "decodeNullableSerializableElement should not be called for collections."
        }

        val pos = bitmask.nullablePos(index)
        require(pos != -1) {
            "Element $index is not declared as nullable in the descriptor."
        }

        if (nullValues[pos]) return null

        return decodeSerializableElement(
            descriptor,
            index,
            deserializer as DeserializationStrategy<T>,
            previousValue,
        )
    }

    // Inline varint/varlong helpers: advance position directly, produce no Pair allocation
    private fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            require(position < input.size) { "Unexpected EOF while decoding VarInt" }
            val b = input[position++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            require(shift <= 28) { "VarInt too long" }
        }
    }

    private fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(position < input.size) { "Unexpected EOF while decoding VarLong" }
            val b = input[position++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            require(shift <= 63) { "VarLong too long" }
        }
    }

    private fun readShortPos(): Short = readShort(input, position).also { position += 2 }

    private fun readIntPos(): Int = readInt(input, position).also { position += 4 }

    private fun readLongPos(): Long = readLong(input, position).also { position += 8 }

    private fun readUtf8Char(): Char {
        require(position < input.size) { "Unexpected EOF while decoding UTF-8 char" }
        val b0 = input[position].toInt() and 0xFF
        val (len, cp) = when {
            (b0 and 0b1000_0000) == 0 -> 1 to b0

            (b0 and 0b1110_0000) == 0b1100_0000 -> {
                require(position + 2 <= input.size)
                val b1 = input[position + 1].toInt() and 0xFF
                require((b1 and 0b1100_0000) == 0b1000_0000) { "Invalid UTF-8 continuation byte" }
                2 to (((b0 and 0x1F) shl 6) or (b1 and 0x3F))
            }

            (b0 and 0b1111_0000) == 0b1110_0000 -> {
                require(position + 3 <= input.size)
                val b1 = input[position + 1].toInt() and 0xFF
                val b2 = input[position + 2].toInt() and 0xFF
                require((b1 and 0b1100_0000) == 0b1000_0000) { "Invalid UTF-8 continuation byte" }
                require((b2 and 0b1100_0000) == 0b1000_0000) { "Invalid UTF-8 continuation byte" }
                3 to (((b0 and 0x0F) shl 12) or ((b1 and 0x3F) shl 6) or (b2 and 0x3F))
            }

            else -> throw IllegalArgumentException(
                "Code points above U+FFFF (4-byte UTF-8 sequences) are not supported in Char fields; " +
                    "use String instead",
            )
        }
        position += len
        return cp.toChar()
    }
}
