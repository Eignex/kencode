package com.eignex.kencode

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import kotlin.math.*

const val BASE_62: String =
    "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

/**
 * Base62 encoder/decoder using digits, lowercase, then uppercase characters.
 */
object Base62 : BaseRadix(BASE_62)

/**
 * Base36 encoder/decoder using digits and lowercase characters.
 */
object Base36 : BaseRadix(BASE_62.take(36))

/**
 * Maps indices to characters (encoding) and characters back to indices (decoding).
 * [indexOf] returns -1 for characters not in the alphabet.
 */
interface Alphabet {
    val size: Int
    operator fun get(index: Int): Char
    fun indexOf(c: Char): Int
}

/**
 * Alphabet backed by an explicit string of characters.
 */
class CharAlphabet(private val chars: String) : Alphabet {
    init {
        require(chars.length > 1) { "Alphabet must contain at least 2 characters." }
        require(chars.toSet().size == chars.length) { "Alphabet must not contain duplicate characters." }
    }

    override val size: Int = chars.length
    private val maxCode: Int = chars.maxOf { it.code }
    private val inverse: IntArray = IntArray(maxCode + 1) { -1 }.also { lookup ->
        chars.forEachIndexed { index, c -> lookup[c.code] = index }
    }

    override fun get(index: Int): Char = chars[index]
    override fun indexOf(c: Char): Int = if (c.code <= maxCode) inverse[c.code] else -1
}

/**
 * Alphabet backed by a contiguous Unicode range starting at [start].
 * Defaults to U+0020 – U+D7FF (55,264 characters), the largest BMP range
 * that avoids surrogate code points.
 */
class UnicodeRangeAlphabet(
    private val start: Int = 0x0020,
    override val size: Int = 0xD800 - 0x0020
) : Alphabet {
    init {
        require(size > 1) { "Alphabet must contain at least 2 characters." }
        require(start >= 0) { "Unicode range start must be non-negative." }
        require(start + size <= 0xD800 || start >= 0xE000) {
            "Unicode range must not overlap surrogate block (U+D800–U+DFFF)."
        }
        require(start + size <= 0x110000) { "Unicode range out of bounds." }
    }

    override fun get(index: Int): Char = (start + index).toChar()
    override fun indexOf(c: Char): Int {
        val offset = c.code - start
        return if (offset in 0 until size) offset else -1
    }
}

/**
 * Generic base-N encoder/decoder for binary data using arbitrary alphabets and block processing.
 */
open class BaseRadix(private val alphabet: Alphabet, val blockSize: Int = 32) :
    ByteEncoding {

    constructor(chars: String, blockSize: Int = 32) : this(CharAlphabet(chars), blockSize)

    private val base: BigInteger = BigInteger.fromLong(alphabet.size.toLong())
    private val logBase: Double = log2(alphabet.size.toDouble())

    private val bigZero = BigInteger.ZERO
    private val bigFF = BigInteger.fromLong(0xFFL)
    private val zeroChar: Char get() = alphabet[0]

    private val isMassiveBase = alphabet.size > 256
    // Full block length must exceed any partial block's so the decoder can split unambiguously.
    private val massiveFullBlockLen: Int = maxOf(
        ceil((blockSize * 8) / logBase).toInt(),
        ceil(((blockSize - 1) * 8) / logBase).toInt() + 1
    )

    private val lengths: IntArray = IntArray(blockSize) { blockIndex ->
        val bytesCount = blockIndex + 1
        ceil((bytesCount * 8) / logBase).toInt()
    }.also { arr ->
        for (i in 1 until arr.size) {
            val prev = arr[i - 1]
            while (arr[i] <= prev) arr[i]++
        }
    }

    private val invLengths: IntArray = IntArray(lengths.last()).also { inv ->
        var previousEncodedLength = 0
        lengths.forEachIndexed { decodedBytes, encodedLength ->
            val decodedCount = decodedBytes + 1
            while (previousEncodedLength < encodedLength) {
                inv[previousEncodedLength] = decodedCount
                previousEncodedLength++
            }
        }
    }

    override fun encode(input: ByteArray, offset: Int, length: Int): String {
        require(offset >= 0 && length >= 0 && offset + length <= input.size)
        if (length == 0) return ""

        val output = StringBuilder(length * 2)
        var inPos = offset
        var remaining = length

        while (remaining > 0) {
            val inLen = min(blockSize, remaining)

            if (isMassiveBase) {
                encodeMassiveBlock(input, inPos, inLen, output)
            } else {
                val outLen = lengths[inLen - 1]
                val workBuffer = ByteArray(blockSize)
                if (inLen < blockSize) workBuffer.fill(0, 0, blockSize - inLen)
                input.copyInto(workBuffer, blockSize - inLen, inPos, inPos + inLen)
                encodeStandardBlock(workBuffer, output = output, outLen = outLen)
            }

            inPos += inLen
            remaining -= inLen
        }
        return output.toString()
    }

    override fun decode(input: CharSequence): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        if (isMassiveBase) return decodeMassive(input)

        val fullBlockLen = lengths.last()
        val fullBlocks = input.length / fullBlockLen
        val lastBlockLen = input.length % fullBlockLen

        require(!(lastBlockLen != 0 && lastBlockLen !in lengths)) { "Invalid encoded length: ${input.length}" }

        val lastOutLen = if (lastBlockLen > 0) invLengths[lastBlockLen - 1] else 0
        val output = ByteArray(fullBlocks * invLengths.last() + lastOutLen)

        var inPos = 0
        var outPos = 0

        repeat(fullBlocks) {
            decodeStandardBlock(input, inPos, fullBlockLen, output, outPos, invLengths.last())
            inPos += fullBlockLen
            outPos += invLengths.last()
        }

        if (lastBlockLen != 0) {
            decodeStandardBlock(input, inPos, lastBlockLen, output, outPos, lastOutLen)
        }

        return output
    }

    private fun encodeStandardBlock(
        input: ByteArray,
        inPos: Int = 0,
        inLen: Int = input.size,
        output: StringBuilder = StringBuilder(lengths.last()),
        outLen: Int = lengths[inLen - 1]
    ) {
        var n = BigInteger.fromByteArray(
            if (inPos == 0 && inLen == input.size) input else input.sliceArray(inPos until inPos + inLen),
            Sign.POSITIVE
        )
        val startPos = output.length
        repeat(outLen) { output.append(zeroChar) }
        var writeIndex = output.length - 1
        while (n > bigZero && writeIndex >= startPos) {
            val remainder = n.rem(base)
            output[writeIndex--] = alphabet[remainder.intValue(exactRequired = false)]
            n /= base
        }
    }

    private fun decodeStandardBlock(
        input: CharSequence,
        inPos: Int = 0,
        inLen: Int = input.length,
        output: ByteArray = ByteArray(invLengths[inLen - 1]),
        outPos: Int = 0,
        outLen: Int = invLengths[inLen - 1]
    ) {
        var n = bigZero
        for (i in inPos until (inPos + inLen)) {
            val index = alphabet.indexOf(input[i])
            require(index >= 0) { "Not an encoding char: '${input[i]}'" }
            n = n * base + BigInteger.fromLong(index.toLong())
        }

        for (i in outLen - 1 downTo 0) {
            output[outPos + i] = (n and bigFF).byteValue(exactRequired = false)
            n = n shr 8
        }
        require(n == bigZero) { "Invalid encoding block." }
    }

    private fun encodeMassiveBlock(input: ByteArray, inPos: Int, inLen: Int, output: StringBuilder) {
        if (inLen == blockSize) {
            var n = BigInteger.fromByteArray(input.sliceArray(inPos until inPos + inLen), Sign.POSITIVE)
            val startPos = output.length
            repeat(massiveFullBlockLen) { output.append(zeroChar) }
            var writeIndex = output.length - 1

            while (n > bigZero && writeIndex >= startPos) {
                val remainder = n.rem(base)
                output[writeIndex--] = alphabet[remainder.intValue(exactRequired = false)]
                n /= base
            }
        } else {
            var lz = 0
            while (lz < inLen && input[inPos + lz] == 0.toByte()) lz++

            repeat(lz) { output.append(zeroChar) }

            if (lz < inLen) {
                var temp = BigInteger.fromByteArray(input.sliceArray((inPos + lz) until (inPos + inLen)), Sign.POSITIVE)
                val chars = mutableListOf<Char>()
                while (temp > bigZero) {
                    val rem = temp.rem(base)
                    chars.add(alphabet[rem.intValue(exactRequired = false)])
                    temp /= base
                }
                for (i in chars.indices.reversed()) {
                    output.append(chars[i])
                }
            }
        }
    }

    private fun decodeMassive(input: CharSequence): ByteArray {
        val fullBlocks = input.length / massiveFullBlockLen
        val lastBlockLen = input.length % massiveFullBlockLen

        val output = ByteArray(fullBlocks * blockSize + blockSize)
        var outPos = 0
        var inPos = 0

        repeat(fullBlocks) {
            var n = bigZero
            for (i in inPos until (inPos + massiveFullBlockLen)) {
                val index = alphabet.indexOf(input[i])
                require(index >= 0) { "Not an encoding char: '${input[i]}'" }
                n = n * base + BigInteger.fromLong(index.toLong())
            }

            val bytes = n.toByteArray()
            val actualBytes = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.sliceArray(1 until bytes.size) else bytes

            val pad = blockSize - actualBytes.size
            require(pad >= 0) { "Decoded block exceeds expected block size." }

            for (i in 0 until pad) output[outPos++] = 0
            actualBytes.copyInto(output, outPos)
            outPos += actualBytes.size

            inPos += massiveFullBlockLen
        }

        if (lastBlockLen != 0) {
            var lz = 0
            while (lz < lastBlockLen && input[inPos + lz] == zeroChar) lz++

            repeat(lz) { output[outPos++] = 0 }

            if (lz < lastBlockLen) {
                var n = bigZero
                for (i in (inPos + lz) until (inPos + lastBlockLen)) {
                    val index = alphabet.indexOf(input[i])
                    require(index >= 0) { "Not an encoding char: '${input[i]}'" }
                    n = n * base + BigInteger.fromLong(index.toLong())
                }

                val bytes = n.toByteArray()
                val actualBytes = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.sliceArray(1 until bytes.size) else bytes

                actualBytes.copyInto(output, outPos)
                outPos += actualBytes.size
            }
        }

        return output.sliceArray(0 until outPos)
    }
}
