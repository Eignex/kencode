package com.eignex.kencode

import BitPacking
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
class BitPackedDecoder(
    private val input: ByteArray
) : Decoder, CompositeDecoder {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var inStructure: Boolean = false
    private lateinit var currentDescriptor: SerialDescriptor
    private var currentIndex: Int = -1
    private var position: Int = 0

    private var booleanIndices: IntArray = intArrayOf()
    private lateinit var booleanValues: BooleanArray

    private var nullableIndices: IntArray = intArrayOf()
    private lateinit var nullValues: BooleanArray

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (inStructure) error("Nested objects are not supported")
        inStructure = true
        currentDescriptor = descriptor

        val boolIdx = (0 until descriptor.elementsCount)
            .filter { descriptor.getElementDescriptor(it).kind == PrimitiveKind.BOOLEAN }
        booleanIndices = boolIdx.toIntArray()

        val nullableIdx = (0 until descriptor.elementsCount)
            .filter { descriptor.getElementDescriptor(it).isNullable }
        nullableIndices = nullableIdx.toIntArray()

        val (flagsLong, bytesRead) = BitPacking.decodeVarLong(input, position)
        position += bytesRead

        val totalFlags = booleanIndices.size + nullableIndices.size
        if (totalFlags == 0) {
            booleanValues = BooleanArray(0)
            nullValues = BooleanArray(0)
        } else {
            val allFlags = BitPacking.unpackFlagsFromLong(flagsLong, totalFlags)
            booleanValues = if (booleanIndices.isEmpty()) {
                BooleanArray(0)
            } else {
                allFlags.copyOfRange(0, booleanIndices.size)
            }
            nullValues = if (nullableIndices.isEmpty()) {
                BooleanArray(0)
            } else {
                allFlags.copyOfRange(booleanIndices.size, totalFlags)
            }
        }

        return this
    }

    override fun decodeBoolean(): Boolean {
        return if (inStructure) {
            decodeBooleanElement(currentDescriptor, currentIndex)
        } else {
            input[position++].toInt() != 0
        }
    }

    override fun decodeByte(): Byte {
        return if (inStructure) {
            decodeByteElement(currentDescriptor, currentIndex)
        } else {
            input[position++]
        }
    }

    override fun decodeShort(): Short {
        return if (inStructure) {
            decodeShortElement(currentDescriptor, currentIndex)
        } else {
            readShortPos()
        }
    }

    override fun decodeInt(): Int {
        return if (inStructure) {
            decodeIntElement(currentDescriptor, currentIndex)
        } else {
            readIntPos()
        }
    }

    override fun decodeLong(): Long {
        return if (inStructure) {
            decodeLongElement(currentDescriptor, currentIndex)
        } else {
            readLongPos()
        }
    }

    override fun decodeFloat(): Float {
        return if (inStructure) {
            decodeFloatElement(currentDescriptor, currentIndex)
        } else {
            java.lang.Float.intBitsToFloat(readIntPos())
        }
    }

    override fun decodeDouble(): Double {
        return if (inStructure) {
            decodeDoubleElement(currentDescriptor, currentIndex)
        } else {
            java.lang.Double.longBitsToDouble(readLongPos())
        }
    }

    override fun decodeChar(): Char {
        return if (inStructure) {
            decodeCharElement(currentDescriptor, currentIndex)
        } else {
            readShortPos().toInt().toChar()
        }
    }

    override fun decodeString(): String {
        return if (inStructure) {
            decodeStringElement(currentDescriptor, currentIndex)
        } else {
            val (len, bytesRead) = BitPacking.decodeVarInt(input, position)
            position += bytesRead
            val bytes = input.copyOfRange(position, position + len)
            position += len
            bytes.toString(Charsets.UTF_8)
        }
    }

    @ExperimentalSerializationApi
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val (v, bytesRead) = BitPacking.decodeVarInt(input, position)
        position += bytesRead
        return v
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        return null
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        if (!inStructure) {
            // Standalone / top-level nullable value.
            // We encode a single flagsLong here where:
            // bit 0 = 1 -> null, bit 0 = 0 -> non-null.
            val (flags, bytesRead) = BitPacking.decodeVarLong(input, position)
            position += bytesRead
            return (flags and 1L) == 0L
        }

        // Inside a structure, nullability is driven by the per-field nullValues bitmask.
        val idx = currentIndex
        if (idx < 0) return true
        val pos = nullablePos(idx)
        if (pos == -1) return true
        return !nullValues[pos]
    }

    @ExperimentalSerializationApi
    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        inStructure = false
        currentIndex = -1
        booleanIndices = intArrayOf()
        nullableIndices = intArrayOf()
    }

    override fun decodeSequentially(): Boolean = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        CompositeDecoder.DECODE_DONE

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

    override fun decodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Boolean {
        val pos = booleanPos(index)
        if (pos == -1) error("Element $index is not a boolean")
        return booleanValues[pos]
    }

    override fun decodeIntElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Int {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasVarInt()
        val varInt = anns.hasVarUInt() || zigZag

        return if (varInt) {
            val (raw, bytesRead) = BitPacking.decodeVarInt(input, position)
            position += bytesRead
            if (zigZag) BitPacking.zigZagDecodeInt(raw) else raw
        } else {
            readIntPos()
        }
    }

    override fun decodeLongElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Long {
        val anns = descriptor.getElementAnnotations(index)
        val zigZag = anns.hasVarInt()
        val varInt = anns.hasVarUInt() || zigZag

        return if (varInt) {
            val (raw, bytesRead) = BitPacking.decodeVarLong(input, position)
            position += bytesRead
            if (zigZag) BitPacking.zigZagDecodeLong(raw) else raw
        } else {
            readLongPos()
        }
    }

    override fun decodeByteElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Byte {
        return input[position++]
    }

    override fun decodeShortElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Short {
        return readShortPos()
    }

    override fun decodeCharElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Char {
        return readShortPos().toInt().toChar()
    }

    override fun decodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Float {
        return java.lang.Float.intBitsToFloat(readIntPos())
    }

    override fun decodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Double {
        return java.lang.Double.longBitsToDouble(readLongPos())
    }

    override fun decodeStringElement(
        descriptor: SerialDescriptor,
        index: Int
    ): String {
        val (len, bytesRead) = BitPacking.decodeVarInt(input, position)
        position += bytesRead
        val bytes = input.copyOfRange(position, position + len)
        position += len
        return bytes.toString(Charsets.UTF_8)
    }

    @ExperimentalSerializationApi
    override fun decodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int
    ): Decoder {
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

        if (!deserializer.descriptor.isInline) {
            val kind = deserializer.descriptor.kind
            if (kind is StructureKind.CLASS ||
                kind is StructureKind.OBJECT ||
                kind is StructureKind.LIST ||
                kind is StructureKind.MAP ||
                kind is PolymorphicKind
            ) {
                error("Nested objects/collections are not supported")
            }
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
        val pos = nullablePos(index)
        if (pos == -1) {
            // Not declared nullable; treat as non-null
            currentIndex = index
            val value = deserializer.deserialize(this)
            currentIndex = -1
            return value
        }

        if (nullValues[pos]) {
            // Is null, no payload
            return null
        }

        currentIndex = index
        val value = deserializer.deserialize(this)
        currentIndex = -1
        return value
    }

    private fun readShortPos(): Short {
        return BitPacking.readShort(input, position).also {
            position += 2
        }
    }

    private fun readIntPos(): Int {
        return BitPacking.readInt(input, position).also {
            position += 4
        }
    }

    private fun readLongPos(): Long {
        return BitPacking.readLong(input, position).also {
            position += 8
        }
    }
}
