package com.eignex.kencode

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
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
    private var layout: StructureLayout = StructureLayout.NONE
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
    private var collectionElementCount = 0L
    private var collectionIndex = 0

    // Null bitmap followed by packed boolean/enum values for LIST elements.
    private var collectionNullBitmap: BooleanArray = booleanArrayOf()
    private lateinit var collectionValueBits: BitInput
    private var nullBitmapIndex: Int = 0

    // When a nullable-collection parent intercepts the null check for a complex element it
    // primes the sub-decoder so that NullableSerializer's own decodeNotNullMark call is a no-op.
    internal var skipNextNullMark: Boolean = false

    /** A decoder for a value with its own structure state, reading from the same cursor. */
    private fun child(headerCtx: HeaderContext? = null) =
        PackedDecoder(input, config, serializersModule, headerCtx, cursor)

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        inStructure = true
        currentDescriptor = descriptor
        nextElementIndex = 0
        layout = StructureLayout.of(descriptor)
        if (layout.isCollection) {
            readCollectionHeader(elementsPerEntry = if (descriptor.kind is StructureKind.MAP) 2 else 1)
            return this
        }

        bitmask = ClassBitmask(descriptor)

        // Only CLASS and OBJECT use the shared merged-header context.
        // Polymorphic, enum, and other kinds read their own inline bitmask as before.
        if (layout.isMergedKind) {
            if (isRoot) position += ctx.load(input, position, countAllBits(descriptor))
            readBitmask(ctx::read)
        } else {
            val bits = BitInput(input, position, bitmask.totalCount.toLong())
            position += bits.byteCount
            readBitmask(bits::readBit)
        }

        return this
    }

    private fun readBitmask(next: () -> Boolean) {
        booleanValues = BooleanArray(bitmask.boolCount) { next() }
        nullValues = BooleanArray(bitmask.nullCount) { next() }
        enumValues = BooleanArray(bitmask.enumBitCount) { next() }
        bitmask.requireNullElementsClear(currentDescriptor, booleanValues, nullValues, enumValues)
    }

    // The size is read with the structure so decodeSequentially can see it before any allocation.
    private fun readCollectionHeader(elementsPerEntry: Int) {
        val size = readVarInt()
        require(size >= 0) { "Negative collection size" }
        collectionSize = size
        collectionElementCount = size.toLong() * elementsPerEntry
        require(collectionElementCount <= Int.MAX_VALUE) { "Map size $size exceeds the element index range" }
        collectionIndex = 0
        nullBitmapIndex = 0
        var valueCount = size
        if (layout.isNullableCollection) {
            val bits = BitInput(input, position, size.toLong())
            position += bits.byteCount
            collectionNullBitmap = BooleanArray(size) { bits.readBit() }
            valueCount -= collectionNullBitmap.count { it }
        }
        if (layout.isPackedCollection) {
            collectionValueBits = BitInput(input, position, valueCount.toLong() * layout.collectionValueWidth)
            position += collectionValueBits.byteCount
        }
    }

    // -1 (unknown) keeps kotlinx from preallocating a size that is not backed by input bytes.
    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        if (decodeSequentially()) collectionSize else -1

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (layout.isCollection) {
            return if (collectionIndex < collectionElementCount) collectionIndex++ else CompositeDecoder.DECODE_DONE
        }
        return if (nextElementIndex < descriptor.elementsCount) {
            currentIndex = nextElementIndex++
            currentIndex
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    // A collection claiming more elements than there are bytes left is read element by element
    // without preallocation, so a bogus size fails at EOF instead of exhausting memory. Lists of
    // zero-byte elements (Unit, packed booleans) still decode this way.
    override fun decodeSequentially(): Boolean = !layout.isCollection || collectionElementCount <= input.size - position

    private val inClass: Boolean get() = inStructure && !layout.isCollection

    override fun decodeBoolean(): Boolean = when {
        inClass -> decodeBooleanElement(currentDescriptor, currentIndex)

        layout.isPackedCollection -> collectionValueBits.readBit()

        else -> {
            val byte = readByte().toInt()
            require(byte == 0 || byte == 1) { "Invalid Boolean byte: $byte" }
            byte == 1
        }
    }

    override fun decodeByte(): Byte = readByte()

    override fun decodeShort(): Short = readShortPos()

    override fun decodeInt(): Int =
        if (inClass) decodeIntElement(currentDescriptor, currentIndex) else readPackedInt(config.defaultEncoding)

    override fun decodeLong(): Long =
        if (inClass) decodeLongElement(currentDescriptor, currentIndex) else readPackedLong(config.defaultEncoding)

    override fun decodeFloat(): Float = Float.fromBits(readIntPos())

    override fun decodeDouble(): Double = Double.fromBits(readLongPos())

    override fun decodeChar(): Char = readUtf8Char()

    override fun decodeString(): String {
        val len = readVarInt()
        require(len >= 0 && position + len <= input.size) { "Unexpected EOF while decoding String" }
        val value = try {
            input.decodeToString(position, position + len, throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            throw IllegalArgumentException("Invalid UTF-8 in String", e)
        }
        position += len
        return value
    }

    @ExperimentalSerializationApi
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val width = enumBitWidth(enumDescriptor)
        val pos = if (inClass) bitmask.enumPos(currentIndex) else -1
        val index = when {
            pos != -1 -> {
                var value = 0
                repeat(width) { bit -> if (enumValues[pos + bit]) value = value or (1 shl bit) }
                value
            }

            layout.isPackedCollection -> collectionValueBits.read(width)

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
        if (inClass) {
            // Reached through a nullable serializer passed to decodeSerializableElement.
            return !nullValues[requireNullablePos(currentIndex)]
        }
        if (layout.isNullableCollection) {
            val isNull = collectionNullBitmap.getOrElse(nullBitmapIndex) { false }
            nullBitmapIndex++
            return !isNull
        }
        if (skipNextNullMark) {
            skipNextNullMark = false
            return true
        }
        val mark = readVarLong()
        require(mark == 0L || mark == 1L) { "Invalid null marker: $mark" }
        return mark == 0L
    }

    private fun requireNullablePos(index: Int): Int {
        val pos = bitmask.nullablePos(index)
        require(pos != -1) { "Element $index is not declared as nullable in the descriptor." }
        return pos
    }

    @ExperimentalSerializationApi
    override fun decodeInline(descriptor: SerialDescriptor): Decoder =
        if (needsInlineBoundary(descriptor)) child() else this

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        val target = if (inStructure && currentDescriptor.isContextualElement(currentIndex)) child() else this
        return deserializer.deserialize(target)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        inStructure = false
        layout = StructureLayout.NONE
        currentIndex = -1
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (layout.isCollection) return decodeBoolean()
        val pos = bitmask.booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        return booleanValues[pos]
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte = decodeByte()

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = decodeShort()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int =
        readPackedInt(resolveIntEncoding(descriptor.getElementAnnotations(index), config))

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long =
        readPackedLong(resolveIntEncoding(descriptor.getElementAnnotations(index), config))

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float = decodeFloat()

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double = decodeDouble()

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = decodeChar()

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = decodeString()

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

        val childDescriptor = deserializer.descriptor
        val isStructural = !childDescriptor.isInline &&
            (childDescriptor.kind is StructureKind || childDescriptor.kind is PolymorphicKind)

        // Nullable structural values in a nullable list or a class read their null marker from
        // the parent's bitmap, so the sub-decoder must not read another one.
        val parentTracksNull = layout.isNullableCollection || !layout.isCollection

        val value = when {
            !isStructural -> deserializer.deserialize(this)

            parentTracksNull && childDescriptor.isNullable ->
                if (decodeNotNullMark()) {
                    deserializer.deserialize(child().also { it.skipNextNullMark = true })
                } else {
                    decodeNull() as T
                }

            // Share the HeaderContext only when the current decoder is in merged mode
            // and the child is a non-nullable, non-inline CLASS/OBJECT in a non-collection context.
            else -> {
                val shareCtx = shouldMergeChildCtx(
                    layout.isMergedKind,
                    layout.isCollection,
                    descriptor,
                    index,
                    childDescriptor,
                )
                deserializer.deserialize(child(if (shareCtx) ctx else null))
            }
        }
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
        require(!layout.isCollection) {
            "decodeNullableSerializableElement should not be called for collections."
        }
        if (nullValues[requireNullablePos(index)]) return null

        return decodeSerializableElement(
            descriptor,
            index,
            deserializer as DeserializationStrategy<T>,
            previousValue,
        )
    }

    private fun readPackedInt(packing: IntPacking): Int = when (packing) {
        IntPacking.SIGNED -> zigZagDecodeInt(readVarInt())
        IntPacking.DEFAULT -> readVarInt()
        IntPacking.FIXED -> readIntPos()
    }

    private fun readPackedLong(packing: IntPacking): Long = when (packing) {
        IntPacking.SIGNED -> zigZagDecodeLong(readVarLong())
        IntPacking.DEFAULT -> readVarLong()
        IntPacking.FIXED -> readLongPos()
    }

    private fun readByte(): Byte {
        require(position < input.size) { "Unexpected EOF while decoding Byte" }
        return input[position++]
    }

    private fun readVarInt(): Int = readVarInt(input, cursor)

    private fun readVarLong(): Long = readVarLong(input, cursor)

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
                val cp = ((b0 and 0x1F) shl 6) or (b1 and 0x3F)
                require(cp >= 0x80) { "Overlong UTF-8 encoding" }
                2 to cp
            }

            (b0 and 0b1111_0000) == 0b1110_0000 -> {
                require(position + 3 <= input.size)
                val b1 = input[position + 1].toInt() and 0xFF
                val b2 = input[position + 2].toInt() and 0xFF
                require((b1 and 0b1100_0000) == 0b1000_0000) { "Invalid UTF-8 continuation byte" }
                require((b2 and 0b1100_0000) == 0b1000_0000) { "Invalid UTF-8 continuation byte" }
                val cp = ((b0 and 0x0F) shl 12) or ((b1 and 0x3F) shl 6) or (b2 and 0x3F)
                require(cp >= 0x800) { "Overlong UTF-8 encoding" }
                3 to cp
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
