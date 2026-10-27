package com.eignex.kencode

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalSerializationApi::class)
class PackedEncodeDefaultTest {
    @Serializable
    data class NeverFlag(@EncodeDefault(EncodeDefault.Mode.NEVER) val flag: Boolean = true)

    @Serializable
    data class NeverString(@EncodeDefault(EncodeDefault.Mode.NEVER) val a: String = "", val b: String)

    @Serializable
    data class NestedNever(val inner: NeverFlag, val id: Int)

    @Serializable
    data class AlwaysDefaults(
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val flag: Boolean = true,
        val id: Int = 7,
        val name: String? = null,
    )

    @Test
    fun `elements skipped by EncodeDefault NEVER are rejected`() {
        assertFailsWith<SerializationException> { PackedFormat.encodeToByteArray(NeverFlag.serializer(), NeverFlag()) }
        assertFailsWith<SerializationException> {
            PackedFormat.encodeToByteArray(NeverString.serializer(), NeverString(b = "hello"))
        }
        assertFailsWith<SerializationException> {
            EncodedFormat.encodeToString(NestedNever.serializer(), NestedNever(NeverFlag(), 1))
        }
    }

    @Test
    fun `EncodeDefault NEVER elements with non-default values roundtrip`() {
        val value = NeverFlag(flag = false)
        val bytes = PackedFormat.encodeToByteArray(NeverFlag.serializer(), value)
        assertEquals(value, PackedFormat.decodeFromByteArray(NeverFlag.serializer(), bytes))
    }

    @Test
    fun `default values are still encoded`() {
        val value = AlwaysDefaults()
        val bytes = PackedFormat.encodeToByteArray(AlwaysDefaults.serializer(), value)
        assertEquals(value, PackedFormat.decodeFromByteArray(AlwaysDefaults.serializer(), bytes))
    }
}
