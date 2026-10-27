package com.eignex.kencode

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CodecValidationTest {
    private fun decodesOnlyCanonically(codec: ByteEncoding, candidate: String) {
        val decoded = runCatching { codec.decode(candidate) }.getOrNull() ?: return
        assertEquals(codec.encode(decoded), candidate, "non-canonical input decoded: $candidate")
    }

    @Test
    fun `Base64 rejects non-zero padding bits`() {
        assertContentEquals(byteArrayOf(0), Base64.decode("AA=="))
        assertContentEquals(byteArrayOf(0, 0), Base64.decode("AAA="))
        assertFailsWith<IllegalArgumentException> { Base64.decode("AB==") }
        assertFailsWith<IllegalArgumentException> { Base64.decode("AP==") }
        assertFailsWith<IllegalArgumentException> { Base64.decode("AAB=") }
        assertFailsWith<IllegalArgumentException> { Base64Url.decode("AAAAAB==") }
    }

    @Test
    fun `Base64 final groups have a single encoding`() {
        for (first in BASE_64) {
            for (second in BASE_64) {
                decodesOnlyCanonically(Base64, "$first$second==")
                decodesOnlyCanonically(Base64, "A$first$second=")
            }
        }
    }

    @Test
    fun `Base85 final groups have a single encoding`() {
        for (first in ASCII85) {
            for (second in ASCII85) {
                decodesOnlyCanonically(Base85, "$first$second")
                decodesOnlyCanonically(Base85, "!!$first$second")
            }
        }
        for (size in 0..12) {
            val data = ByteArray(size) { (it * 37 + 200).toByte() }
            assertContentEquals(data, Base85.decode(Base85.encode(data)))
        }
    }

    @Test
    fun `Base64 and Base85 reject invalid alphabets`() {
        assertFailsWith<IllegalArgumentException> { Base64(("€" + BASE_64.drop(1)).toCharArray()) }
        assertFailsWith<IllegalArgumentException> { Base64(BASE_64.replace('B', 'A').toCharArray()) }
        assertFailsWith<IllegalArgumentException> { Base64(BASE_64.replace('/', '=').toCharArray()) }
        assertFailsWith<IllegalArgumentException> { Base85(("€" + ASCII85.drop(1)).toCharArray()) }
        assertFailsWith<IllegalArgumentException> { Base85(ASCII85.replace('B', 'A').toCharArray()) }
    }

    @Test
    fun `BaseRadix rejects non-positive block sizes`() {
        assertFailsWith<IllegalArgumentException> { BaseRadix(BASE_62, 0) }
        assertFailsWith<IllegalArgumentException> { BaseRadix(BASE_62, -1) }
        assertFailsWith<IllegalArgumentException> { BaseRadix(UnicodeRangeAlphabet(), 0) }
    }

    @Test
    fun `ByteOutput grows from zero capacity`() {
        val out = ByteOutput(0)
        repeat(3) { out.write(it) }
        assertContentEquals(byteArrayOf(0, 1, 2), out.toByteArray())
    }

    @Test
    fun `checksum setter appends to an existing transform`() {
        val data = byteArrayOf(0, 0, 0, 0, 5)
        val format = EncodedFormat {
            transform = CompactZeros
            checksum = Crc16
        }
        val expected = CompactZeros.then(Crc16.asTransform()).encode(data)
        assertContentEquals(expected, format.configuration.transform!!.encode(data))
        assertContentEquals(data, format.configuration.transform!!.decode(expected))
    }

    @Test
    fun `checksum setter alone and null keep their behavior`() {
        val data = byteArrayOf(1, 2, 3)
        val alone = EncodedFormat { checksum = Crc16 }
        assertContentEquals(Crc16.asTransform().encode(data), alone.configuration.transform!!.encode(data))
        val cleared = EncodedFormat {
            transform = CompactZeros
            checksum = null
        }
        assertSame(CompactZeros, cleared.configuration.transform)
    }
}
