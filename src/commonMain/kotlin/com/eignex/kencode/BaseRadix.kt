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
        val output = StringBuilder(length * 2)
        val workBuffer = ByteArray(blockSize)
        var inPos = offset
        var remaining = length

        while (remaining > 0) {
            val inLen = min(blockSize, remaining)
            val outLen = lengths[inLen - 1]
            if (inLen < blockSize) workBuffer.fill(0, 0, blockSize - inLen)
            input.copyInto(workBuffer, blockSize - inLen, inPos, inPos + inLen)
            encodeBlock(workBuffer, output = output, outLen = outLen)
            inPos += inLen
            remaining -= inLen
        }
        return output.toString()
    }

    override fun decode(input: CharSequence): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val fullBlockLen = lengths.last()
        val fullBlocks = input.length / fullBlockLen
        val lastBlockLen = input.length % fullBlockLen

        require(!(lastBlockLen != 0 && lastBlockLen !in lengths)) { "Invalid encoded length: ${input.length}" }

        val lastOutLen =
            if (lastBlockLen > 0) invLengths[lastBlockLen - 1] else 0
        val output = ByteArray(fullBlocks * invLengths.last() + lastOutLen)
        var inPos = 0
        var outPos = 0

        repeat(fullBlocks) {
            decodeBlock(
                input,
                inPos,
                fullBlockLen,
                output,
                outPos,
                invLengths.last()
            )
            inPos += fullBlockLen
            outPos += invLengths.last()
        }

        if (lastBlockLen != 0) {
            decodeBlock(input, inPos, lastBlockLen, output, outPos, lastOutLen)
        }

        return output
    }

    internal fun encodeBlock(
        input: ByteArray,
        inPos: Int = 0,
        inLen: Int = input.size,
        output: StringBuilder = StringBuilder(lengths.last()),
        outLen: Int = lengths[inLen - 1]
    ): StringBuilder {
        var n = BigInteger.fromByteArray(
            if (inPos == 0 && inLen == input.size) {
                input
            } else {
                input.sliceArray(inPos until inPos + inLen)
            },
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
        return output
    }

    internal fun decodeBlock(
        input: CharSequence,
        inPos: Int = 0,
        inLen: Int = input.length,
        output: ByteArray = ByteArray(invLengths[inLen - 1]),
        outPos: Int = 0,
        outLen: Int = invLengths[inLen - 1]
    ): ByteArray {
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
        return output
    }
}
