@file:OptIn(ExperimentalTime::class)

package com.eignex.kencode

import PackedFormat
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PackedFormatTest {

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private inline fun <reified T> roundtrip(value: T) {
        val serializer = kotlinx.serialization.serializer<T>()
        val bytes = PackedFormat.encodeToByteArray(serializer, value)
        val decoded = PackedFormat.decodeFromByteArray(serializer, bytes)
        assertEquals(value, decoded, "Roundtrip failed for ${T::class.simpleName}")
    }

    private fun <T> roundtrip(serializer: KSerializer<T>, value: T) {
        val bytes = PackedFormat.encodeToByteArray(serializer, value)
        val decoded = PackedFormat.decodeFromByteArray(serializer, bytes)
        assertEquals(value, decoded, "Roundtrip failed for ${serializer.descriptor.serialName}")
    }

    // -------------------------------------------------------------------------
    // 1. Primitive & Basic Structure Tests
    // -------------------------------------------------------------------------

    @Test
    fun `simple ints and booleans roundtrip`() {
        val cases = listOf(
            SimpleIntsAndBooleans(1, 100, true, false),
            SimpleIntsAndBooleans(Int.MAX_VALUE, Int.MIN_VALUE, false, true),
            SimpleIntsAndBooleans(0, 0, false, false)
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `all primitive types roundtrip`() {
        val cases = listOf(
            AllPrimitiveTypes(
                intVal = 123, longVal = 456L, shortVal = 7, byteVal = 8,
                floatVal = 3.5f, doubleVal = 2.75, charVal = 'A',
                boolVal = true, stringVal = "Hello"
            ),
            AllPrimitiveTypes(
                intVal = Int.MIN_VALUE, longVal = Long.MAX_VALUE,
                shortVal = Short.MIN_VALUE, byteVal = Byte.MAX_VALUE,
                floatVal = -1.0f, doubleVal = 1.0, charVal = 'Ω',
                boolVal = false, stringVal = "Unicode ✓ and longer text"
            )
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `all primitive types nullable roundtrip`() {
        val cases = listOf(
            AllPrimitiveTypesNullable(
                intVal = 123, longVal = 456L, shortVal = 7, byteVal = 8,
                floatVal = 3.5f, doubleVal = 2.75, charVal = 'A',
                boolVal = true, stringVal = "Hello"
            ),
            AllPrimitiveTypesNullable(
                intVal = null, longVal = null, shortVal = null, byteVal = null,
                floatVal = null, doubleVal = null, charVal = null,
                boolVal = null, stringVal = null
            )
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `no booleans and no nulls roundtrip`() {
        // Ensures header optimization (0 flags) works correctly
        val cases = listOf(
            NoBooleansNoNulls(42, 123_456_789L, "No flags here"),
            NoBooleansNoNulls(-1, Long.MIN_VALUE, "")
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `enum payload roundtrip`() {
        val cases = listOf(
            EnumPayload(1, Status.NEW, null),
            EnumPayload(2, Status.IN_PROGRESS, Status.DONE),
            EnumPayload(3, Status.DONE, Status.NEW)
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `nullable fields payload roundtrip`() {
        val cases = listOf(
            NullableFieldsPayload(null, null, null, null),
            NullableFieldsPayload(10, "Alice", 999L, true),
            NullableFieldsPayload(-5, "", null, false)
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `nullable booleans and non booleans roundtrip`() {
        val cases = listOf(
            NullableBooleansAndNonBooleans(null, false, null, null, "all-null-bools"),
            NullableBooleansAndNonBooleans(true, true, false, 123, "mixed"),
            NullableBooleansAndNonBooleans(false, true, null, -1, "different")
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `string heavy payload roundtrip`() {
        val cases = listOf(
            StringHeavyPayload("", "", "", null),
            StringHeavyPayload("short", "longer", "unicode ✓✓✓", "present"),
            StringHeavyPayload("line1\nline2", "tabs\t", "{\"k\":\"v\"}", "")
        )
        cases.forEach { roundtrip(it) }
    }

    // -------------------------------------------------------------------------
    // 2. Annotation & Inline Class Tests (VarInt, Duration, etc.)
    // -------------------------------------------------------------------------

    @Test
    fun `varint and varuint payload roundtrip`() {
        val cases = listOf(
            VarIntVarUIntPayload(
                signedIntVar = -1,                // zigzag
                signedLongVar = -123_456_789L,    // zigzag
                unsignedIntVar = 0,               // varint
                unsignedLongVar = 1L,             // varlong
                plainInt = 42,
                plainLong = 9_999L
            ),
            VarIntVarUIntPayload(
                signedIntVar = Int.MIN_VALUE,
                signedLongVar = Long.MIN_VALUE,
                unsignedIntVar = Int.MAX_VALUE,
                unsignedLongVar = Long.MAX_VALUE,
                plainInt = -1000,
                plainLong = 123_456_789_012_345L
            )
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `uint inline payload roundtrip`() {
        val cases = listOf(
            UIntInlinePayload(1, 0u, 1u, true),
            UIntInlinePayload(2, UInt.MAX_VALUE, 123_456_789u, false)
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `duration inline payload roundtrip`() {
        val cases = listOf(
            DurationInlinePayload("zero-null", 0.milliseconds, null, 0),
            DurationInlinePayload("mixed", 5.seconds + 250.milliseconds, 2.minutes, 10),
            DurationInlinePayload("large", (10_000).seconds, 123_456.milliseconds, -1)
        )
        cases.forEach { roundtrip(it) }
    }

    @Test
    fun `instant inline payload roundtrip`() {
        val cases = listOf(
            InstantInlinePayload(Instant.fromEpochMilliseconds(0), null, true, 1),
            InstantInlinePayload(
                Instant.fromEpochMilliseconds(1_000_000_000_123),
                Instant.fromEpochMilliseconds(2_000_000_000_456),
                false, 2
            )
        )
        cases.forEach { roundtrip(it) }
    }

    // -------------------------------------------------------------------------
    // 3. Top-Level Primitive Tests
    // -------------------------------------------------------------------------

    @Test
    fun `top level primitives roundtrip`() {
        listOf(true, false).forEach { roundtrip(it) }
        listOf('A', 'ñ', 'क', '✓', '\u0000', '\uFFFF').forEach { roundtrip(it) }
        listOf((-1).toByte(), Byte.MAX_VALUE, 42.toByte()).forEach { roundtrip(it) }
        listOf((-1).toShort(), Short.MAX_VALUE, 42.toShort()).forEach { roundtrip(it) }
        listOf(-1, 1, Int.MIN_VALUE, Int.MAX_VALUE, 42).forEach { roundtrip(it) }
        listOf(-1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 42L).forEach { roundtrip(it) }
        listOf(-1f, 1f, Float.MIN_VALUE, Float.NaN).forEach { roundtrip(it) }
        listOf(-1.0, Double.MAX_VALUE, Double.NaN).forEach { roundtrip(it) }
        listOf(Status.NEW, Status.DONE).forEach { roundtrip(it) }
        listOf("a", "longer string", "").forEach { roundtrip(it) }
    }

    @Test
    fun `top level nullable roundtrip`() {
        val s: String? = "asae"
        val n: String? = null
        roundtrip(s)
        roundtrip(n)
    }

    // -------------------------------------------------------------------------
    // 4. Extended Tests: Collections & Nesting
    // -------------------------------------------------------------------------

    @Test
    fun `nested class roundtrip`() {
        val value = Parent(id = 1, child = Child(2))
        roundtrip(value)
    }

    @Test
    fun `list in object roundtrip`() {
        val value = WithList(id = 1, items = listOf(1, 2, 3))
        roundtrip(value)
    }

    @Test
    fun `list of lists roundtrip`() {
        val grid = Grid(
            rows = listOf(
                listOf(1, 2, 3),
                emptyList(),
                listOf(4)
            )
        )
        roundtrip(grid)
    }

    @Test
    fun `optional list roundtrip`() {
        val present = NestedOptionalList(1, listOf("alpha", "beta"), listOf(10, 20))
        val missing = NestedOptionalList(2, null, emptyList())
        roundtrip(present)
        roundtrip(missing)
    }

    @Test
    fun `deep nested nullable object roundtrip`() {
        val full = DeepNested(
            name = "root",
            level1 = Level1(
                active = true,
                level2 = Level2("payload", listOf(listOf(1, 2), listOf(3, 4)))
            )
        )
        val partial = DeepNested(
            name = "root-partial",
            level1 = Level1(
                active = false,
                level2 = null
            )
        )
        roundtrip(full)
        roundtrip(partial)
    }

    @Test
    fun `map holder roundtrip`() {
        val value = MapHolder(
            config = mapOf("host" to "localhost", "port" to "8080"),
            counts = mapOf("retries" to 3, "timeouts" to 0)
        )
        val valueNull = MapHolder(
            config = emptyMap(),
            counts = null
        )
        roundtrip(value)
        roundtrip(valueNull)
    }

   @Test
    fun `complex map with lists and objects roundtrip`() {
        val value = ComplexMap(
            history = mapOf(
                "monday" to listOf(12, 14, 15),
                "tuesday" to listOf(9, 10)
            ),
            registry = mapOf(
                101 to Child(1),
                202 to Child(99)
            )
        )
        roundtrip(value)
    }

    @Test
    fun `mixed bag roundtrip`() {
        val value = MixedBag(
            id = 42,
            meta = mapOf("k1" to "v1"),
            children = listOf(Child(1), Child(2)),
            flags = listOf(true, null, false)
        )
        roundtrip(value)
    }

    @Test
    fun `map with nullable values`() {
        val value = NullableMap(
            mapOf("k1" to "v1", "k2" to null, "k3" to "v3")
        )
        roundtrip(value)
    }

    // -------------------------------------------------------------------------
    // 5. Negative / Edge Case Tests
    // -------------------------------------------------------------------------

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
        val bytes = byteArrayOf(0xC2.toByte(), 0x41) // 0x41 ('A') is not a valid continuation
        val decoder = PackedDecoder(bytes)
        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

}
