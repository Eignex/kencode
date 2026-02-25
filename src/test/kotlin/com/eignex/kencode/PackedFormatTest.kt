@file:OptIn(ExperimentalTime::class)

package com.eignex.kencode

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
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
        value: T
    ) {
        val bytes = PackedFormat.encodeToByteArray(serializer, value)
        val decoded = PackedFormat.decodeFromByteArray(serializer, bytes)
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
            NullableFieldsPayload(null, null, null, null),
            NullableFieldsPayload(10, "Alice", 999L, true),
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
}
