package com.eignex.kencode

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapEntrySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test
import kotlin.test.assertEquals

class PackedStructureEdgeTest {
    @Serializable
    data class EntryHolder(val entry: Map.Entry<String, Int>, val flag: Boolean)

    @Serializable
    data class Shape(val sides: Int)

    @Test
    fun `map entries roundtrip`() {
        val serializer = MapEntrySerializer(String.serializer(), Int.serializer())
        val entry = mapOf("a" to 1).entries.single()
        val decoded = PackedFormat.decodeFromByteArray(serializer, PackedFormat.encodeToByteArray(serializer, entry))
        assertEquals(entry.key to entry.value, decoded.key to decoded.value)

        val holder = EntryHolder(mapOf("key" to -5).entries.single(), true)
        val back = EncodedFormat.decodeFromString(
            EntryHolder.serializer(),
            EncodedFormat.encodeToString(EntryHolder.serializer(), holder),
        )
        assertEquals(holder.entry.key to holder.entry.value, back.entry.key to back.entry.value)
        assertEquals(holder.flag, back.flag)
    }

    @Test
    fun `polymorphic primitive subclasses roundtrip`() {
        val format = PackedFormat {
            serializersModule = SerializersModule {
                polymorphic(Any::class) {
                    subclass(Boolean::class, Boolean.serializer())
                    subclass(Int::class, Int.serializer())
                    subclass(Shape::class)
                }
            }
        }
        val serializer = PolymorphicSerializer(Any::class)
        for (value in listOf<Any>(true, false, 42, Shape(3))) {
            assertEquals(value, format.decodeFromByteArray(serializer, format.encodeToByteArray(serializer, value)))
        }
    }
}
