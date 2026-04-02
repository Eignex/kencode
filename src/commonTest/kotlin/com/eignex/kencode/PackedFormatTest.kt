@file:OptIn(ExperimentalTime::class)

package com.eignex.kencode

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
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
    @OptIn(InternalSerializationApi::class)
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
            PolymorphicBase.Action("Standalone", 0),

            BooleanListPayload(1, listOf(true, false, true)),
            BooleanListPayload(2, emptyList()),
            BooleanListPayload(3, List(65) { it % 2 == 0 }),
        )

        testData.forEach { value ->
            @Suppress("UNCHECKED_CAST")
            val serializer = value::class.serializer() as KSerializer<Any>
            assertPackedRoundtrip(serializer, value)
        }
    }

    @Test
    @OptIn(InternalSerializationApi::class)
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
            val serializer = value::class.serializer() as KSerializer<Any>
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
    fun `defaultEncoding DEFAULT reduces size for positive integers`() {
        val payload = UnannotatedPayload(5, 10L)

        val standardFormat = PackedFormat.Default
        val optimizedFormat =
            PackedFormat { defaultEncoding = PackedIntegerType.DEFAULT }

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

        assertEquals(
            payload,
            optimizedFormat.decodeFromByteArray(
                UnannotatedPayload.serializer(),
                optimizedBytes
            )
        )
    }

    @Test
    fun `defaultEncoding SIGNED reduces size for negative integers`() {
        val payload = UnannotatedPayload(-1, -1L)

        val standardFormat = PackedFormat.Default
        val varIntFormat =
            PackedFormat { defaultEncoding = PackedIntegerType.DEFAULT }
        val zigZagFormat =
            PackedFormat { defaultEncoding = PackedIntegerType.SIGNED }

        // Standard: 12 bytes
        assertEquals(
            12,
            standardFormat.encodeToByteArray(
                UnannotatedPayload.serializer(),
                payload
            ).size
        )
        // Unsigned VarInt of -1 uses max bytes: 5 + 10 = 15 bytes
        assertEquals(
            15,
            varIntFormat.encodeToByteArray(
                UnannotatedPayload.serializer(),
                payload
            ).size
        )
        // ZigZag folds -1 into 1: 1 + 1 = 2 bytes
        val zigZagBytes = zigZagFormat.encodeToByteArray(
            UnannotatedPayload.serializer(),
            payload
        )
        assertEquals(2, zigZagBytes.size)

        assertEquals(
            payload,
            zigZagFormat.decodeFromByteArray(
                UnannotatedPayload.serializer(),
                zigZagBytes
            )
        )
    }

    @Test
    fun `defaultEncoding SIGNED is overridden by PackedType FIXED annotation`() {
        val payload = InversePayload(5, -10L, 6u, 7uL)

        val optimizedFormat =
            PackedFormat { defaultEncoding = PackedIntegerType.SIGNED }

        val bytes = optimizedFormat.encodeToByteArray(
            InversePayload.serializer(),
            payload
        )
        assertEquals(24, bytes.size)
        assertEquals(
            payload,
            optimizedFormat.decodeFromByteArray(
                InversePayload.serializer(),
                bytes
            )
        )
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
        val bytes = PackedFormat.encodeToByteArray(
            ListSerializer(Int.serializer()),
            emptyList()
        )
        assertEquals(1, bytes.size)
        assertEquals(0, bytes[0])
    }

    @Test
    fun `empty map roundtrip`() {
        assertPackedRoundtrip(
            MapHolder.serializer(),
            MapHolder(emptyMap(), null)
        )
        assertPackedRoundtrip(
            MapHolder.serializer(),
            MapHolder(emptyMap(), emptyMap())
        )
    }

    // --- VarInt / VarUInt encoding distinction ---

    @Test
    fun `VarUInt does not apply zigzag so negative inputs encode large`() {
        val uintNegative = VarIntVarUIntPayload(0, 0L, -1, -1L, 0, 0)
        val bytes = PackedFormat.encodeToByteArray(
            VarIntVarUIntPayload.serializer(),
            uintNegative
        )
        assertEquals(29, bytes.size)

        val intNegative = VarIntVarUIntPayload(-1, -1L, 0, 0L, 0, 0)
        val bytes2 = PackedFormat.encodeToByteArray(
            VarIntVarUIntPayload.serializer(),
            intNegative
        )
        assertEquals(16, bytes2.size)

        assertPackedRoundtrip(VarIntVarUIntPayload.serializer(), uintNegative)
        assertPackedRoundtrip(VarIntVarUIntPayload.serializer(), intNegative)
    }

    @Test
    fun `deep null mid-chain roundtrip`() {
        assertPackedRoundtrip(
            DeepNested.serializer(),
            DeepNested("root", Level1(false, null))
        )
    }

    @Test
    fun `3-byte UTF-8 char roundtrip`() {
        assertPackedRoundtrip(
            AllPrimitiveTypes.serializer(),
            AllPrimitiveTypes(0, 0L, 0, 0, 0f, 0.0, '中', false, "")
        )
    }

    @Test
    fun `nullable list of complex types roundtrip`() {
        val list = listOf(Child(1), null, Child(3), null, Child(5))
        val bytes = PackedFormat.encodeToByteArray(
            ListSerializer(Child.serializer().nullable),
            list
        )
        val decoded = PackedFormat.decodeFromByteArray(
            ListSerializer(Child.serializer().nullable),
            bytes
        )
        assertEquals(list, decoded)
    }

    @Test
    fun `truncated bool list bitmap throws`() {
        val valid = PackedFormat.encodeToByteArray(
            ListSerializer(Boolean.serializer()),
            List(16) { true })
        val truncated =
            valid.copyOfRange(0, 2) // size byte + first bitmap byte only
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                ListSerializer(Boolean.serializer()),
                truncated
            )
        }
    }

    @Test
    fun `truncated nullable list bitmap throws`() {
        val list = List(16) { if (it % 2 == 0) "x" else null }
        val valid = PackedFormat.encodeToByteArray(
            ListSerializer(String.serializer().nullable),
            list
        )
        val truncated = valid.copyOfRange(0, 2)
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                ListSerializer(String.serializer().nullable),
                truncated
            )
        }
    }

    @Test
    fun `truncated varint throws`() {
        val incomplete = byteArrayOf(0x80.toByte())
        assertFailsWith<IllegalArgumentException> {
            PackedUtils.decodeVarInt(incomplete, 0)
        }
    }

    @Test
    fun `truncated string bytes throws`() {
        val bad = byteArrayOf(5, 'h'.code.toByte(), 'i'.code.toByte())
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(String.serializer(), bad)
        }
    }

    @Test
    fun `truncated bitmask header throws`() {
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                NullableFieldsPayload.serializer(),
                byteArrayOf()
            )
        }
    }

    @Test
    fun `schema-derived bitmask is more compact than VarLong for 8-flag boundary`() {
        val allTrue = BooleanFlags64(BooleanArray(64) { true })
        val bytes =
            PackedFormat.encodeToByteArray(BooleanFlags64.serializer(), allTrue)
        assertEquals(8, bytes.size)
        assertPackedRoundtrip(BooleanFlags64.serializer(), allTrue)
    }

    @Test
    fun `schema-derived bitmask handles 65-flag class without length prefix`() {
        val allTrue = BooleanFlags65(BooleanArray(65) { true })
        val bytes =
            PackedFormat.encodeToByteArray(BooleanFlags65.serializer(), allTrue)
        assertEquals(9, bytes.size)
        assertPackedRoundtrip(BooleanFlags65.serializer(), allTrue)
    }

    @Test
    fun `boolean list encodes all values as a single bitmap`() {
        val payload = BooleanListPayload(0, List(8) { it % 2 == 0 })
        val bytes = PackedFormat.encodeToByteArray(
            BooleanListPayload.serializer(),
            payload
        )
        assertEquals(6, bytes.size)
        assertPackedRoundtrip(BooleanListPayload.serializer(), payload)
    }

    @Test
    fun `boolean list bitmap roundtrip for various sizes`() {
        for (n in listOf(0, 1, 7, 8, 9, 63, 64, 65)) {
            val payload = BooleanListPayload(n, List(n) { it % 3 == 0 })
            assertPackedRoundtrip(BooleanListPayload.serializer(), payload)
        }
    }

    @Test
    fun `nullable list encodes null markers as a bitmap`() {
        val list = listOf("a", null, "b", null, "c", null, "d", null)
        val bytes = PackedFormat.encodeToByteArray(
            ListSerializer(String.serializer().nullable),
            list
        )
        assertEquals(10, bytes.size)
        val decoded = PackedFormat.decodeFromByteArray(
            ListSerializer(String.serializer().nullable),
            bytes
        )
        assertEquals(list, decoded)
    }

    @Test
    fun `nullable list bitmap roundtrip for mixed null and non-null`() {
        val lists = listOf(
            listOf(null, null, null),
            listOf("x"),
            listOf("a", null, "b"),
            emptyList(),
            listOf(null)
        )
        for (list in lists) {
            val bytes = PackedFormat.encodeToByteArray(
                ListSerializer(String.serializer().nullable),
                list
            )
            val decoded = PackedFormat.decodeFromByteArray(
                ListSerializer(String.serializer().nullable),
                bytes
            )
            assertEquals(list, decoded)
        }
    }

    // --- Merged bitmask header ---

    @Test
    fun `class with only nested booleans and nullables encodes to header byte only`() {
        // Level1(active: Boolean, level2: Level2?) — all info fits in the 2-bit merged header.
        // active=true  → bit 0 = 1
        // level2=null  → bit 1 = 1 (null-marker)
        // No data bytes remain, so total = 1 byte.
        val bytes = PackedFormat.encodeToByteArray(
            Level1.serializer(),
            Level1(true, null)
        )
        assertEquals(1, bytes.size)
        assertEquals(0b00000011, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `merged header bit pattern matches field order and value`() {
        // active=false → bit 0 = 0
        // level2 not null → bit 1 = 0 (null-marker = false means present)
        // header byte = 0b00000000 = 0
        val bytes = PackedFormat.encodeToByteArray(
            Level1.serializer(), Level1(false, Level2("", emptyList()))
        )
        assertEquals(0, bytes[0].toInt() and 0xFF)
        assertPackedRoundtrip(
            Level1.serializer(),
            Level1(false, Level2("", emptyList()))
        )
    }

    @Test
    fun `non-nullable nested class flags are promoted into root merged header`() {
        // DeepNested.level1 is a non-nullable Level1 — its bits merge into DeepNested's header.
        // countAllBits(DeepNested) = countAllBits(Level1) = 2.
        // Header byte: bit 0=active=true, bit 1=level2_null=true → 0b00000011 = 3.
        // Data after header: "x" (varint 1 + byte) = 2 bytes. Total = 3.
        val value = DeepNested("x", Level1(active = true, level2 = null))
        val bytes =
            PackedFormat.encodeToByteArray(DeepNested.serializer(), value)
        assertEquals(3, bytes.size)
        assertEquals(0b00000011, bytes[0].toInt() and 0xFF)
        assertPackedRoundtrip(DeepNested.serializer(), value)
    }

    @Test
    fun `multiple non-nullable nested class fields share a single header byte`() {
        // DeepBreadth has three non-nullable Level1 fields (2 bits each = 6 bits total → 1 byte).
        // Old format: 3 separate 1-byte headers = 3 bytes overhead.
        // New format: 1 merged byte.
        // Bit layout (depth-first, booleans before nullables per class):
        //   bit 0: branchA.active=true,    bit 1: branchA.level2_null=true
        //   bit 2: branchB.active=false,   bit 3: branchB.level2_null=true
        //   bit 4: branchC.active=true,    bit 5: branchC.level2_null=true
        // byte = 0b00111011 = 59
        // Data: rootValue=42 (4 bytes). Total = 5.
        val value = DeepBreadth(
            branchA = Level1(true, null),
            branchB = Level1(false, null),
            branchC = Level1(true, null),
            rootValue = 42,
        )
        val bytes =
            PackedFormat.encodeToByteArray(DeepBreadth.serializer(), value)
        assertEquals(5, bytes.size)
        assertEquals(59, bytes[0].toInt() and 0xFF)
        assertPackedRoundtrip(DeepBreadth.serializer(), value)
    }

    @Test
    fun `nullable nested class field keeps local inline header not merged`() {
        // level2 is nullable → its own boolean/nullable bits are NOT merged up.
        // Only the null-marker for level2 itself is in Level1's merged header.
        // Level2 has no boolean or nullable fields, so no extra header when present.
        val withLevel2 =
            Level1(active = true, level2 = Level2("data", listOf(listOf(1))))
        val withoutLevel2 = Level1(active = true, level2 = null)
        assertPackedRoundtrip(Level1.serializer(), withLevel2)
        assertPackedRoundtrip(Level1.serializer(), withoutLevel2)
        // Present level2 → header bit 1 = 0; absent → header bit 1 = 1
        val bytesPresent =
            PackedFormat.encodeToByteArray(Level1.serializer(), withLevel2)
        val bytesAbsent =
            PackedFormat.encodeToByteArray(Level1.serializer(), withoutLevel2)
        assertEquals(
            0b00000001,
            bytesPresent[0].toInt() and 0xFF
        )  // active=1, level2_null=0
        assertEquals(
            0b00000011,
            bytesAbsent[0].toInt() and 0xFF
        )   // active=1, level2_null=1
    }

    @Test
    fun `non-nullable nested class with no flags contributes no header bytes`() {
        // Parent(id: Int, child: Child) — Child has no booleans or nullables.
        // countAllBits = 0 → no header byte at all.
        val bytes = PackedFormat.encodeToByteArray(
            Parent.serializer(),
            Parent(7, Child(42))
        )
        assertEquals(8, bytes.size)  // 4 bytes id + 4 bytes child.value
        assertPackedRoundtrip(Parent.serializer(), Parent(7, Child(42)))
    }

    @Test
    fun `flipping a merged header bit changes the corresponding nested field`() {
        val value = DeepNested("root", Level1(active = true, level2 = null))
        val bytes =
            PackedFormat.encodeToByteArray(DeepNested.serializer(), value)
                .copyOf()
        // Flip bit 0 (active) in the merged header byte
        bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        val decoded =
            PackedFormat.decodeFromByteArray(DeepNested.serializer(), bytes)
        assertEquals(false, decoded.level1.active)
        assertEquals(null, decoded.level1.level2)
    }

    @Test
    fun `truncated merged header throws for nested structure`() {
        // DeepNested requires 1 byte for its merged header; empty input should fail.
        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(
                DeepNested.serializer(),
                byteArrayOf()
            )
        }
    }

    @Test
    fun `DeepBreadth roundtrip with all flag combinations`() {
        val cases = listOf(
            DeepBreadth(
                Level1(false, null),
                Level1(false, null),
                Level1(false, null),
                0
            ),
            DeepBreadth(
                Level1(true, null),
                Level1(true, null),
                Level1(true, null),
                -1
            ),
            DeepBreadth(
                Level1(true, Level2("a", emptyList())),
                Level1(false, null),
                Level1(true, Level2("c", listOf(listOf(1, 2)))),
                999,
            ),
        )
        for (value in cases) {
            assertPackedRoundtrip(DeepBreadth.serializer(), value)
        }
    }

    @Test
    fun `decodeElementIndex simulates sequential decoding for classes and collections`() {
        val classSerializer = UnannotatedPayload.serializer()
        val classBytes = PackedFormat.encodeToByteArray(
            classSerializer,
            UnannotatedPayload(1, 2L)
        )

        val classDecoder = PackedDecoder(classBytes)
        val classDescriptor = classSerializer.descriptor

        classDecoder.beginStructure(classDescriptor)

        assertEquals(0, classDecoder.decodeElementIndex(classDescriptor))
        assertEquals(1, classDecoder.decodeElementIndex(classDescriptor))
        assertEquals(
            kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE,
            classDecoder.decodeElementIndex(classDescriptor)
        )

        val listSerializer = ListSerializer(serializer<Int>())
        val listBytes =
            PackedFormat.encodeToByteArray(listSerializer, listOf(10, 20))

        val listDecoder = PackedDecoder(listBytes)
        val listDescriptor = listSerializer.descriptor

        listDecoder.beginStructure(listDescriptor)
        listDecoder.decodeCollectionSize(listDescriptor)

        assertEquals(0, listDecoder.decodeElementIndex(listDescriptor))
        assertEquals(1, listDecoder.decodeElementIndex(listDescriptor))
        assertEquals(
            kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE,
            listDecoder.decodeElementIndex(listDescriptor)
        )
    }
}
