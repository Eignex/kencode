@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.eignex.kencode

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.BooleanArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.jvm.JvmInline
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PackedBoundaryTest {
    @Serializable
    @JvmInline
    value class Flag(val value: Boolean)

    @Serializable
    @JvmInline
    value class OptionalFlag(val value: Boolean?)

    @Serializable
    @JvmInline
    value class NestedFlag(val value: Flag)

    @Serializable
    data class Child(val enabled: Boolean, val count: Int)

    @Serializable
    @JvmInline
    value class WrappedChild(val value: Child)

    @Serializable
    data class InlineHolder(
        val before: Boolean,
        val flag: Flag,
        val child: WrappedChild,
        val optional: OptionalFlag,
        val nested: NestedFlag,
        val tail: Int,
        val after: Boolean,
    )

    @Serializable
    data class ContextHolder(val before: Boolean, @Contextual val child: Child, val tail: Int, val after: Boolean)

    @Serializable
    data class NullableContextHolder(val before: Boolean, @Contextual val child: Child?, val tail: Int)

    @Serializable
    data class ContextBooleanHolder(@Contextual val flag: Boolean, val tail: Int)

    @Serializable
    data class CollectionHolder(val items: List<Int>, val tail: Int)

    @Serializable
    data class ArraysHolder(val before: Boolean, val flags: BooleanArray, val tail: Int)

    private val contextualFormat = PackedFormat {
        serializersModule = SerializersModule {
            contextual(Child.serializer())
            contextual(Boolean.serializer())
        }
    }

    private fun <T> roundtrip(serializer: KSerializer<T>, value: T, format: PackedFormat = PackedFormat) {
        assertEquals(value, format.decodeFromByteArray(serializer, format.encodeToByteArray(serializer, value)))
    }

    @Test
    fun `boolean arrays use the list bitmap at byte boundaries`() {
        for (size in listOf(0, 1, 7, 8, 9, 63, 64, 65)) {
            val flags = BooleanArray(size) { it % 3 == 0 }
            val bytes = PackedFormat.encodeToByteArray(BooleanArraySerializer(), flags)
            assertContentEquals(PackedFormat.encodeToByteArray(flags.toList()), bytes)
            assertContentEquals(flags, PackedFormat.decodeFromByteArray(BooleanArraySerializer(), bytes))
            val holder = ArraysHolder(true, flags, 42)
            val decoded = PackedFormat.decodeFromByteArray<ArraysHolder>(PackedFormat.encodeToByteArray(holder))
            assertEquals(holder.before, decoded.before)
            assertContentEquals(flags, decoded.flags)
            assertEquals(holder.tail, decoded.tail)
        }
    }

    @Test
    fun `boolean array decoder accepts an existing boolean list bitmap`() {
        val bytes = PackedFormat.encodeToByteArray(listOf(true, false, true))
        assertContentEquals(
            booleanArrayOf(true, false, true),
            PackedFormat.decodeFromByteArray(BooleanArraySerializer(), bytes),
        )
    }

    @Test
    fun `inline boolean fields roundtrip`() {
        @Serializable
        data class Holder(val flag: Flag)
        roundtrip(Holder.serializer(), Holder(Flag(true)))
        roundtrip(Holder.serializer(), Holder(Flag(false)))
    }

    @Test
    fun `inline structured fields preserve parent headers and following values`() {
        @Serializable
        data class Holder(val before: Boolean, val child: WrappedChild, val tail: Int)
        roundtrip(Holder.serializer(), Holder(true, WrappedChild(Child(true, 17)), 42))
    }

    @Test
    fun `inline boolean nullable and nested representations roundtrip`() {
        for (flag in listOf(null, false, true)) {
            val value = InlineHolder(
                true,
                Flag(false),
                WrappedChild(Child(true, 17)),
                OptionalFlag(flag),
                NestedFlag(Flag(true)),
                42,
                false,
            )
            roundtrip(InlineHolder.serializer(), value)
            roundtrip(OptionalFlag.serializer(), OptionalFlag(flag))
        }
        roundtrip(ListSerializer(Flag.serializer().nullable), listOf(Flag(true), null, Flag(false)))
        roundtrip(
            ListSerializer(WrappedChild.serializer().nullable),
            listOf(null, WrappedChild(Child(false, 17)), null),
        )
    }

    @Test
    fun `contextual children preserve parent headers and following values`() {
        for (before in listOf(false, true)) {
            roundtrip(ContextHolder.serializer(), ContextHolder(before, Child(true, 17), 42, false), contextualFormat)
            roundtrip(
                NullableContextHolder.serializer(),
                NullableContextHolder(before, Child(false, 17), 42),
                contextualFormat,
            )
            roundtrip(NullableContextHolder.serializer(), NullableContextHolder(before, null, 42), contextualFormat)
        }
    }

    @Test
    fun `contextual primitives and nullable collection elements roundtrip`() {
        roundtrip(ContextBooleanHolder.serializer(), ContextBooleanHolder(true, 42), contextualFormat)
        val contextualChild = kotlinx.serialization.ContextualSerializer(Child::class)
        roundtrip(
            ListSerializer(contextualChild.nullable),
            listOf(null, Child(true, 17), Child(false, 0), null),
            contextualFormat,
        )
        val contextualBoolean = kotlinx.serialization.ContextualSerializer(Boolean::class)
        roundtrip(ListSerializer(contextualBoolean.nullable), listOf(true, null, false), contextualFormat)
    }

    @Test
    fun `element index advances after serializable fields`() {
        val descriptor = CollectionHolder.serializer().descriptor
        val decoder = PackedDecoder(PackedFormat.encodeToByteArray(CollectionHolder(listOf(1), 42)))
        decoder.beginStructure(descriptor)
        assertEquals(0, decoder.decodeElementIndex(descriptor))
        assertEquals(
            listOf(1),
            decoder.decodeSerializableElement(descriptor, 0, ListSerializer(Int.serializer()), null),
        )
        assertEquals(1, decoder.decodeElementIndex(descriptor))
        assertEquals(42, decoder.decodeIntElement(descriptor, 1))
        assertEquals(
            kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE,
            decoder.decodeElementIndex(descriptor),
        )
        decoder.endStructure(descriptor)
    }
}
