package com.eignex.kencode

import kotlin.random.Random
import kotlin.test.*

class Base85Test {

    private fun assertRoundtrip(
        bytes: ByteArray, message: String? = null
    ) {
        val encoded = Base85.encode(bytes)
        val decoded = Base85.decode(encoded)

        if (message != null) {
            assertContentEquals(bytes, decoded, message)
        } else {
            assertContentEquals(bytes, decoded)
        }

        if (bytes.isEmpty()) {
            assertTrue(encoded.isEmpty())
            assertEquals(0, Base85.decode("").size)
        }
    }

    @Test
    fun `empty input should encode to and decode from empty string`() {
        val bytes = ByteArray(0)
        val encoded = Base85.encode(bytes, 0, 0)
        assertTrue(encoded.isEmpty())
        val decoded = Base85.decode("")
        assertEquals(0, decoded.size)
    }

    @Test
    fun `fixed patterns should roundtrip`() {
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
                bytes, "ASCII85 failed for pattern=${bytes.toList()}"
            )
        }
    }

    @Test
    fun `offset and length encoding should roundtrip`() {
        val rnd = Random(42L)
        val buffer = ByteArray(100).also { rnd.nextBytes(it) }
        val offset = 10
        val length = 50
        val slice = buffer.copyOfRange(offset, offset + length)

        val encoded = Base85.encode(buffer, offset, length)
        val decoded = Base85.decode(encoded)

        assertEquals(length, decoded.size)
        assertContentEquals(slice, decoded)
    }

    @Test
    fun `invalid offset or length should throw and boundary should succeed`() {
        val data = ByteArray(10) { it.toByte() }

        assertFailsWith<IllegalArgumentException> { Base85.encode(data, -1, 5) }
        assertFailsWith<IllegalArgumentException> { Base85.encode(data, 0, -1) }
        assertFailsWith<IllegalArgumentException> { Base85.encode(data, 5, 6) }

        val encoded = Base85.encode(data, data.size, 0)
        assertTrue(encoded.isEmpty())
    }

    @Test
    fun `invalid length remainder 1 should throw`() {
        // Length 1 → rem = 1 → should be rejected by decodeAscii85
        assertFailsWith<IllegalArgumentException> {
            Base85.decode("!")
        }
    }

    @Test
    fun `invalid Base85 character should throw for`() {
        val encoded = Base85.encode(byteArrayOf(1, 2, 3, 4))
        val corrupted = "~" + encoded.drop(1) // '~' not in ASCII85 alphabet

        assertFailsWith<IllegalArgumentException> {
            Base85.decode(corrupted)
        }
    }

    @Test
    fun `alphabet with invalid length should throw`() {
        assertFailsWith<IllegalArgumentException> {
            Base85(CharArray(84))
        }
        assertFailsWith<IllegalArgumentException> {
            Base85(CharArray(86))
        }
    }
}
