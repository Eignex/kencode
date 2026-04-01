@file:OptIn(ExperimentalTime::class)

package com.eignex.kencode

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.*
import kotlinx.serialization.serializer
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PackedFormatTest {

    /**
     * Executes a binary roundtrip using PackedFormat and asserts equality.
     */
    private fun <T> assertPackedRoundtrip(
        serializer: KSerializer<T>,
        value: T,
        format: PackedFormat = PackedFormat
    ) {
        val bytes = format.encodeToByteArray(serializer, value)
        val decoded = format.decodeFromByteArray(serializer, bytes)
        assertEquals(
            value,
            decoded,
            "Roundtrip failed for ${serializer.descriptor.serialName}"
        )
    }

    @Test
    fun `verify all test class patterns`() {
        // A comprehensive list of all test data instances from TestClasses.kt
        val testData: List<Any> = listOf(
            // Primitive & Basic
            SimpleIntsAndBooleans(1, 100, true, false),
            SimpleIntsAndBooleans(Int.MAX_VALUE, Int.MIN_VALUE, false, true),
            AllPrimitiveTypes(123, 456L, 7, 8, 3.5f, 2.75, 'A', true, "Hello"),
            AllPrimitiveTypes(
                123,
                456L,
                7,
                8,
                Float.NaN,
                Double.NaN,
                'A',
                true,
                "Hello"
            ),
            AllPrimitiveTypes(
                -123,
                -456L,
                -7,
                -8,
                -.5f,
                -2.75,
                'ñ',
                true,
                "Hello \uD83D\uDE80"
            ),
            AllPrimitiveTypesNullable(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            ),
            NoBooleansNoNulls(42, 123_456_789L, "No flags here"),

            // Enums & Nullables
            EnumPayload(1, Status.NEW, null),
            EnumPayload(2, Status.IN_PROGRESS, Status.DONE),
            NullableFieldsPayload(null, null, null, null, null),
            NullableFieldsPayload(10, "Alice", 999L, true, listOf(1, 2, 3)),
            NullableBooleansAndNonBooleans(
                null,
                false,
                null,
                null,
                "all-null-bools"
            ),
            StringHeavyPayload("short", "longer", "unicode ✓✓✓", "present"),

            // Annotations & Inlines
            VarIntVarUIntPayload(-1, -123_456_789L, 0, 1L, 42, 9_999L),
            VarIntVarUIntPayload(
                Int.MIN_VALUE,
                Long.MIN_VALUE,
                Int.MAX_VALUE,
                Long.MAX_VALUE,
                -1000,
                123_456L
            ),
            UIntInlinePayload(1, 0u, 1u, true),
            DurationInlinePayload(
                "mixed",
                5.seconds + 250.milliseconds,
                2.minutes,
                10
            ),
            InstantInlinePayload(
                Instant.fromEpochMilliseconds(0),
                null,
                true,
                1
            ),

            // Nesting & Collections
            Parent(id = 1, child = Child(2)),
            WithList(id = 1, items = listOf(1, 2, 3)),
            Grid(rows = listOf(listOf(1, 2, 3), emptyList(), listOf(4))),
            NestedOptionalList(1, listOf("alpha", "beta"), listOf(10, 20)),
            NestedOptionalList(2, null, emptyList()),
            DeepNested(
                "root",
                Level1(true, Level2("payload", listOf(listOf(1, 2))))
            ),
            MapHolder(mapOf("host" to "localhost"), mapOf("retries" to 3)),
            ComplexMap(mapOf("day" to listOf(1)), mapOf(101 to Child(1))),
            MixedBag(
                42,
                mapOf("k" to "v"),
                listOf(Child(1)),
                listOf(true, null, false)
            ),
            NullableMap(mapOf("k1" to "v1", "k2" to null)),
            BooleanFlags63(BooleanArray(63) { it % 2 == 0 }),
            BooleanFlags63(BooleanArray(63) { true }),
            BooleanFlags63(BooleanArray(63) { false }),
            BooleanFlags64(BooleanArray(64) { it % 2 == 0 }),
            BooleanFlags64(BooleanArray(64) { true }),
            BooleanFlags64(BooleanArray(64) { false }),
            BooleanFlags65(BooleanArray(65) { it % 2 == 0 }),
            BooleanFlags65(BooleanArray(65) { true }),
            BooleanFlags65(BooleanArray(65) { false }),
            RecursiveTree(
                "root", listOf(
                    RecursiveTree(
                        "child1",
                        listOf(RecursiveTree("grandchild1"))
                    ),
                    RecursiveTree("child2")
                )
            ),

            InlineHeavyPayload(
                255u, 65535u, 4000000000u, 18000000000000000000u,
                UserId(9999L), Email("test@example.com"),
                listOf(UserId(1), UserId(2)), Instant.DISTANT_PAST
            ),

            MultiLevelCollections(
                complexMap = mapOf(
                    "outer" to mapOf(
                        1 to listOf(
                            "a",
                            null,
                            "c"
                        )
                    )
                ),
                nestedLists = listOf(listOf(listOf(1, 2), listOf(3))),
                optionalDeepMap = mapOf(42 to null)
            ),

            DeepBreadth(
                branchA = Level1(true, Level2("data-a", listOf(listOf(1)))),
                branchB = Level1(false, null),
                branchC = Level1(true, Level2("data-c", emptyList())),
                rootValue = 999
            ),

            PolymorphicContainer(
                main = PolymorphicBase.Action("Login", 1),
                history = listOf(
                    PolymorphicBase.Notification("Welcome!", false),
                    PolymorphicBase.Heartbeat,
                    PolymorphicBase.Action("Update", 2),
                    PolymorphicBase.Notification("Hello", true),
                )
            ),

            PolymorphicBase.Heartbeat,
            PolymorphicBase.Action("Standalone", 0)
        )

        testData.forEach { value ->
            @Suppress("UNCHECKED_CAST")
            val serializer = serializer(value::class.java)
            assertPackedRoundtrip(serializer, value)
        }
    }

    @Test
    fun `top level primitives roundtrip`() {
        val primitives: List<Any> = listOf(
            true, false,
            'A', 'ñ', '✓',
            42.toByte(),
            1000.toShort(),
            Int.MAX_VALUE,
            Long.MIN_VALUE,
            3.14f,
            2.71828,
            "Stand-alone string",
            Status.DONE
        )

        primitives.forEach { value ->
            @Suppress("UNCHECKED_CAST")
            val serializer = serializer(value::class.java)
            assertPackedRoundtrip(serializer, value)
        }
    }

    @Test
    fun `top level nullable roundtrip`() {
        assertPackedRoundtrip(String.serializer().nullable, "asae")
        assertPackedRoundtrip(String.serializer().nullable, null)
    }

    @Test
    fun `single continuation byte should throw for top level char`() {
        val bytes = byteArrayOf(0x80.toByte())
        val decoder = PackedDecoder(bytes)
        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

    @Test
    fun `truncated 2-byte sequence should throw for top level char`() {
        val bytes = byteArrayOf(0xC2.toByte()) // Missing 2nd byte
        val decoder = PackedDecoder(bytes)
        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

    @Test
    fun `invalid continuation byte should throw for top level char`() {
        val bytes = byteArrayOf(
            0xC2.toByte(),
            0x41
        ) // 0x41 ('A') is not a valid continuation
        val decoder = PackedDecoder(bytes)
        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

    @Test
    fun `builder defaultVarInt reduces size for positive integers`() {
        val payload = UnannotatedPayload(5, 10L)

        val standardFormat = PackedFormat.Default
        val optimizedFormat = PackedFormat { defaultVarInt = true }

        val standardBytes = standardFormat.encodeToByteArray(
            UnannotatedPayload.serializer(),
            payload
        )
        val optimizedBytes = optimizedFormat.encodeToByteArray(
            UnannotatedPayload.serializer(),
            payload
        )

        // Standard size: 4 bytes (Int) + 8 bytes (Long) = 12 bytes
        assertEquals(12, standardBytes.size)

        // Optimized size: 1 byte (VarInt 5) + 1 byte (VarLong 10) = 2 bytes
        assertEquals(2, optimizedBytes.size)
        assertTrue(optimizedBytes.size < standardBytes.size)

        // Validate roundtrip
        val decoded = optimizedFormat.decodeFromByteArray(
            UnannotatedPayload.serializer(),
            optimizedBytes
        )
        assertEquals(payload, decoded)
    }

    @Test
    fun `builder defaultZigZag reduces size for negative integers`() {
        // -1 without ZigZag takes 5 bytes for Int and 10 bytes for Long in standard VarInt encoding
        val payload = UnannotatedPayload(-1, -1L)

        val standardFormat = PackedFormat.Default
        val varIntFormat = PackedFormat { defaultVarInt = true }
        val zigZagFormat = PackedFormat { defaultZigZag = true }

        val standardBytes = standardFormat.encodeToByteArray(
            UnannotatedPayload.serializer(),
            payload
        )
        val varIntBytes = varIntFormat.encodeToByteArray(
            UnannotatedPayload.serializer(),
            payload
        )
        val zigZagBytes = zigZagFormat.encodeToByteArray(
            UnannotatedPayload.serializer(),
            payload
        )

        // Standard: 12 bytes
        assertEquals(12, standardBytes.size)
        // Basic VarInt of negative numbers uses max bytes: 5 + 10 = 15 bytes
        assertEquals(15, varIntBytes.size)
        // ZigZag folds -1 into 1, which takes 1 byte each: 1 + 1 = 2 bytes
        assertEquals(2, zigZagBytes.size)

        // Validate roundtrip
        val decoded = zigZagFormat.decodeFromByteArray(
            UnannotatedPayload.serializer(),
            zigZagBytes
        )
        assertEquals(payload, decoded)
    }

    @Test
    fun `builder defaultVarInt is overridden by FixedInt annotation`() {
        val payload = InversePayload(5, -10L, 6u, 7uL)

        val optimizedFormat = PackedFormat {
            defaultVarInt = true
            defaultZigZag = true
        }

        val bytes = optimizedFormat.encodeToByteArray(
            InversePayload.serializer(),
            payload
        )
        assertEquals(24, bytes.size)
        val decoded = optimizedFormat.decodeFromByteArray(
            InversePayload.serializer(),
            bytes
        )
        assertEquals(payload, decoded)
    }

    @Test
    fun `truncated data should throw informative error`() {
        val serializer = AllPrimitiveTypes.serializer()
        val valid = PackedFormat.encodeToByteArray(
            serializer,
            AllPrimitiveTypes(1, 1, 1, 1, 1f, 1.0, 'a', true, "test")
        )

        val truncated = valid.copyOfRange(0, valid.size - 5)
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(serializer, truncated)
        }
    }

    // --- Size assertions ---

    @Test
    fun `class with no booleans or nullables has no bitmask header`() {
        // 4 (Int) + 8 (Long) + 1 (VarInt len) + 13 (String bytes) = 26
        val bytes = PackedFormat.encodeToByteArray(
            NoBooleansNoNulls.serializer(),
            NoBooleansNoNulls(42, 42L, "No flags here")
        )
        assertEquals(26, bytes.size)
    }

    @Test
    fun `boolean fields pack into single bitmask byte`() {
        // 2 booleans → 1 bitmask byte (VarLong), 2 ints → 4 bytes each = 9 total
        val bytes = PackedFormat.encodeToByteArray(
            SimpleIntsAndBooleans.serializer(),
            SimpleIntsAndBooleans(0, 0, false, false)
        )
        assertEquals(9, bytes.size)
    }

    @Test
    fun `empty string encodes as single zero byte`() {
        val bytes = PackedFormat.encodeToByteArray(String.serializer(), "")
        assertEquals(1, bytes.size)
        assertEquals(0, bytes[0])
    }

    @Test
    fun `empty list encodes as single zero byte`() {
        val bytes = PackedFormat.encodeToByteArray(ListSerializer(Int.serializer()), emptyList())
        assertEquals(1, bytes.size)
        assertEquals(0, bytes[0])
    }

    @Test
    fun `empty map roundtrip`() {
        assertPackedRoundtrip(MapHolder.serializer(), MapHolder(emptyMap(), null))
        assertPackedRoundtrip(MapHolder.serializer(), MapHolder(emptyMap(), emptyMap()))
    }

    // --- VarInt / VarUInt encoding distinction ---

    @Test
    fun `VarUInt does not apply zigzag so negative inputs encode large`() {
        // @VarUInt(-1 as Int) = 0xFFFFFFFF unsigned = 5 bytes
        // @VarUInt(-1L as Long) = 0xFFFFFFFFFFFFFFFF unsigned = 10 bytes
        val uintNegative = VarIntVarUIntPayload(0, 0L, -1, -1L, 0, 0)
        val bytes = PackedFormat.encodeToByteArray(VarIntVarUIntPayload.serializer(), uintNegative)
        // VarInt(0)=1, VarLong(0)=1, VarInt(0xFFFFFFFF)=5, VarLong(0xFFFFFFFF…)=10, Int=4, Long=8 → 29
        assertEquals(29, bytes.size)

        // @VarInt(-1) = zigzag(−1) = 1 = 1 byte
        val intNegative = VarIntVarUIntPayload(-1, -1L, 0, 0L, 0, 0)
        val bytes2 = PackedFormat.encodeToByteArray(VarIntVarUIntPayload.serializer(), intNegative)
        // VarInt(1)=1, VarLong(1)=1, VarInt(0)=1, VarLong(0)=1, Int=4, Long=8 → 16
        assertEquals(16, bytes2.size)

        assertPackedRoundtrip(VarIntVarUIntPayload.serializer(), uintNegative)
        assertPackedRoundtrip(VarIntVarUIntPayload.serializer(), intNegative)
    }

    // --- defaultVarInt / defaultZigZag interaction ---

    @Test
    fun `both defaultVarInt and defaultZigZag active uses zigzag`() {
        val fmt = PackedFormat { defaultVarInt = true; defaultZigZag = true }
        val payload = UnannotatedPayload(-1, -1L)
        val bytes = fmt.encodeToByteArray(UnannotatedPayload.serializer(), payload)
        // zigzag(−1)=1 for both → 1 + 1 = 2 bytes
        assertEquals(2, bytes.size)
        assertEquals(payload, fmt.decodeFromByteArray(UnannotatedPayload.serializer(), bytes))
    }

    // --- Nullability roundtrips not covered in the main table ---

    @Test
    fun `deep null mid-chain roundtrip`() {
        assertPackedRoundtrip(DeepNested.serializer(), DeepNested("root", Level1(false, null)))
    }

    // --- UTF-8 character encoding ---

    @Test
    fun `3-byte UTF-8 char roundtrip`() {
        assertPackedRoundtrip(
            AllPrimitiveTypes.serializer(),
            AllPrimitiveTypes(0, 0L, 0, 0, 0f, 0.0, '中', false, "")
        )
    }

    // --- Truncation error coverage ---

    @Test
    fun `truncated varint throws`() {
        // Continuation bit set but no following byte
        val incomplete = byteArrayOf(0x80.toByte())
        assertFailsWith<IllegalArgumentException> {
            PackedUtils.decodeVarInt(incomplete, 0)
        }
    }

    @Test
    fun `truncated string bytes throws`() {
        // VarInt says length 5 but only 2 bytes follow
        val bad = byteArrayOf(5, 'h'.code.toByte(), 'i'.code.toByte())
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(String.serializer(), bad)
        }
    }

    @Test
    fun `truncated bitmask header throws`() {
        // NullableFieldsPayload expects a bitmask but gets empty input
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(NullableFieldsPayload.serializer(), byteArrayOf())
        }
    }

    @Test
    fun `decodeElementIndex simulates sequential decoding for classes and collections`() {
        // 1. Test the Class path
        // UnannotatedPayload has 2 properties (x, y)
        val classSerializer = UnannotatedPayload.serializer()
        val classBytes = PackedFormat.encodeToByteArray(classSerializer, UnannotatedPayload(1, 2L))

        val classDecoder = PackedDecoder(classBytes)
        val classDescriptor = classSerializer.descriptor

        classDecoder.beginStructure(classDescriptor)

        // It should yield index 0, then 1, then DECODE_DONE
        assertEquals(0, classDecoder.decodeElementIndex(classDescriptor))
        assertEquals(1, classDecoder.decodeElementIndex(classDescriptor))
        assertEquals(
            kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE,
            classDecoder.decodeElementIndex(classDescriptor)
        )

        // 2. Test the Collection path
        val listSerializer = ListSerializer(serializer<Int>())
        val listBytes = PackedFormat.encodeToByteArray(listSerializer, listOf(10, 20))

        val listDecoder = PackedDecoder(listBytes)
        val listDescriptor = listSerializer.descriptor

        listDecoder.beginStructure(listDescriptor)
        // Manually trigger the size decoding which sets up the collection state
        listDecoder.decodeCollectionSize(listDescriptor)

        // It should yield index 0, then 1, then DECODE_DONE
        assertEquals(0, listDecoder.decodeElementIndex(listDescriptor))
        assertEquals(1, listDecoder.decodeElementIndex(listDescriptor))
        assertEquals(
            kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE,
            listDecoder.decodeElementIndex(listDescriptor)
        )
    }
}
