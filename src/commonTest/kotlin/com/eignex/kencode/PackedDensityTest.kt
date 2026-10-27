@file:OptIn(
    kotlinx.serialization.ExperimentalSerializationApi::class,
    kotlinx.serialization.InternalSerializationApi::class,
)

package com.eignex.kencode

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackedDensityTest {
    @Serializable
    private data class EnumFields(val a: Status, val b: Status, val c: Status, val d: Status)

    @Serializable
    private data class MixedFields(val active: Boolean, val status: Status?, val id: Int)

    @Serializable
    private data class NestedFields(val flag: Boolean, val child: EnumFields, val optional: MixedFields?, val tail: Int)

    @Serializable
    private data class Lists(
        val flags: List<Boolean?>,
        val statuses: List<Status?>,
        val nested: List<List<Status>>,
        val tail: Int,
    )

    @Serializable
    private enum class Singleton { ONLY, }

    @Serializable
    private data class SingletonFields(val status: Singleton, val optional: Singleton?, val tail: Int)

    private class OrdinalSerializer(count: Int) : KSerializer<Int> {
        override val descriptor: SerialDescriptor = buildSerialDescriptor("Ordinal$count", SerialKind.ENUM) {
            repeat(count) { index ->
                element("V$index", buildSerialDescriptor("Ordinal$count.V$index", StructureKind.OBJECT))
            }
        }

        override fun serialize(encoder: Encoder, value: Int) = encoder.encodeEnum(descriptor, value)
        override fun deserialize(decoder: Decoder): Int = decoder.decodeEnum(descriptor)
    }

    private object WideOrdinalSerializer : KSerializer<Int> by OrdinalSerializer(257)

    @Serializable
    private data class WideFields(
        val flag: Boolean,
        @Serializable(with = WideOrdinalSerializer::class) val first: Int,
        @Serializable(with = WideOrdinalSerializer::class) val second: Int,
        val status: Status,
        val tail: Int,
    )

    private fun <T> roundtrip(serializer: KSerializer<T>, value: T): ByteArray {
        val bytes = PackedFormat.encodeToByteArray(serializer, value)
        assertEquals(value, PackedFormat.decodeFromByteArray(serializer, bytes))
        return bytes
    }

    @Test
    fun `four three-value enums fit in one byte with fixed bit order`() {
        val value = EnumFields(Status.NEW, Status.IN_PROGRESS, Status.DONE, Status.NEW)
        assertContentEquals(byteArrayOf(0x24), roundtrip(EnumFields.serializer(), value))
    }

    @Test
    fun `class enums share the boolean and nullable header`() {
        assertContentEquals(
            byteArrayOf(0x09, 42),
            roundtrip(MixedFields.serializer(), MixedFields(true, Status.DONE, 42)),
        )
        assertContentEquals(byteArrayOf(0x03, 42), roundtrip(MixedFields.serializer(), MixedFields(true, null, 42)))
        for (active in listOf(false, true)) {
            for (status in listOf(null) + Status.entries) {
                roundtrip(MixedFields.serializer(), MixedFields(active, status, 17))
            }
        }
    }

    @Test
    fun `nested enums merge headers while nullable objects keep local headers`() {
        val child = EnumFields(Status.NEW, Status.IN_PROGRESS, Status.DONE, Status.NEW)
        assertContentEquals(
            byteArrayOf(0x93.toByte(), 0x00, 42),
            roundtrip(NestedFields.serializer(), NestedFields(true, child, null, 42)),
        )
        for (optional in listOf(null, MixedFields(true, Status.DONE, 17), MixedFields(false, null, 1))) {
            roundtrip(NestedFields.serializer(), NestedFields(true, child, optional, 42))
        }
    }

    @Test
    fun `class enum bits cross header bytes and preserve subsequent fields`() {
        val value = WideFields(true, 256, 255, Status.DONE, 42)
        assertEquals(4, roundtrip(WideFields.serializer(), value).size)
    }

    @Test
    fun `enum lists pack ordinals across byte boundaries`() {
        assertContentEquals(
            byteArrayOf(5, 0x64, 0x02),
            roundtrip(
                ListSerializer(Status.serializer()),
                listOf(Status.NEW, Status.IN_PROGRESS, Status.DONE, Status.IN_PROGRESS, Status.DONE),
            ),
        )
        assertEquals(9, roundtrip(ListSerializer(Status.serializer()), List(32) { Status.entries[it % 3] }).size)
    }

    @Test
    fun `enum widths handle powers of two and wider ordinals`() {
        for (count in listOf(1, 2, 3, 7, 8, 9, 127, 128, 129, 255, 256, 257)) {
            val serializer = OrdinalSerializer(count)
            val width = Int.SIZE_BITS - (count - 1).countLeadingZeroBits()
            for (ordinal in listOf(0, count / 2, count - 1)) {
                assertEquals((width + 7) / 8, roundtrip(serializer, ordinal).size)
            }
            for (size in listOf(0, 1, 7, 8, 9, 31, 32, 33, 127, 128, 129)) {
                val values = List(size) { it % count }
                val sizeBytes = if (size < 128) 1 else 2
                assertEquals(sizeBytes + (size * width + 7) / 8, roundtrip(ListSerializer(serializer), values).size)
                val nullableValues = List(size) { if (it % 3 == 0) null else it % count }
                val valueCount = nullableValues.count { it != null }
                assertEquals(
                    sizeBytes + (size + 7) / 8 + (valueCount * width + 7) / 8,
                    roundtrip(ListSerializer(serializer.nullable), nullableValues).size,
                )
            }
        }
    }

    @Test
    fun `nullable enum list stores nulls separately from ordinals`() {
        val serializer = ListSerializer(Status.serializer().nullable)
        assertContentEquals(
            byteArrayOf(5, 0x0A, 0x09),
            roundtrip(serializer, listOf(Status.IN_PROGRESS, null, Status.DONE, null, Status.NEW)),
        )
    }

    @Test
    fun `singleton enums need no ordinal bytes`() {
        assertContentEquals(byteArrayOf(), roundtrip(Singleton.serializer(), Singleton.ONLY))
        assertContentEquals(
            byteArrayOf(0, 42),
            roundtrip(SingletonFields.serializer(), SingletonFields(Singleton.ONLY, Singleton.ONLY, 42)),
        )
        assertContentEquals(
            byteArrayOf(1, 42),
            roundtrip(SingletonFields.serializer(), SingletonFields(Singleton.ONLY, null, 42)),
        )
        assertContentEquals(
            byteArrayOf(32),
            roundtrip(ListSerializer(Singleton.serializer()), List(32) { Singleton.ONLY }),
        )
        roundtrip(ListSerializer(Singleton.serializer().nullable), listOf(null, Singleton.ONLY, null))
        roundtrip(Singleton.serializer().nullable, null)
        roundtrip(Singleton.serializer().nullable, Singleton.ONLY)
    }

    @Test
    fun `empty enum schemas support null and empty collections`() {
        val serializer = OrdinalSerializer(0)
        roundtrip(serializer.nullable, null)
        roundtrip(ListSerializer(serializer), emptyList())
        roundtrip(ListSerializer(serializer.nullable), listOf(null, null))
        assertFailsWith<IllegalArgumentException> { PackedFormat.decodeFromByteArray(serializer, byteArrayOf()) }
    }

    @Test
    fun `nullable booleans use a null bitmap and packed non-null values`() {
        val serializer = ListSerializer(Boolean.serializer().nullable)
        assertContentEquals(byteArrayOf(5, 0x0A, 0x05), roundtrip(serializer, listOf(true, null, false, null, true)))
        assertEquals(9, roundtrip(serializer, List(32) { it % 2 == 0 }).size)
        assertEquals(8, roundtrip(serializer, List(32) { if (it % 4 == 0) null else it % 2 == 0 }).size)
        assertEquals(5, roundtrip(serializer, List(32) { null }).size)
    }

    @Test
    fun `nullable boolean packing roundtrips around bit and length boundaries`() {
        val serializer = ListSerializer(Boolean.serializer().nullable)
        val random = Random(2710)
        for (size in (0..65).toList() + listOf(127, 128, 129, 255, 256, 257, 1024)) {
            val examples = listOf(
                List<Boolean?>(size) { null },
                List<Boolean?>(size) { false },
                List<Boolean?>(size) { true },
                List<Boolean?>(size) {
                    when (random.nextInt(3)) {
                        0 -> null
                        1 -> false
                        else -> true
                    }
                },
            )
            for (value in examples) {
                val prefix = if (size < 128) 1 else 2
                assertEquals(
                    prefix + (size + 7) / 8 + (value.count { it != null } + 7) / 8,
                    roundtrip(serializer, value).size,
                )
            }
        }
    }

    @Test
    fun `null bitmap padding does not affect present value count`() {
        assertEquals(
            listOf(true, null, null),
            PackedFormat.decodeFromByteArray(
                ListSerializer(Boolean.serializer().nullable),
                byteArrayOf(3, 0xFE.toByte(), 1),
            ),
        )
        assertEquals(
            listOf(Status.DONE, null, null),
            PackedFormat.decodeFromByteArray(
                ListSerializer(Status.serializer().nullable),
                byteArrayOf(3, 0xFE.toByte(), 2),
            ),
        )
    }

    @Test
    fun `packed lists preserve nested collection and following field positions`() {
        val value = Lists(
            listOf(null, true, false, null, true),
            listOf(Status.DONE, null, Status.NEW, Status.IN_PROGRESS),
            listOf(emptyList(), listOf(Status.DONE), List(17) { Status.entries[it % 3] }),
            12345,
        )
        roundtrip(Lists.serializer(), value)
        roundtrip(
            ListSerializer(Lists.serializer().nullable),
            listOf(null, value, value.copy(flags = emptyList(), statuses = listOf(null))),
        )
    }

    @Test
    fun `enum map entries and top-level nullable enums roundtrip`() {
        roundtrip(
            MapSerializer(Status.serializer(), Status.serializer().nullable),
            mapOf(Status.NEW to Status.DONE, Status.IN_PROGRESS to null),
        )
        for (status in listOf(null) + Status.entries) roundtrip(Status.serializer().nullable, status)
        val wide = OrdinalSerializer(257)
        roundtrip(MapSerializer(Int.serializer(), wide), mapOf(1 to 256, 2 to 128))
    }

    @Test
    fun `truncated packed lists and class enum headers fail`() {
        // Each case checks both the null and value bitmap.
        for (cut in 0..2) {
            assertFailsWith<IllegalArgumentException> {
                PackedFormat.decodeFromByteArray(
                    ListSerializer(Status.serializer().nullable),
                    byteArrayOf(3, 2, 2).copyOf(cut),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                PackedFormat.decodeFromByteArray(
                    ListSerializer(Boolean.serializer().nullable),
                    byteArrayOf(3, 2, 1).copyOf(cut),
                )
            }
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                EnumFields.serializer(),
                byteArrayOf(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                OrdinalSerializer(257),
                byteArrayOf(0),
            )
        }
    }

    @Test
    fun `unused enum bit patterns and invalid serializer ordinals fail`() {
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                Status.serializer(),
                byteArrayOf(3),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                EnumFields.serializer(),
                byteArrayOf(3),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                ListSerializer(Status.serializer()),
                byteArrayOf(1, 3),
            )
        }
        val serializer = OrdinalSerializer(3)
        for (ordinal in listOf(-1, 3, 4)) {
            assertFailsWith<IllegalArgumentException> { PackedFormat.encodeToByteArray(serializer, ordinal) }
        }
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                ListSerializer(Status.serializer()),
                byteArrayOf(-1, -1, -1, -1, 15),
            )
        }
    }

    @Test
    fun `packed bit reader handles widths up to 31 with arbitrary alignment`() {
        val out = ByteOutput()
        val bits = BitOutput()
        for (width in 0..31) {
            bits.write(0, width)
            bits.write(if (width == 0) 0 else Int.MAX_VALUE ushr (31 - width), width)
        }
        bits.writeTo(out)
        val input = BitInput(out.toByteArray(), 0, 992)
        for (width in 0..31) {
            assertEquals(0, input.read(width))
            assertEquals(if (width == 0) 0 else Int.MAX_VALUE ushr (31 - width), input.read(width))
        }
        assertFailsWith<IllegalArgumentException> { input.read(1) }
    }
}
