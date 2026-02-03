package com.eignex.kencode

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
class PackedDecoder(
    private val input: ByteArray
) : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    internal var position: Int = 0

    private var inStructure: Boolean = false
    private var isCollection: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1

    private var booleanIndices: IntArray = intArrayOf()
    private lateinit var booleanValues: BooleanArray
    private var nullableIndices: IntArray = intArrayOf()
    private lateinit var nullValues: BooleanArray

    private var collectionSize = -1
    private var collectionIndex = 0

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        inStructure = true
        currentDescriptor = descriptor

        val kind = descriptor.kind
        isCollection = kind is StructureKind.LIST || kind is StructureKind.MAP

        if (isCollection) {
            booleanIndices = intArrayOf()
            nullableIndices = intArrayOf()
            return this
        }

        val boolIdx = (0 until descriptor.elementsCount).filter {
            descriptor.getElementDescriptor(it).kind == PrimitiveKind.BOOLEAN
        }
        booleanIndices = boolIdx.toIntArray()

        val nullableIdx = (0 until descriptor.elementsCount).filter {
            descriptor.getElementDescriptor(it).isNullable
        }
        nullableIndices = nullableIdx.toIntArray()

        val totalFlags = booleanIndices.size + nullableIndices.size

        if (totalFlags == 0) {
            booleanValues = BooleanArray(0)
            nullValues = BooleanArray(0)
        } else {
            val allFlags: BooleanArray
            if (totalFlags > 64) {
                val (byteCount, bytesRead) = PackedUtils.decodeVarInt(input, position)
                position += bytesRead
                allFlags = PackedUtils.unpackFlags(input, position, byteCount)
                position += byteCount
            } else {
                val (flagsLong, bytesRead) = PackedUtils.decodeVarLong(input, position)
                position += bytesRead
                allFlags = PackedUtils.unpackFlagsFromLong(flagsLong, totalFlags)
            }

            booleanValues = BooleanArray(booleanIndices.size) { i ->
                if (i < allFlags.size) allFlags[i] else false
            }
            val nullOffset = booleanIndices.size
            nullValues = BooleanArray(nullableIndices.size) { i ->
                val flagIdx = nullOffset + i
                if (flagIdx < allFlags.size) allFlags[flagIdx] else false
            }
        }

        return this
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val (size, bytesRead) = PackedUtils.decodeVarInt(input, position)
        position += bytesRead
        collectionSize = size
        collectionIndex = 0
        return size
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (isCollection) {
            return if (collectionIndex < collectionSize) {
                collectionIndex++
            } else {
                CompositeDecoder.DECODE_DONE
            }
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeSequentially(): Boolean = true

    override fun decodeBoolean(): Boolean {
        if (!inStructure || isCollection) {
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

    override fun decodeInt(): Int {
        return if (inStructure && !isCollection) decodeIntElement(currentDescriptor, currentIndex) else readIntPos()
    }

    override fun decodeLong(): Long {
        return if (inStructure && !isCollection) decodeLongElement(currentDescriptor, currentIndex) else readLongPos()
    }

    override fun decodeFloat(): Float = java.lang.Float.intBitsToFloat(readIntPos())
    override fun decodeDouble(): Double = java.lang.Double.longBitsToDouble(readLongPos())
    override fun decodeChar(): Char = readUtf8Char()
    override fun decodeString(): String = readStringInline()

    private fun readStringInline(): String {
        val (len, bytesRead) = PackedUtils.decodeVarInt(input, position)
        position += bytesRead
        require(len >= 0 && position + len <= input.size)
        val bytes = input.copyOfRange(position, position + len)
        position += len
        return bytes.toString(Charsets.UTF_8)
    }

    @ExperimentalSerializationApi
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val (v, bytesRead) = PackedUtils.decodeVarInt(input, position)
        position += bytesRead
        return v
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? = null

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        if (!inStructure || isCollection) {
            val (flags, bytesRead) = PackedUtils.decodeVarLong(input, position)
            position += bytesRead
            return (flags and 1L) == 0L
        }
        val idx = currentIndex
        if (idx < 0) return true
        val pos = nullablePos(idx)
        if (pos == -1) return true
        return !nullValues[pos]
    }

    @ExperimentalSerializationApi
    override fun decodeInline(descriptor: SerialDescriptor): Decoder = this

    override fun endStructure(descriptor: SerialDescriptor) {
        inStructure = false
        isCollection = false
        currentIndex = -1
    }

    private fun booleanPos(index: Int): Int {
        for (i in booleanIndices.indices) if (booleanIndices[i] == index) return i
        return -1
    }
    private fun nullablePos(index: Int): Int {
        for (i in nullableIndices.indices) if (nullableIndices[i] == index) return i
        return -1
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        val pos = booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        return booleanValues[pos]
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        require(position < input.size)
        return input[position++]
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short = readShortPos()

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasVarInt()
        val varInt = anns.hasVarUInt() || zigZag

        return if (varInt) {
            val (raw, bytesRead) = PackedUtils.decodeVarInt(input, position)
            position += bytesRead
            if (zigZag) PackedUtils.zigZagDecodeInt(raw) else raw
        } else {
            readIntPos()
        }
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasVarInt()
        val varInt = anns.hasVarUInt() || zigZag

        return if (varInt) {
            val (raw, bytesRead) = PackedUtils.decodeVarLong(input, position)
            position += bytesRead
            if (zigZag) PackedUtils.zigZagDecodeLong(raw) else raw
        } else {
            readLongPos()
        }
    }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float =
        java.lang.Float.intBitsToFloat(readIntPos())

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double =
        java.lang.Double.longBitsToDouble(readLongPos())

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char = readUtf8Char()
    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String = readStringInline()

    @ExperimentalSerializationApi
    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
        currentIndex = index
        return this
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T>, previousValue: T?
    ): T {
        currentIndex = index

        val kind = deserializer.descriptor.kind
        val isInline = deserializer.descriptor.isInline

        // [CRITICAL FIX]
        // 1. Isolate complex structures (Classes, Lists, Maps) to separate their state.
        // 2. EXCLUDE Inline Classes (Value Classes) via `!isInline`. They are wrappers
        //    and must share the parent's context to access field annotations (like @VarUInt).
        if ((kind is StructureKind.CLASS || kind is StructureKind.OBJECT || kind is StructureKind.LIST || kind is StructureKind.MAP || kind is PolymorphicKind) && !isInline) {
            val subDecoder = PackedDecoder(input)
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
        descriptor: SerialDescriptor, index: Int, deserializer: DeserializationStrategy<T?>, previousValue: T?
    ): T? {
        if (isCollection) {
            val notNull = decodeNotNullMark()
            return if (notNull) {
                decodeSerializableElement(descriptor, index, deserializer as DeserializationStrategy<T>, previousValue)
            } else {
                decodeNull()
            }
        }

        val pos = nullablePos(index)
        if (pos == -1) {
            return decodeSerializableElement(descriptor, index, deserializer as DeserializationStrategy<T>, previousValue)
        }

        if (nullValues[pos]) return null

        return decodeSerializableElement(descriptor, index, deserializer as DeserializationStrategy<T>, previousValue)
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
            else -> throw IllegalArgumentException("Invalid UTF-8 start byte")
        }
        position += len
        return cp.toChar()
    }
}
