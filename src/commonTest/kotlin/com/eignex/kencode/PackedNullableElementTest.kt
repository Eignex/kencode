package com.eignex.kencode

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.serializer
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PackedNullableElementTest {
    @Serializable
    data class Box<T>(val before: Boolean, val value: T, val tail: Int)

    @Serializable
    data class Child(val enabled: Boolean, val count: Int)

    @Serializable
    @JvmInline
    value class Id(val value: Int)

    @Serializable
    data class NullableIntHolder(val before: Boolean, val value: Int?, val tail: Int)

    @Serializable
    data class NullableChildHolder(val before: Boolean, val value: Child?, val tail: Int)

    private fun <T> roundtrip(serializer: KSerializer<T>, value: T): ByteArray {
        val bytes = PackedFormat.encodeToByteArray(serializer, value)
        assertEquals(value, PackedFormat.decodeFromByteArray(serializer, bytes))
        return bytes
    }

    private fun <T> roundtripBox(element: KSerializer<T>, vararg values: T) {
        val serializer = Box.serializer(element)
        for (value in values) roundtrip(serializer, Box(true, value, 42))
    }

    @Test
    fun `generic nullable primitives roundtrip`() {
        roundtripBox(Int.serializer().nullable, null, 0, -1, 300)
        roundtripBox(String.serializer().nullable, null, "", "kencode")
        roundtripBox(Boolean.serializer().nullable, null, true, false)
        roundtripBox(Status.serializer().nullable, null, Status.NEW, Status.DONE)
        roundtripBox(Id.serializer().nullable, null, Id(7))
    }

    @Test
    fun `generic nullable structures roundtrip`() {
        roundtripBox(Child.serializer().nullable, null, Child(true, 3), Child(false, 0))
        roundtripBox(ListSerializer(Int.serializer()).nullable, null, emptyList(), listOf(1, 2, 3))
        roundtripBox(
            PolymorphicBase.serializer().nullable,
            null,
            PolymorphicBase.Action("a", 5),
            PolymorphicBase.Notification("n", true),
        )
        roundtripBox(Box.serializer(Int.serializer().nullable).nullable, null, Box(false, null, 1), Box(true, 2, 3))
    }

    @Test
    fun `generic nullable values match the declared nullable layout`() {
        assertContentEquals(
            PackedFormat.encodeToByteArray(NullableIntHolder.serializer(), NullableIntHolder(true, null, 42)),
            roundtrip(Box.serializer(Int.serializer().nullable), Box(true, null, 42)),
        )
        assertContentEquals(
            PackedFormat.encodeToByteArray(NullableIntHolder.serializer(), NullableIntHolder(true, 5, 42)),
            roundtrip(Box.serializer(Int.serializer().nullable), Box(true, 5, 42)),
        )
        assertContentEquals(
            PackedFormat.encodeToByteArray(
                NullableChildHolder.serializer(),
                NullableChildHolder(true, Child(true, 3), 42),
            ),
            roundtrip(Box.serializer(Child.serializer().nullable), Box(true, Child(true, 3), 42)),
        )
    }

    @Test
    fun `pair and triple with nullable components roundtrip`() {
        roundtrip(serializer<Pair<Int?, String>>(), Pair(null, "x"))
        roundtrip(serializer<Pair<Int?, String>>(), Pair(1, "x"))
        roundtrip(serializer<Pair<Child?, Child?>>(), Pair(Child(true, 1), null))
        roundtrip(serializer<Triple<String?, Boolean?, Int>>(), Triple(null, true, 2))
        roundtrip(serializer<Triple<String?, Boolean?, Int>>(), Triple("s", null, 2))
    }

    @Test
    fun `generic nullable values roundtrip through encoded format`() {
        val serializer = Box.serializer(Child.serializer().nullable)
        for (value in listOf<Box<Child?>>(Box(true, Child(true, 3), 42), Box(false, null, 0))) {
            assertEquals(
                value,
                EncodedFormat.decodeFromString(serializer, EncodedFormat.encodeToString(serializer, value)),
            )
        }
    }
}
