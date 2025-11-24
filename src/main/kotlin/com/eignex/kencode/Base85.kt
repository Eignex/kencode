package com.eignex.kencode

val ASCII85 =
    "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstu"

/**
 * Generic Base85 encoder/decoder supporting both ASCII85 and ZeroMQ Z85.
 *
 *  - 4 input bytes → 5 output chars.
 *  - Allows final partial group (1–3 bytes → 2–4 chars).
 *  - No 'z' compression, no <~ ~> delimiters.
 *
 * Detection:
 * - Alphabet identity determines whether to use ASCII85 or Z85 rules.
 *
 * @property alphabet the 85-character encoding alphabet.
 */
open class Base85(
    private val alphabet: CharArray
) : ByteEncoding {

    companion object Default : Base85(ASCII85.toCharArray())

    init {
        require(alphabet.size == 85) { "Base85 requires an alphabet of length 85" }
    }

    private val decodeTable: IntArray = IntArray(256) { -1 }.apply {
        for (i in alphabet.indices) {
            this[alphabet[i].code] = i
        }
    }

    override fun encode(
        input: ByteArray, offset: Int, length: Int
    ): String {
        require(offset >= 0 && length >= 0 && offset + length <= input.size) {
            "Invalid offset/length for input of size ${input.size}"
        }
        if (length == 0) return ""

        val end = offset + length
        val out = StringBuilder(((length + 3) / 4) * 5)

        var pos = offset

        while (pos < end) {
            val remaining = end - pos
            val chunkLen = if (remaining >= 4) 4 else remaining

            var value = 0L
            for (i in 0 until 4) {
                val b =
                    if (i < chunkLen) (input[pos + i].toInt() and 0xFF) else 0
                value = (value shl 8) or (b.toLong() and 0xFFL)
            }
            pos += chunkLen

            val tmp = CharArray(5)
            for (i in 4 downTo 0) {
                val digit = (value % 85L).toInt()
                tmp[i] = alphabet[digit]
                value /= 85L
            }

            // Full chunk: 5 chars; partial chunk (1–3 bytes): (chunkLen + 1) chars
            val outLen = if (chunkLen == 4) 5 else chunkLen + 1
            out.append(tmp, 0, outLen)
        }

        return out.toString()
    }

    override fun decode(input: CharSequence): ByteArray {
        val len = input.length
        if (len == 0) return ByteArray(0)

        val fullGroups = len / 5
        val rem = len % 5

        // Remainder of 1 is invalid in ASCII85 (n bytes → n+1 chars, so remainder is 0 or 2–4).
        if (rem == 1) {
            throw IllegalArgumentException("Invalid ASCII85 length: remainder of 1 is not allowed")
        }

        val extraBytes = if (rem == 0) 0 else (rem - 1)
        val out = ByteArray(fullGroups * 4 + extraBytes)

        var inPos = 0
        var outPos = 0

        // Decode full 5-char groups → 4 bytes each.
        repeat(fullGroups) {
            var value = 0L
            repeat(5) {
                val c = input[inPos++]
                val digit = decodeChar(c)
                value = value * 85L + digit
            }

            for (i in 3 downTo 0) {
                out[outPos + i] = (value and 0xFFL).toByte()
                value = value shr 8
            }
            outPos += 4
        }

        // Decode final partial group (2–4 chars) if present.
        if (rem != 0) {
            val padChar =
                alphabet[alphabet.lastIndex] // highest value (84), usually 'u' in ASCII85
            var value = 0L

            // Treat missing chars as highest-value pad char.
            for (i in 0 until 5) {
                val c = if (i < rem) input[inPos + i] else padChar
                val digit = decodeChar(c)
                value = value * 85L + digit
            }

            // Convert to 4 bytes and keep only (rem - 1) leading bytes.
            val tmp = ByteArray(4)
            for (i in 3 downTo 0) {
                tmp[i] = (value and 0xFFL).toByte()
                value = value shr 8
            }

            val needed = rem - 1
            System.arraycopy(tmp, 0, out, outPos, needed)
        }

        return out
    }

    private fun decodeChar(c: Char): Int {
        val code = c.code
        val value = if (code < decodeTable.size) decodeTable[code] else -1
        require(value >= 0) { "Invalid Base85 character: '$c'" }
        return value
    }
}
