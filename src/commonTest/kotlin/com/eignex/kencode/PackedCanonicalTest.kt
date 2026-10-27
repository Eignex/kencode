package com.eignex.kencode

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PackedCanonicalTest {
    @Serializable
    data class Flags(val a: Boolean, val b: Int)

    @Serializable
    data class NullableBool(val a: Boolean?)

    @Serializable
    enum class Color { RED, GREEN, BLUE }

    @Serializable
    data class NullableColor(val c: Color?)

    @Serializable
    data class Letter(val c: Char)

    @Serializable
    data class Text(val s: String)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun <T> assertCanonical(serializer: KSerializer<T>, value: T, expected: ByteArray) {
        val encoded = PackedFormat.encodeToByteArray(serializer, value)
        assertContentEquals(expected, encoded)
        assertEquals(value, PackedFormat.decodeFromByteArray(serializer, encoded))
    }

    private fun <T> assertRejected(serializer: KSerializer<T>, input: ByteArray) {
        assertFailsWith<IllegalArgumentException> { PackedFormat.decodeFromByteArray(serializer, input) }
    }

    @Test
    fun `class header padding bits are rejected`() {
        assertCanonical(Flags.serializer(), Flags(true, 1), bytes(0x01, 0x01))
        assertRejected(Flags.serializer(), bytes(0xFF, 0x01))
        assertRejected(Flags.serializer(), bytes(0x03, 0x01))
    }

    @Test
    fun `null elements reject leftover value bits`() {
        assertCanonical(NullableBool.serializer(), NullableBool(null), bytes(0x02))
        assertRejected(NullableBool.serializer(), bytes(0x03))
        assertCanonical(NullableColor.serializer(), NullableColor(null), bytes(0x01))
        assertRejected(NullableColor.serializer(), bytes(0x03))
        assertRejected(NullableColor.serializer(), bytes(0x05))
    }

    @Test
    fun `boolean bytes must be zero or one`() {
        assertCanonical(Boolean.serializer(), true, bytes(0x01))
        assertRejected(Boolean.serializer(), bytes(0x02))
        val map = MapSerializer(Int.serializer(), Boolean.serializer())
        assertCanonical(map, mapOf(5 to true), bytes(0x01, 0x05, 0x01))
        assertRejected(map, bytes(0x01, 0x05, 0x07))
    }

    @Test
    fun `null markers must be zero or one`() {
        val serializer = Int.serializer().nullable
        assertCanonical(serializer, null, bytes(0x01))
        assertCanonical(serializer, 5, bytes(0x00, 0x05))
        assertRejected(serializer, bytes(0x03))
        assertRejected(serializer, bytes(0x02, 0x05))
    }

    @Test
    fun `overlong UTF-8 chars are rejected`() {
        assertCanonical(Letter.serializer(), Letter('\u0000'), bytes(0x00))
        assertRejected(Letter.serializer(), bytes(0xC0, 0x80))
        assertRejected(Letter.serializer(), bytes(0xC1, 0xBF))
        assertCanonical(Letter.serializer(), Letter('߿'), bytes(0xDF, 0xBF))
        assertRejected(Letter.serializer(), bytes(0xE0, 0x9F, 0xBF))
        assertCanonical(Letter.serializer(), Letter('\uD800'), bytes(0xED, 0xA0, 0x80))
    }

    @Test
    fun `invalid UTF-8 strings are rejected`() {
        assertCanonical(Text.serializer(), Text("hé😀"), bytes(0x07, 0x68, 0xC3, 0xA9, 0xF0, 0x9F, 0x98, 0x80))
        assertRejected(Text.serializer(), bytes(0x01, 0xFF))
        assertRejected(Text.serializer(), bytes(0x02, 0xC0, 0x80))
        assertRejected(Text.serializer(), bytes(0x03, 0xED, 0xA0, 0x80))
    }

    @Test
    fun `strings with unpaired surrogates are rejected when encoding`() {
        // Kotlin/JS drops unpaired surrogates from string literals.
        fun text(vararg codes: Int) = codes.map { Char(it) }.joinToString("")
        for (value in listOf(text(0xD800), text(0x61, 0xDC00, 0x62), text(0xDE00, 0xD83D))) {
            assertFailsWith<IllegalArgumentException> { PackedFormat.encodeToByteArray(Text.serializer(), Text(value)) }
            assertFailsWith<IllegalArgumentException> { PackedFormat.encodeToByteArray(String.serializer(), value) }
        }
    }

    @Test
    fun `packed list padding bits are rejected`() {
        val booleans = ListSerializer(Boolean.serializer())
        assertCanonical(booleans, listOf(true), bytes(0x01, 0x01))
        assertRejected(booleans, bytes(0x01, 0xFF))
        val colors = ListSerializer(Color.serializer())
        assertCanonical(colors, listOf(Color.BLUE), bytes(0x01, 0x02))
        assertRejected(colors, bytes(0x01, 0x06))
        assertCanonical(Color.serializer(), Color.BLUE, bytes(0x02))
        assertRejected(Color.serializer(), bytes(0x06))
    }

    @Test
    fun `nullable list bitmap padding bits are rejected`() {
        val serializer = ListSerializer(Int.serializer().nullable)
        assertCanonical(serializer, listOf(5, null), bytes(0x02, 0x02, 0x05))
        assertRejected(serializer, bytes(0x02, 0x06, 0x05))
        assertRejected(serializer, bytes(0x01, 0xFE, 0x05))
    }
}
