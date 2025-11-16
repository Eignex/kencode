package com.eignex.kencode

import kotlin.random.Random
import kotlin.test.*

class BaseCoderTest {

    @Test
    fun encodeDecode_emptyArray() {
        val bytes = ByteArray(0)
        val encoded = Base62.encode(bytes)
        val decoded = Base62.decode(encoded)
        assertTrue(encoded.isEmpty())
        assertContentEquals(bytes, decoded)
    }

    @Test
    fun encodeDecode_smallFixedPatterns() {
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
            val encoded = Base62.encode(bytes)
            val decoded = Base62.decode(encoded)
            assertContentEquals(
                bytes,
                decoded,
                "Failed for pattern: ${bytes.toList()}"
            )
        }
    }

    @Test
    fun encodeDecode_roundtripVariousLengths_base62() {
        val rnd = Random(1234L)
        for (len in 0..(Base62.chunkSize * 2)) {
            val bytes = ByteArray(len).also { rnd.nextBytes(it) }
            val encoded = Base62.encode(bytes)
            val decoded = Base62.decode(encoded)
            assertContentEquals(
                bytes,
                decoded,
                "Roundtrip failed for length=$len"
            )
        }
    }

    @Test
    fun encodeDecode_roundtripVariousLengths_base36() {
        val rnd = Random(5678L)
        for (len in 0..(Base36.chunkSize * 2)) {
            val bytes = ByteArray(len).also { rnd.nextBytes(it) }
            val encoded = Base36.encode(bytes)
            val decoded = Base36.decode(encoded)
            assertContentEquals(
                bytes,
                decoded,
                "Roundtrip failed for length=$len"
            )
        }
    }

    @Test
    fun encode_withOffsetAndLength_roundtrip() {
        val rnd = Random(42L)
        val total = 100
        val buffer = ByteArray(total).also { rnd.nextBytes(it) }

        val offset = 10
        val length = 50
        val slice = buffer.copyOfRange(offset, offset + length)

        val encoded = Base62.encode(buffer, offset, length)
        val decoded = Base62.decode(encoded)

        assertEquals(length, decoded.size)
        assertContentEquals(slice, decoded)
    }

    @Test
    fun encode_withInvalidOffsetOrLength_throws() {
        val data = ByteArray(10) { it.toByte() }

        assertFailsWith<IllegalArgumentException> {
            Base62.encode(data, offset = -1, length = 5)
        }
        assertFailsWith<IllegalArgumentException> {
            Base62.encode(data, offset = 0, length = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            Base62.encode(data, offset = 5, length = 6) // 5 + 6 > 10
        }
    }

    @Test
    fun decode_invalidEncodedLength_throws() {
        val bytes = ByteArray(10) { it.toByte() }
        val encoded = Base62.encode(bytes)

        // Remove one character from the end so last chunk length is invalid
        val truncated = encoded.dropLast(1)

        assertFailsWith<IllegalArgumentException> {
            Base62.decode(truncated)
        }
    }

    @Test
    fun decode_invalidCharacter_throws() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = Base62.encode(bytes)

        // Replace first char with a character not in the Base62 alphabet (e.g., '!')
        val corrupted = "!" + encoded.drop(1)

        assertFailsWith<IllegalArgumentException> {
            Base62.decode(corrupted)
        }
    }

    @Test
    fun chunkLevel_encodeDecode_roundtrip() {
        val rnd = Random(99L)
        val coder = BaseCoder("0123456789abcdef") // base16

        for (len in 1..coder.chunkSize) {
            val bytes = ByteArray(len).also { rnd.nextBytes(it) }

            val outLen = run {
                // reflect encodedLengthForBytes(len) using encodeChunk overload default
                val tmp = StringBuilder()
                coder.encodeChunk(bytes, 0, len, tmp)
                tmp.length
            }

            val encoded = StringBuilder()
            coder.encodeChunk(
                input = bytes,
                inPos = 0,
                inLen = len,
                output = encoded,
                outPos = 0,
                outLen = outLen
            )

            val decoded = coder.decodeChunk(
                input = encoded,
                inPos = 0,
                inLen = encoded.length
            )

            assertContentEquals(
                bytes,
                decoded,
                "Chunk roundtrip failed for len=$len"
            )
        }
    }
}
