package com.eignex.kencode

import kotlin.test.*

class ByteCodecNumericDefaultTest {

    @Test
    fun intRoundtrip_base62_includesNegativeValues() {
        val codec: ByteCodec = Base62

        val values = listOf(
            Int.MIN_VALUE,
            -1,
            0,
            1,
            42,
            123456789,
            Int.MAX_VALUE
        )

        for (v in values) {
            val encoded = codec.encode(v)
            val decoded = codec.decodeInt(encoded)
            assertEquals(
                v,
                decoded,
                "Int roundtrip failed for $v (encoded='$encoded')"
            )
        }
    }

    @Test
    fun longRoundtrip_base62_includesNegativeValues() {
        val codec: ByteCodec = Base62

        val values = listOf(
            Long.MIN_VALUE,
            -1L,
            0L,
            1L,
            42L,
            1234567890123456789L,
            Long.MAX_VALUE
        )

        for (v in values) {
            val encoded = codec.encode(v)
            val decoded = codec.decodeLong(encoded)
            assertEquals(
                v,
                decoded,
                "Long roundtrip failed for $v (encoded='$encoded')"
            )
        }
    }

    @Test
    fun decodeInt_signExtend_fromShortPositive() {
        val bytes = byteArrayOf(0x7F) // 127

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeInt("ignored")
        assertEquals(127, decoded)
    }

    @Test
    fun decodeInt_signExtend_fromShortNegative() {
        val bytes = byteArrayOf(0xFF.toByte()) // -1 in 1 byte

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeInt("ignored")
        assertEquals(-1, decoded)
    }

    @Test
    fun decodeInt_signExtensionWithExtraLeadingBytes_positive() {
        // Extra leading 0x00, then 4-byte value 0x00 00 01 02 -> 0x00000102 = 258
        val bytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x02)

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeInt("ignored")
        assertEquals(0x00000102, decoded)
    }

    @Test
    fun decodeInt_signExtensionWithExtraLeadingBytes_negative() {
        // Extra leading 0xFF, then 4-byte value 0xFF FF FF FE -> -2
        val bytes = byteArrayOf(
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFE.toByte()
        )

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeInt("ignored")
        assertEquals(-2, decoded)
    }

    @Test
    fun decodeInt_inconsistentSignExtension_throws() {
        // Leading bytes not all equal, sign extension invalid
        val bytes = byteArrayOf(
            0x00,           // sign
            0xFF.toByte(), // mismatch
            0x00,
            0x01,
            0x02
        )

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        assertFailsWith<IllegalArgumentException> {
            codec.decodeInt("ignored")
        }
    }

    @Test
    fun decodeLong_signExtend_fromShortPositive() {
        val bytes = byteArrayOf(0x7F) // 127

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeLong("ignored")
        assertEquals(127L, decoded)
    }

    @Test
    fun decodeLong_signExtend_fromShortNegative() {
        val bytes = byteArrayOf(0xFF.toByte()) // -1

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeLong("ignored")
        assertEquals(-1L, decoded)
    }

    @Test
    fun decodeLong_signExtensionWithExtraLeadingBytes_positive() {
        // Extra leading 0x00, then 8-byte value 0x00 00 00 00 00 00 01 02 -> 0x0000000000000102 = 258
        val bytes = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x02
        )

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeLong("ignored")
        assertEquals(0x0000000000000102L, decoded)
    }

    @Test
    fun decodeLong_signExtensionWithExtraLeadingBytes_negative() {
        // Extra leading 0xFF, then 8-byte value 0xFF FF FF FF FF FF FF FE -> -2
        val bytes = byteArrayOf(
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFE.toByte()
        )

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        val decoded = codec.decodeLong("ignored")
        assertEquals(-2L, decoded)
    }

    @Test
    fun decodeLong_inconsistentSignExtension_throws() {
        val bytes = byteArrayOf(
            0x00,
            0xFF.toByte(), // mismatch
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x01,
            0x02
        )

        val codec = object : ByteCodec {
            override fun encode(
                input: ByteArray,
                offset: Int,
                length: Int
            ): String = throw UnsupportedOperationException("Not needed")

            override fun decode(input: CharSequence): ByteArray = bytes
        }

        assertFailsWith<IllegalArgumentException> {
            codec.decodeLong("ignored")
        }
    }
}
