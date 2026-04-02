package com.eignex.kencode

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalSerializationApi::class)
class PackedDecoder internal constructor(
    private val input: ByteArray,
    private val config: PackedConfiguration,
    override val serializersModule: SerializersModule,
    headerCtx: HeaderContext?,
) : Decoder, CompositeDecoder {

    constructor(
        input: ByteArray,
        config: PackedConfiguration = PackedConfiguration(),
        serializersModule: SerializersModule = EmptySerializersModule(),
    ) : this(input, config, serializersModule, null)

    private val ctx: HeaderContext = headerCtx ?: HeaderContext()
    private val isRoot: Boolean = headerCtx == null

    internal var position: Int = 0

    private var inStructure: Boolean = false
    private var isCollection: Boolean = false
    // True when the current structure uses the shared merged-header path (CLASS/OBJECT only).
    private var isMergedKind: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    // Schema metadata: non-null for all non-collection structures.
    private lateinit var bitmask: ClassBitmask

    // Bitmask state for CLASSES only
    private lateinit var booleanValues: BooleanArray
    private lateinit var nullValues: BooleanArray

    private var collectionSize = -1
    private var collectionIndex = 0

    // Bitmap state for nullable/boolean LIST elements
    private var isNullableCollection: Boolean = false
    private var isBooleanCollection: Boolean = false
    private var collectionNullBitmap: BooleanArray = booleanArrayOf()
    private var collectionBoolBitmap: BooleanArray = booleanArrayOf()
    private var nullBitmapIndex: Int = 0
    private var boolBitmapIndex: Int = 0

    // When a nullable-collection parent intercepts the null check for a complex element it
    // primes the sub-decoder so that NullableSerializer's own decodeNotNullMark call is a no-op.
    internal var skipNextNullMark: Boolean = false

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        inStructure = true
        currentDescriptor = descriptor

        val kind = descriptor.kind
        isCollection = kind is StructureKind.LIST || kind is StructureKind.MAP

        if (isCollection) {
            val isList = kind is StructureKind.LIST
            val elemDesc = descriptor.getElementDescriptor(0)
            isNullableCollection = isList && elemDesc.isNullable
            isBooleanCollection = isList && !isNullableCollection && elemDesc.kind == PrimitiveKind.BOOLEAN
            return this
        }

        isNullableCollection = false
        isBooleanCollection = false

        bitmask = ClassBitmask(descriptor)

        // Only CLASS and OBJECT use the shared merged-header context.
        // Polymorphic, enum, and other kinds read their own inline bitmask as before.
        isMergedKind = kind is StructureKind.CLASS || kind is StructureKind.OBJECT
        if (isMergedKind) {
            if (isRoot) {
                val totalBits = countAllBits(descriptor)
                position += ctx.load(input, position, totalBits)
            }
            booleanValues = BooleanArray(bitmask.boolCount) { ctx.read() }
            nullValues = BooleanArray(bitmask.nullCount) { ctx.read() }
        } else {
            val totalFlags = bitmask.totalCount
            if (totalFlags == 0) {
                booleanValues = BooleanArray(0)
                nullValues = BooleanArray(0)
            } else {
                val numBytes = (totalFlags + 7) / 8
                val allFlags = PackedUtils.unpackFlags(input, position, numBytes)
                position += numBytes
                booleanValues = BooleanArray(bitmask.boolCount) { i -> allFlags[i] }
                nullValues = BooleanArray(bitmask.nullCount) { i -> allFlags[bitmask.boolCount + i] }
            }
        }

        return this
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val size = readVarInt()
        collectionSize = size
        collectionIndex = 0
        nullBitmapIndex = 0
        boolBitmapIndex = 0
        if (size > 0) {
            if (isNullableCollection) {
                val numBytes = (size + 7) / 8
                collectionNullBitmap = PackedUtils.unpackFlags(input, position, numBytes)
                position += numBytes
            }
            if (isBooleanCollection) {
                val numBytes = (size + 7) / 8
                collectionBoolBitmap = PackedUtils.unpackFlags(input, position, numBytes)
                position += numBytes
            }
        }
        return size
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (isCollection) {
            return if (collectionIndex < collectionSize) collectionIndex++ else CompositeDecoder.DECODE_DONE
        }
        val nextIndex = currentIndex + 1
        return if (nextIndex < descriptor.elementsCount) {
            currentIndex = nextIndex
            nextIndex
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeSequentially(): Boolean = true

    override fun decodeBoolean(): Boolean {
        if (!inStructure || isCollection) {
            if (isCollection && isBooleanCollection) {
                val v = if (boolBitmapIndex < collectionBoolBitmap.size) collectionBoolBitmap[boolBitmapIndex] else false
                boolBitmapIndex++
                return v
            }
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

    override fun decodeInt(): Int =
        if (inStructure && !isCollection) decodeIntElement(currentDescriptor, currentIndex) else readIntPos()

    override fun decodeLong(): Long =
        if (inStructure && !isCollection) decodeLongElement(currentDescriptor, currentIndex) else readLongPos()

    override fun decodeFloat(): Float = Float.fromBits(readIntPos())

    override fun decodeDouble(): Double = Double.fromBits(readLongPos())

    override fun decodeChar(): Char = readUtf8Char()
    override fun decodeString(): String = readStringInline()

    private fun readStringInline(): String {
        val len = readVarInt()
        require(len >= 0 && position + len <= input.size)
        return input.decodeToString(position, position + len).also { position += len }
    }

    @ExperimentalSerializationApi
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = readVarInt()

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? = null

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        require(!inStructure || isCollection) {
            "decodeNotNullMark cannot be called inside classes. Use decodeNullableSerializableElement instead."
        }
        if (isNullableCollection) {
            val isNull = if (nullBitmapIndex < collectionNullBitmap.size) collectionNullBitmap[nullBitmapIndex] else false
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
    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    override fun endStructure(descriptor: SerialDescriptor) {
        inStructure = false
        isCollection = false
        isNullableCollection = false
        isBooleanCollection = false
        currentIndex = -1
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val pos = bitmask.booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        return booleanValues[pos]
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        require(position < input.size)
        return input[position++]
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = readShortPos()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
        when (resolveIntEncoding(descriptor.getElementAnnotations(index), config)) {
            IntEncoding.ZIGZAG -> PackedUtils.zigZagDecodeInt(readVarInt())
            IntEncoding.VARINT -> readVarInt()
            IntEncoding.FIXED  -> readIntPos()
        }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long =
        when (resolveIntEncoding(descriptor.getElementAnnotations(index), config)) {
            IntEncoding.ZIGZAG -> PackedUtils.zigZagDecodeLong(readVarLong())
            IntEncoding.VARINT -> readVarLong()
            IntEncoding.FIXED  -> readLongPos()
        }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
        Float.fromBits(readIntPos())

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        Double.fromBits(readLongPos())

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = readUtf8Char()

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = readStringInline()

    @ExperimentalSerializationApi
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
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
        val isInline = deserializer.descriptor.isInline

        if (isNullableCollection && deserializer.descriptor.isNullable && !isInline && (kind is StructureKind || kind is PolymorphicKind)) {
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

        if (!isInline && (kind is StructureKind || kind is PolymorphicKind)) {
            // Share the HeaderContext only when the current decoder is in merged mode
            // and the child is a non-nullable, non-inline CLASS/OBJECT in a non-collection context.
            val shouldShareCtx = shouldMergeChildCtx(isMergedKind, isCollection,
                descriptor, index, deserializer.descriptor)
            val subDecoder = PackedDecoder(
                input, config, serializersModule, if (shouldShareCtx) ctx else null
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
        previousValue: T?
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
            previousValue
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
            require(shift <= 35) { "VarInt too long" }
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
            require(shift <= 70) { "VarLong too long" }
        }
    }

    private fun readShortPos(): Short = PackedUtils.readShort(input, position).also { position += 2 }
    private fun readIntPos(): Int = PackedUtils.readInt(input, position).also { position += 4 }
    private fun readLongPos(): Long = PackedUtils.readLong(input, position).also { position += 8 }

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
                "Code points above U+FFFF (4-byte UTF-8 sequences) are not supported in Char fields; use String instead"
            )
        }
        position += len
        return cp.toChar()
    }
}
