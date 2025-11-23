package com.eignex.kencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class BitPackedFormatTest {

    // ---------- Test models ----------

    @Serializable
    data class SimpleIntsAndBooleans(
        val id: Int,
        val score: Int,
        val active: Boolean,
        val deleted: Boolean
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
    data class NoBooleansNoNulls(
        val x: Int,
        val y: Long,
        val msg: String
    )

    enum class Status {
        NEW,
        IN_PROGRESS,
        DONE
    }

    @Serializable
    data class EnumPayload(
        val id: Int,
        val status: Status,
        val secondaryStatus: Status?
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
        val a: String,
        val b: String,
        val c: String,
        val d: String?
    )

    // ---------- Generic helper ----------

    private fun <T> roundtrip(
        serializer: KSerializer<T>,
        value: T
    ) {
        val bytes = BitPackedFormat.encodeToByteArray(serializer, value)
        val decoded = BitPackedFormat.decodeFromByteArray(serializer, bytes)
        assertEquals(value, decoded)
    }

    // ---------- Tests (one per model) ----------

    @Test
    fun `simple ints and booleans roundtrip`() {
        val serializer = SimpleIntsAndBooleans.serializer()
        val cases = listOf(
            SimpleIntsAndBooleans(
                id = 1,
                score = 100,
                active = true,
                deleted = false
            ),
            SimpleIntsAndBooleans(
                id = Int.MAX_VALUE,
                score = Int.MIN_VALUE,
                active = false,
                deleted = true
            ),
            SimpleIntsAndBooleans(
                id = 0,
                score = 0,
                active = false,
                deleted = false
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
            ),
            AllPrimitiveTypes(
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
                x = 42,
                y = 123_456_789L,
                msg = "No flags here"
            ),
            NoBooleansNoNulls(
                x = -1,
                y = Long.MIN_VALUE,
                msg = ""
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
                id = 1,
                status = Status.NEW,
                secondaryStatus = null
            ),
            EnumPayload(
                id = 2,
                status = Status.IN_PROGRESS,
                secondaryStatus = Status.DONE
            ),
            EnumPayload(
                id = 3,
                status = Status.DONE,
                secondaryStatus = Status.NEW
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
            ),
            NullableFieldsPayload(
                maybeId = 10,
                maybeName = "Alice",
                maybeScore = 999L,
                maybeFlag = true
            ),
            NullableFieldsPayload(
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
            // All nullable booleans are null, flag2 false
            NullableBooleansAndNonBooleans(
                flag1 = null,
                flag2 = false,
                flag3 = null,
                count = null,
                label = "all-null-bools"
            ),
            // Mixed values, including true/false and non-null count
            NullableBooleansAndNonBooleans(
                flag1 = true,
                flag2 = true,
                flag3 = false,
                count = 123,
                label = "mixed"
            ),
            // Another combination to exercise flag positions
            NullableBooleansAndNonBooleans(
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
                a = "",
                b = "",
                c = "",
                d = null
            ),
            StringHeavyPayload(
                a = "short",
                b = "a bit longer string",
                c = "even longer string with unicode ✓✓✓",
                d = "nullable-but-present"
            ),
            StringHeavyPayload(
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
}
