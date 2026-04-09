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
 * Generic base-N encoder/decoder for binary data using arbitrary alphabets and block processing.
 */
open class BaseRadix(private val alphabet: String, val blockSize: Int = 32) :
    ByteEncoding {

    init {
        require(alphabet.length > 1) { "Alphabet must contain at least 2 characters." }
        require(alphabet.toSet().size == alphabet.length) { "Alphabet must not contain duplicate characters." }
    }

    private val alphabetSize: Int = alphabet.length
    private val base: BigInteger = BigInteger.fromLong(alphabetSize.toLong())
    private val logBase: Double = log2(alphabetSize.toDouble())

    private val bigZero = BigInteger.ZERO
    private val bigFF = BigInteger.fromLong(0xFFL)
    private val zeroChar: Char get() = alphabet[0]

    private val maxAlphabetChar: Int = alphabet.maxOf { it.code }
    private val inverseAlphabet: IntArray =
        IntArray(maxAlphabetChar + 1) { -1 }.also { lookup ->
            alphabet.forEachIndexed { index, c -> lookup[c.code] = index }
        }

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
            output[writeIndex--] =
                alphabet[remainder.intValue(exactRequired = false)]
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
            val code = input[i].code
            val index =
                if (code < inverseAlphabet.size) inverseAlphabet[code] else -1
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
