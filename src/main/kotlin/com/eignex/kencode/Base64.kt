package com.eignex.kencode

const val BASE_64 =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

const val BASE_64_URL_SAFE =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

object Base64UrlSafe : Base64(BASE_64_URL_SAFE.toCharArray())

/**
 * RFC 4648–compatible Base64 encoder/decoder.
 *
 * Features:
 * - Uses a configurable 64-character alphabet.
 * - Produces '=' padding to align output to 4-character blocks.
 * - Handles input in 3-byte groups → 4 Base64 characters.
 *
 * Decoding:
 * - Accepts only input lengths divisible by 4.
 * - Validates and strips up to two padding characters.
 * - Throws on encountering non-alphabet characters.
 *
 * @property alphabet the 64-symbol Base64 alphabet.
 */
open class Base64(val alphabet: CharArray) : ByteEncoding {

    companion object Default : Base64(BASE_64.toCharArray())

    init {
        require(alphabet.size == 64) {"Base64 requires a 64 length alphabet" }
    }

    private val decodeTable: IntArray = IntArray(256) { -1 }.apply {
        for (i in alphabet.indices) {
            this[alphabet[i].code] = i
        }
        this['='.code] = 0
    }

    override fun encode(
        input: ByteArray,
        offset: Int,
        length: Int
    ): String {
        require(offset >= 0 && length >= 0 && offset + length <= input.size) {
            "Invalid offset/length for input of size ${input.size}"
        }

        if (length == 0) return ""

        val end = offset + length
        val outLen = ((length + 2) / 3) * 4
        val out = CharArray(outLen)

        var inPos = offset
        var outPos = 0

        while (inPos < end) {
            val remaining = end - inPos

            val b0 = input[inPos++].toInt() and 0xFF
            val b1 = if (remaining > 1) input[inPos++].toInt() and 0xFF else 0
            val b2 = if (remaining > 2) input[inPos++].toInt() and 0xFF else 0

            val i0 = b0 ushr 2
            val i1 = ((b0 and 0x03) shl 4) or (b1 ushr 4)
            val i2 = ((b1 and 0x0F) shl 2) or (b2 ushr 6)
            val i3 = b2 and 0x3F

            out[outPos++] = alphabet[i0]
            out[outPos++] = alphabet[i1]
            out[outPos++] = if (remaining > 1) alphabet[i2] else '='
            out[outPos++] = if (remaining > 2) alphabet[i3] else '='
        }

        return String(out)
    }


    override fun decode(input: CharSequence): ByteArray {
        val len = input.length
        require(len % 4 == 0) { "Base64 input length must be a multiple of 4" }
        if (len == 0) return ByteArray(0)

        // Count padding
        var pad = 0
        if (input[len - 1] == '=') pad++
        if (input[len - 2] == '=') pad++

        val outLen = (len / 4) * 3 - pad
        val out = ByteArray(outLen)

        var outPos = 0

        for (i in 0 until len step 4) {
            val c0 = decodeChar(input[i])
            val c1 = decodeChar(input[i + 1])
            val c2 = decodeChar(input[i + 2])
            val c3 = decodeChar(input[i + 3])

            val b0 = (c0 shl 2) or (c1 ushr 4)
            val b1 = ((c1 and 0x0F) shl 4) or (c2 ushr 2)
            val b2 = ((c2 and 0x03) shl 6) or c3

            out[outPos++] = b0.toByte()
            if (outPos < outLen) out[outPos++] = b1.toByte()
            if (outPos < outLen) out[outPos++] = b2.toByte()
        }

        return out
    }

    private fun decodeChar(c: Char): Int {
        val value = decodeTable.getOrElse(c.code) { -1 }
        require(value >= 0) { "Invalid Base64 character: '$c'" }
        return value
    }
}
