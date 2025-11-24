package com.eignex.kencode

import kotlin.random.Random
import kotlin.test.*

class Base64Test {

    private fun assertRoundtrip(
        codec: Base64, bytes: ByteArray, message: String? = null
    ) {
        val encoded = codec.encode(bytes)
        val decoded = codec.decode(encoded)

        if (message != null) {
            assertContentEquals(bytes, decoded, message)
        } else {
            assertContentEquals(bytes, decoded)
        }

        if (bytes.isEmpty()) {
            assertTrue(encoded.isEmpty())
            assertEquals(0, codec.decode("").size)
        }
    }

    private fun randomRoundtrip(codec: Base64) {
        val rnd = Random(1234L)
        for (len in 0..128) {
            val bytes = ByteArray(len).also { rnd.nextBytes(it) }
            assertRoundtrip(codec, bytes, "Roundtrip failed for length=$len")
        }
    }

    @Test
    fun `empty input should encode to and decode from empty string`() {
        val bytes = ByteArray(0)
        val encoded = Base64.encode(bytes, 0, 0)
        assertTrue(encoded.isEmpty())
        val decoded = Base64.decode("")
        assertEquals(0, decoded.size)
    }

    @Test
    fun `fixed patterns should roundtrip for Base64`() {
        val patterns = listOf(
            byteArrayOf(),
            byteArrayOf(0),
            byteArrayOf(1),
            byteArrayOf(-1),
            byteArrayOf(0, 1, 2, 3, 4),
            byteArrayOf(-1, -2, -3, -4),
            byteArrayOf(127, -128, 0, 42)
        )

        for (bytes in patterns) {
            assertRoundtrip(
                Base64, bytes, "Failed for pattern=${bytes.toList()}"
            )
        }
    }

    @Test
    fun `fixed patterns should roundtrip for Base64UrlSafe`() {
        val patterns = listOf(
            byteArrayOf(),
            byteArrayOf(0),
            byteArrayOf(1),
            byteArrayOf(-1),
            byteArrayOf(0, 1, 2, 3, 4),
            byteArrayOf(-1, -2, -3, -4),
            byteArrayOf(127, -128, 0, 42)
        )

        for (bytes in patterns) {
            assertRoundtrip(
                Base64UrlSafe, bytes, "Failed for pattern=${bytes.toList()}"
            )
        }
    }

    @Test
    fun `various lengths should roundtrip for Base64`() {
        randomRoundtrip(Base64)
    }

    @Test
    fun `offset and length encoding should roundtrip`() {
        val rnd = Random(42L)
        val buffer = ByteArray(100).also { rnd.nextBytes(it) }
        val offset = 10
        val length = 50
        val slice = buffer.copyOfRange(offset, offset + length)

        val encoded = Base64.encode(buffer, offset, length)
        val decoded = Base64.decode(encoded)

        assertEquals(length, decoded.size)
        assertContentEquals(slice, decoded)
    }

    @Test
    fun `invalid offset or length should throw and boundary should succeed`() {
        val data = ByteArray(10) { it.toByte() }

        assertFailsWith<IllegalArgumentException> { Base64.encode(data, -1, 5) }
        assertFailsWith<IllegalArgumentException> { Base64.encode(data, 0, -1) }
        assertFailsWith<IllegalArgumentException> { Base64.encode(data, 5, 6) }

        val encoded = Base64.encode(data, data.size, 0)
        assertTrue(encoded.isEmpty())
    }

    @Test
    fun `invalid Base64 length should throw`() {
        val bytes = ByteArray(10) { it.toByte() }
        val encoded = Base64.encode(bytes)

        val invalid = encoded + "A"

        assertFailsWith<IllegalArgumentException> {
            Base64.decode(invalid)
        }
    }

    @Test
    fun `invalid Base64 character should throw`() {
        val encoded = Base64.encode(byteArrayOf(1, 2, 3, 4, 5))
        val corrupted = "?" + encoded.drop(1)

        assertFailsWith<IllegalArgumentException> {
            Base64.decode(corrupted)
        }
    }

    @Test
    fun `RFC 4648 test vectors should encode and decode correctly`() {
        val vectors = listOf(
            "" to "",
            "f" to "Zg==",
            "fo" to "Zm8=",
            "foo" to "Zm9v",
            "foob" to "Zm9vYg==",
            "fooba" to "Zm9vYmE=",
            "foobar" to "Zm9vYmFy"
        )

        for ((plain, expectedEncoded) in vectors) {
            val bytes = plain.toByteArray()
            val encoded = Base64.encode(bytes, 0, bytes.size)
            assertEquals(
                expectedEncoded, encoded, "Encoding mismatch for '$plain'"
            )

            val decoded = Base64.decode(expectedEncoded)
            assertContentEquals(
                bytes, decoded, "Decoding mismatch for '$plain'"
            )
        }
    }

    @Test
    fun `alphabet with invalid length should throw`() {
        assertFailsWith<IllegalArgumentException> {
            Base64(CharArray(63))
        }
        assertFailsWith<IllegalArgumentException> {
            Base64(CharArray(65))
        }
    }


}
