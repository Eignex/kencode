package com.eignex.kencode

import kotlin.random.Random
import kotlin.test.*

class BaseRadixTest {

    private val fixedPatterns = listOf(
        byteArrayOf(),
        byteArrayOf(0),
        byteArrayOf(1),
        byteArrayOf(-1),
        byteArrayOf(0, 1, 2, 3, 4),
        byteArrayOf(-1, -2, -3, -4),
        byteArrayOf(127, -128, 0, 42)
    )

    private fun assertRoundtrip(
        codec: BaseRadix, bytes: ByteArray, message: String? = null
    ) {
        val encoded = codec.encode(bytes)
        val decoded = codec.decode(encoded)
        if (message != null) assertContentEquals(bytes, decoded, message)
        else assertContentEquals(bytes, decoded)
        if (bytes.isEmpty()) {
            assertTrue(encoded.isEmpty())
            assertEquals(0, codec.decode("").size)
        }
    }

    private fun randomRoundtrip(codec: BaseRadix, seed: Long) {
        val rng = Random(seed)
        for (len in 0..(codec.blockSize * 2)) {
            val bytes = ByteArray(len).also { rng.nextBytes(it) }
            assertRoundtrip(
                codec,
                bytes,
                "Roundtrip failed for ${codec.javaClass.simpleName} length=$len"
            )
        }
    }

    @Test
    fun `fixed patterns should roundtrip for Base62 and Base36`() {
        for (codec in listOf(Base62, Base36)) {
            for (bytes in fixedPatterns) {
                assertRoundtrip(
                    codec,
                    bytes,
                    "Failed for ${codec.javaClass.simpleName} pattern=${bytes.toList()}"
                )
            }
        }
    }

    @Test
    fun `various lengths should roundtrip for all codecs`() {
        randomRoundtrip(Base62, seed = 1234L)
        randomRoundtrip(Base36, seed = 5678L)
    }

    @Test
    fun `offset and length encoding should roundtrip for all codecs`() {
        val rng = Random(42L)
        val buffer = ByteArray(100).also { rng.nextBytes(it) }
        val slice = buffer.copyOfRange(10, 60)

        for (codec in listOf(Base62, Base36)) {
            val encoded = codec.encode(buffer, 10, 50)
            val decoded = codec.decode(encoded)
            assertEquals(50, decoded.size)
            assertContentEquals(slice, decoded)
        }
    }

    @Test
    fun `invalid offset or length should throw and boundary should succeed`() {
        val data = ByteArray(10) { it.toByte() }

        assertFailsWith<IllegalArgumentException> { Base62.encode(data, -1, 5) }
        assertFailsWith<IllegalArgumentException> { Base62.encode(data, 0, -1) }
        assertFailsWith<IllegalArgumentException> { Base62.encode(data, 5, 6) }

        val encoded = Base62.encode(data, data.size, 0)
        assertTrue(encoded.isEmpty())
    }

    @Test
    fun `invalid encoded length should throw`() {
        val bytes = ByteArray(Base62.blockSize) { it.toByte() }
        val encoded = Base62.encode(bytes)
        val invalid = encoded + encoded.first()
        assertFailsWith<IllegalArgumentException> {
            Base62.decode(invalid)
        }
    }

    @Test
    fun `invalid character should throw`() {
        val encoded = Base62.encode(byteArrayOf(1, 2, 3, 4, 5))
        assertFailsWith<IllegalArgumentException> {
            Base62.decode(
                "!" + encoded.drop(
                    1
                )
            )
        }
    }

    @Test
    fun `empty string should decode to empty array`() {
        assertEquals(0, Base62.decode("").size)
    }

    @Test
    fun `block encoding should roundtrip`() {
        val rng = Random(99L)
        val coder = BaseRadix("0123456789abcdef")

        for (len in 1..coder.blockSize) {
            val bytes = ByteArray(len).also { rng.nextBytes(it) }
            val tmp = StringBuilder()
            coder.encodeBlock(bytes, 0, len, tmp)
            val outLen = tmp.length
            val encoded = StringBuilder()
            coder.encodeBlock(bytes, 0, len, encoded, 0, outLen)
            val decoded = coder.decodeBlock(encoded, 0, encoded.length)
            assertContentEquals(bytes, decoded, "Block len=$len")
        }
    }

    @Test
    fun `decodeBlock with too small output should throw`() {
        val coder = BaseRadix("0123456789abcdef")
        assertFailsWith<IllegalArgumentException> {
            coder.decodeBlock("ff", 0, 2, ByteArray(0), 0, 0)
        }
    }

    @Test
    fun `invalid alphabet should throw`() {
        assertFailsWith<IllegalArgumentException> { BaseRadix("", 4) }
        assertFailsWith<IllegalArgumentException> { BaseRadix("x", 4) }
        assertFailsWith<IllegalArgumentException> { BaseRadix("abcdea", 4) }
    }
}
