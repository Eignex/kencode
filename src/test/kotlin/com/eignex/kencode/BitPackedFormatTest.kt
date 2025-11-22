package com.eignex.kencode

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

class BitPackedFormatRoundtripTest {

    @Test
    fun encode_decode_top_level_int_big_endian() {
        val out = java.io.ByteArrayOutputStream()
        val encoder = BitPackedEncoder(out)

        // Not in structure -> fixed-width int
        encoder.encodeInt(0x01020304)
        val bytes = out.toByteArray()

        assertContentEquals(
            byteArrayOf(0x01, 0x02, 0x03, 0x04),
            bytes
        )

        val decoder = BitPackedDecoder(bytes)
        val decoded = decoder.decodeInt()
        assertEquals(0x01020304, decoded)
    }

    @Test
    fun encode_decode_top_level_boolean_and_string() {
        val out = java.io.ByteArrayOutputStream()
        val encoder = BitPackedEncoder(out)

        encoder.encodeBoolean(true)
        encoder.encodeBoolean(false)
        encoder.encodeString("Hi")

        val bytes = out.toByteArray()
        val decoder = BitPackedDecoder(bytes)

        val b1 = decoder.decodeBoolean()
        val b2 = decoder.decodeBoolean()
        val s = decoder.decodeString()

        assertEquals(true, b1)
        assertEquals(false, b2)
        assertEquals("Hi", s)
    }

    @Serializable
    data class PayloadSimple(
        val id: Int,
        val score: Int,
        val active: Boolean,
        val deleted: Boolean,
        val title: String
    )

    @Serializable
    data class PayloadBooleansOnly(
        val a: Boolean,
        val b: Boolean,
        val c: Boolean,
        val d: Boolean
    )

    @Serializable
    data class PayloadNoBooleans(
        val x: Int,
        val y: Long,
        val msg: String
    )

    @Test
    fun roundtrip_simple_payload() {
        val payload = PayloadSimple(
            id = 123,
            score = 456,
            active = true,
            deleted = false,
            title = "Hello"
        )

        val bytes = BitPackedFormat.encodeToByteArray(
            PayloadSimple.serializer(),
            payload
        )

        val decoded = BitPackedFormat.decodeFromByteArray(
            PayloadSimple.serializer(),
            bytes
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun roundtrip_booleans_only_payload() {
        val payload = PayloadBooleansOnly(
            a = true,
            b = false,
            c = true,
            d = true
        )

        val bytes = BitPackedFormat.encodeToByteArray(
            PayloadBooleansOnly.serializer(),
            payload
        )

        val decoded = BitPackedFormat.decodeFromByteArray(
            PayloadBooleansOnly.serializer(),
            bytes
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun roundtrip_payload_without_booleans() {
        val payload = PayloadNoBooleans(
            x = 42,
            y = 123456789L,
            msg = "No flags here"
        )

        val bytes = BitPackedFormat.encodeToByteArray(
            PayloadNoBooleans.serializer(),
            payload
        )

        val decoded = BitPackedFormat.decodeFromByteArray(
            PayloadNoBooleans.serializer(),
            bytes
        )

        assertEquals(payload, decoded)
    }

    @Test
    fun roundtrip_float_and_double() {
        @Serializable
        data class FloatDoublePayload(
            val f: Float,
            val d: Double,
            val ok: Boolean
        )

        val payload = FloatDoublePayload(
            f = 3.14159f,
            d = 2.718281828,
            ok = true
        )

        val bytes = BitPackedFormat.encodeToByteArray(
            FloatDoublePayload.serializer(),
            payload
        )

        val decoded = BitPackedFormat.decodeFromByteArray(
            FloatDoublePayload.serializer(),
            bytes
        )

        // Floating-point equality is acceptable here because it's raw bits roundtrip
        assertEquals(payload, decoded)
    }

    @Test
    fun manual_encoder_decoder_roundtrip_for_payload() {
        val payload = PayloadSimple(
            id = 999,
            score = -1,     // still fixed-width; no zigzag/varint here
            active = true,
            deleted = true,
            title = "Manual"
        )

        val out = java.io.ByteArrayOutputStream()
        val encoder = BitPackedEncoder(out)

        encoder.encodeSerializableValue(PayloadSimple.serializer(), payload)
        val bytes = out.toByteArray()

        val decoder = BitPackedDecoder(bytes)
        val decoded = decoder.decodeSerializableValue(PayloadSimple.serializer())

        assertEquals(payload, decoded)
    }
}
