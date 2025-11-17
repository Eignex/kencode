package com.eignex.kencode

const val BASE_32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

open class Base32(val alphabet: CharArray) : ByteCodec {

    companion object Default : Base32(BASE_32.toCharArray())

    init {
        require(alphabet.size == 32) { "Base32 requires a 32 length alphabet" }
    }

    private val decodeTable: IntArray = IntArray(256) { -1 }.apply {
        for (i in alphabet.indices) {
            val ch = alphabet[i]
            this[ch.code] = i
            // Accept lowercase as well
            this[ch.lowercaseChar().code] = i
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

        val outLen =
            ((length + 4) / 5) * 8 // RFC 4648: output is in 8-char blocks
        val out = CharArray(outLen)

        var bitBuffer = 0
        var bitCount = 0
        var outPos = 0

        val end = offset + length
        var i = offset
        while (i < end) {
            bitBuffer = (bitBuffer shl 8) or (input[i].toInt() and 0xFF)
            bitCount += 8

            while (bitCount >= 5) {
                bitCount -= 5
                val index = (bitBuffer shr bitCount) and 0x1F
                out[outPos++] = alphabet[index]
            }

            i++
        }

        // Remaining bits -> one more symbol
        if (bitCount > 0) {
            val index = (bitBuffer shl (5 - bitCount)) and 0x1F
            out[outPos++] = alphabet[index]
        }

        // Pad to multiple of 8 characters
        while (outPos % 8 != 0) {
            out[outPos++] = '='
        }

        return String(out)
    }

    override fun decode(input: CharSequence): ByteArray {
        val len = input.length
        require(len % 8 == 0) { "Base32 input length must be a multiple of 8" }
        if (len == 0) return ByteArray(0)

        // Count padding
        var pad = 0
        var i = len - 1
        while (i >= 0 && input[i] == '=') {
            pad++
            i--
        }

        // Valid padding sizes per RFC 4648: 0, 1, 3, 4, 6
        require(pad == 0 || pad == 1 || pad == 3 || pad == 4 || pad == 6) {
            "Invalid Base32 padding"
        }

        // How many bytes are "missing" from the last 5-byte group
        val truncatedBytes = when (pad) {
            0 -> 0 // 5 bytes -> 8 chars
            1 -> 1 // 4 bytes -> 7 chars + '='
            3 -> 2 // 3 bytes -> 5 chars + '==='
            4 -> 3 // 2 bytes -> 4 chars + '===='
            6 -> 4 // 1 byte  -> 2 chars + '======'
            else -> 0 // already guarded above
        }

        val outLen = (len / 8) * 5 - truncatedBytes
        val out = ByteArray(outLen)

        var outPos = 0
        var bitBuffer = 0
        var bitCount = 0

        for (idx in 0 until len) {
            val c = input[idx]
            if (c == '=') break

            val value = decodeChar(c)
            bitBuffer = (bitBuffer shl 5) or value
            bitCount += 5

            while (bitCount >= 8 && outPos < outLen) {
                bitCount -= 8
                val b = (bitBuffer shr bitCount) and 0xFF
                out[outPos++] = b.toByte()
            }
        }

        require(outPos == outLen) {
            "Base32 input has inconsistent padding or characters"
        }

        return out
    }

    private fun decodeChar(c: Char): Int {
        val code = c.code
        require(code < decodeTable.size) { "Invalid Base32 character: '$c'" }
        val value = decodeTable[code]
        require(value >= 0) { "Invalid Base32 character: '$c'" }
        return value
    }


}
