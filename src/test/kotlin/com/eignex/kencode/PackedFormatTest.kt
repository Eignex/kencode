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

    // ---------- Test models for structured payloads ----------

    @Serializable
    data class SimpleIntsAndBooleans(
        val id: Int, val score: Int, val active: Boolean, val deleted: Boolean
    )

    @Serializable
    data class AllPrimitiveTypes(
        val intVal: Int,
        val longVal: Long,
        val shortVal: Short,
        val byteVal: Byte,
        val floatVal: Float,
        val doubleVal: Double,
        val charVal: Char,
        val boolVal: Boolean,
        val stringVal: String
    )


    @Serializable
    data class AllPrimitiveTypesNullable(
        val intVal: Int?,
        val longVal: Long?,
        val shortVal: Short?,
        val byteVal: Byte?,
        val floatVal: Float?,
        val doubleVal: Double?,
        val charVal: Char?,
        val boolVal: Boolean?,
        val stringVal: String?
    )

    @Serializable
    data class NoBooleansNoNulls(
        val x: Int, val y: Long, val msg: String
    )

    @Serializable
    enum class Status {
        NEW, IN_PROGRESS, DONE
    }

    @Serializable
    data class EnumPayload(
        val id: Int, val status: Status, val secondaryStatus: Status?
    )

    @Serializable
    data class NullableFieldsPayload(
        val maybeId: Int?,
        val maybeName: String?,
        val maybeScore: Long?,
        val maybeFlag: Boolean?
    )

    @Serializable
    data class NullableBooleansAndNonBooleans(
        val flag1: Boolean?,
        val flag2: Boolean,
        val flag3: Boolean?,
        val count: Int?,
        val label: String
    )

    @Serializable
    data class StringHeavyPayload(
        val a: String, val b: String, val c: String, val d: String?
    )

    @Serializable
    data class VarIntVarUIntPayload(
        @VarInt val signedIntVar: Int,
        @VarInt val signedLongVar: Long,
        @VarUInt val unsignedIntVar: Int,
        @VarUInt val unsignedLongVar: Long,
        val plainInt: Int,
        val plainLong: Long
    )

    @Serializable
    data class UIntInlinePayload(
        val id: Int, val first: UInt, val second: UInt, val active: Boolean
    )

    @Serializable
    data class DurationInlinePayload(
        val label: String,
        val primary: Duration,
        val secondary: Duration?,
        val count: Int
    )

    @Serializable
    data class InstantInlinePayload(
        val first: Instant,
        val second: Instant?,
        val active: Boolean,
        val seq: Int
    )

    @Serializable
    data class Child(val value: Int)

    @Serializable
    data class Parent(val id: Int, val child: Child)

    @Serializable
    data class WithList(val id: Int, val items: List<Int>)


    private fun <T> roundtrip(
        serializer: KSerializer<T>, value: T
    ) {
        val bytes = PackedFormat.encodeToByteArray(serializer, value)
        val decoded = PackedFormat.decodeFromByteArray(serializer, bytes)
        assertEquals(value, decoded)
    }

    // ---------- Structured payload tests (one per model) ----------

    @Test
    fun `simple ints and booleans roundtrip`() {
        val serializer = SimpleIntsAndBooleans.serializer()
        val cases = listOf(
            SimpleIntsAndBooleans(
                id = 1, score = 100, active = true, deleted = false
            ), SimpleIntsAndBooleans(
                id = Int.MAX_VALUE,
                score = Int.MIN_VALUE,
                active = false,
                deleted = true
            ), SimpleIntsAndBooleans(
                id = 0, score = 0, active = false, deleted = false
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `all primitive types roundtrip`() {
        val serializer = AllPrimitiveTypes.serializer()
        val cases = listOf(
            AllPrimitiveTypes(
                intVal = 123,
                longVal = 456L,
                shortVal = 7,
                byteVal = 8,
                floatVal = 3.5f,
                doubleVal = 2.75,
                charVal = 'A',
                boolVal = true,
                stringVal = "Hello"
            ), AllPrimitiveTypes(
                intVal = Int.MIN_VALUE,
                longVal = Long.MAX_VALUE,
                shortVal = Short.MIN_VALUE,
                byteVal = Byte.MAX_VALUE,
                floatVal = -1.0f,
                doubleVal = 1.0,
                charVal = 'Ω',
                boolVal = false,
                stringVal = "Unicode ✓ and longer text"
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `all primitive types nullable roundtrip`() {
        val serializer = AllPrimitiveTypesNullable.serializer()
        val cases = listOf(
            AllPrimitiveTypesNullable(
                intVal = 123,
                longVal = 456L,
                shortVal = 7,
                byteVal = 8,
                floatVal = 3.5f,
                doubleVal = 2.75,
                charVal = 'A',
                boolVal = true,
                stringVal = "Hello"
            ), AllPrimitiveTypesNullable(
                intVal = Int.MIN_VALUE,
                longVal = Long.MAX_VALUE,
                shortVal = Short.MIN_VALUE,
                byteVal = Byte.MAX_VALUE,
                floatVal = -1.0f,
                doubleVal = 1.0,
                charVal = 'Ω',
                boolVal = false,
                stringVal = "Unicode ✓ and longer text"
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `no booleans and no nulls roundtrip`() {
        val serializer = NoBooleansNoNulls.serializer()
        val cases = listOf(
            NoBooleansNoNulls(
                x = 42, y = 123_456_789L, msg = "No flags here"
            ), NoBooleansNoNulls(
                x = -1, y = Long.MIN_VALUE, msg = ""
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `enum payload roundtrip`() {
        val serializer = EnumPayload.serializer()
        val cases = listOf(
            EnumPayload(
                id = 1, status = Status.NEW, secondaryStatus = null
            ), EnumPayload(
                id = 2,
                status = Status.IN_PROGRESS,
                secondaryStatus = Status.DONE
            ), EnumPayload(
                id = 3, status = Status.DONE, secondaryStatus = Status.NEW
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `nullable fields payload roundtrip`() {
        val serializer = NullableFieldsPayload.serializer()
        val cases = listOf(
            NullableFieldsPayload(
                maybeId = null,
                maybeName = null,
                maybeScore = null,
                maybeFlag = null
            ), NullableFieldsPayload(
                maybeId = 10,
                maybeName = "Alice",
                maybeScore = 999L,
                maybeFlag = true
            ), NullableFieldsPayload(
                maybeId = -5,
                maybeName = "",
                maybeScore = null,
                maybeFlag = false
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `nullable booleans and non booleans roundtrip`() {
        val serializer = NullableBooleansAndNonBooleans.serializer()
        val cases = listOf(
            NullableBooleansAndNonBooleans(
                flag1 = null,
                flag2 = false,
                flag3 = null,
                count = null,
                label = "all-null-bools"
            ), NullableBooleansAndNonBooleans(
                flag1 = true,
                flag2 = true,
                flag3 = false,
                count = 123,
                label = "mixed"
            ), NullableBooleansAndNonBooleans(
                flag1 = false,
                flag2 = true,
                flag3 = null,
                count = -1,
                label = "different"
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `string heavy payload roundtrip`() {
        val serializer = StringHeavyPayload.serializer()
        val cases = listOf(
            StringHeavyPayload(
                a = "", b = "", c = "", d = null
            ), StringHeavyPayload(
                a = "short",
                b = "a bit longer string",
                c = "even longer string with unicode ✓✓✓",
                d = "nullable-but-present"
            ), StringHeavyPayload(
                a = "line1\nline2\nline3",
                b = "with\ttabs\tand\tspaces",
                c = "JSON-like: {\"k\":\"v\"}",
                d = ""
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `varint and varuint payload roundtrip`() {
        val serializer = VarIntVarUIntPayload.serializer()
        val cases = listOf(
            VarIntVarUIntPayload(
                signedIntVar = -1,                // zigzag-encoded
                signedLongVar = -123_456_789L,    // zigzag-encoded
                unsignedIntVar = 0,               // varint
                unsignedLongVar = 1L,             // varlong
                plainInt = 42,                    // fixed-width
                plainLong = 9_999L                // fixed-width
            ), VarIntVarUIntPayload(
                signedIntVar = Int.MIN_VALUE,
                signedLongVar = Long.MIN_VALUE,
                unsignedIntVar = Int.MAX_VALUE,
                unsignedLongVar = Long.MAX_VALUE,
                plainInt = -1000,
                plainLong = 123_456_789_012_345L
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `uint inline payload roundtrip`() {
        val serializer = UIntInlinePayload.serializer()
        val cases = listOf(
            UIntInlinePayload(
                id = 1, first = 0u, second = 1u, active = true
            ), UIntInlinePayload(
                id = 2,
                first = UInt.MAX_VALUE,
                second = 123_456_789u,
                active = false
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `duration inline payload roundtrip`() {
        val serializer = DurationInlinePayload.serializer()
        val cases = listOf(
            DurationInlinePayload(
                label = "zero-and-null",
                primary = 0.milliseconds,
                secondary = null,
                count = 0
            ), DurationInlinePayload(
                label = "mixed",
                primary = 5.seconds + 250.milliseconds,
                secondary = 2.minutes,
                count = 10
            ), DurationInlinePayload(
                label = "large",
                primary = (10_000).seconds,
                secondary = 123_456.milliseconds,
                count = -1
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `instant inline payload roundtrip`() {
        val serializer = InstantInlinePayload.serializer()
        val cases = listOf(
            InstantInlinePayload(
                first = Instant.fromEpochMilliseconds(0),
                second = null,
                active = true,
                seq = 1
            ), InstantInlinePayload(
                first = Instant.fromEpochMilliseconds(1_000_000_000_123),
                second = Instant.fromEpochMilliseconds(2_000_000_000_456),
                active = false,
                seq = 2
            )
        )

        for (value in cases) {
            roundtrip(serializer, value)
        }
    }

    @Test
    fun `top level bool roundtrip`() {
        for (b in listOf(true, false)) {
            roundtrip(Boolean.serializer(), b)
        }
    }

    @Test
    fun `top level char roundtrip`() {
        val testChars = listOf(
            'A',        // 1-byte
            'ñ',        // typical 2-byte
            'क',        // typical 3-byte
            '✓',         // symbol, 3-byte
            '\u0000', // min value, 1-byte UTF-8
            '\u007F', // max 1-byte UTF-8
            '\u0080', // min 2-byte UTF-8
            '\u07FF', // max 2-byte UTF-8
            '\u0800', // min 3-byte UTF-8
            '\uFFFF'  // max Char, 3-byte UTF-8
        )
        for (c in testChars) {
            roundtrip(Char.serializer(), c)
        }
    }

    @Test
    fun `top level byte roundtrip`() {
        for (b in listOf((-1).toByte(), Byte.MAX_VALUE, 42.toByte())) {
            roundtrip(Byte.serializer(), b)
        }
    }

    @Test
    fun `top level short roundtrip`() {
        for (s in listOf((-1).toShort(), Short.MAX_VALUE, 42.toShort())) {
            roundtrip(Short.serializer(), s)
        }
    }

    @Test
    fun `top level int roundtrip`() {
        for (i in listOf(-1, 1, Int.MIN_VALUE, Int.MAX_VALUE, 42)) {
            roundtrip(Int.serializer(), i)
        }
    }

    @Test
    fun `top level long roundtrip`() {
        for (l in listOf(-1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, 42L)) {
            roundtrip(Long.serializer(), l)
        }
    }

    @Test
    fun `top level float roundtrip`() {
        for (f in listOf(-1f, 1f, Float.MIN_VALUE, Float.NaN)) {
            roundtrip(Float.serializer(), f)
        }
    }

    @Test
    fun `top level double roundtrip`() {
        for (d in listOf(-1.0, Double.MAX_VALUE, Double.NaN)) {
            roundtrip(Double.serializer(), d)
        }
    }

    @Test
    fun `top level enum roundtrip`() {
        for (e in listOf(Status.NEW, Status.IN_PROGRESS, Status.DONE)) {
            roundtrip(Status.serializer(), e)
        }
    }

    @Test
    fun `top level string roundtrip`() {
        for (str in listOf("a", "longer string")) {
            roundtrip(String.serializer(), str)
        }
    }

    @Test
    fun `top level nullable roundtrip`() {
        val bytes = PackedFormat.encodeToByteArray<String?>("asae")
        val result: String? = PackedFormat.decodeFromByteArray(bytes)
        assertEquals("asae", result)
    }


    @Test
    fun `top level null roundtrip`() {
        val bytes = PackedFormat.encodeToByteArray<String?>(null)
        val result: String? = PackedFormat.decodeFromByteArray(bytes)
        assertNull(result)
    }

    @Test
    fun `single continuation byte should throw for top level char`() {
        // 0x80 is a continuation byte without a valid leading byte.
        val bytes = byteArrayOf(0x80.toByte())
        val decoder = PackedDecoder(bytes)

        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

    @Test
    fun `truncated 2-byte sequence should throw for top level char`() {
        // 0xC2 is a valid leading byte for a 2-byte sequence, but we don't
        // provide the required continuation byte → Unexpected EOF.
        val bytes = byteArrayOf(0xC2.toByte())
        val decoder = PackedDecoder(bytes)

        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

    @Test
    fun `invalid continuation byte should throw for top level char`() {
        // 0xC2 expects a continuation byte (10xxxxxx); 0x41 ('A') is invalid.
        val bytes = byteArrayOf(0xC2.toByte(), 0x41)
        val decoder = PackedDecoder(bytes)

        assertFailsWith<IllegalArgumentException> {
            decoder.decodeSerializableValue(Char.serializer())
        }
    }

    @Test
    fun `encoding nested class with PackedFormat should throw`() {
        val value = Parent(id = 1, child = Child(2))

        assertFailsWith<IllegalStateException> {
            PackedFormat.encodeToByteArray(Parent.serializer(), value)
        }
    }

    @Test
    fun `encoding list field with PackedFormat should throw`() {
        val value = WithList(id = 1, items = listOf(1, 2, 3))

        assertFailsWith<IllegalStateException> {
            PackedFormat.encodeToByteArray(WithList.serializer(), value)
        }
    }

    @Test
    fun `decoding nested class with PackedFormat should throw`() {
        // Minimal bytes: a single 0x00 varlong for flags; actual payload is irrelevant
        // because the decoder rejects nested structures based on the descriptor
        // before deserializing the nested value.
        val bytes = byteArrayOf(0x00)

        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(Parent.serializer(), bytes)
        }
    }

    @Test
    fun `decoding list field with PackedFormat should throw`() {
        val bytes = byteArrayOf(0x00)

        assertFailsWith<IllegalArgumentException> {
            PackedFormat.decodeFromByteArray(WithList.serializer(), bytes)
        }
    }
}
