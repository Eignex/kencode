package com.eignex.kencode

import java.math.BigInteger
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
 * Generic base-N encoder/decoder for binary data using arbitrary alphabets.
 *
 * Concept:
 * - Processes data in fixed-size blocks (default 32 bytes).
 * - Treats each block as a big-endian integer.
 * - Converts that integer into base-N using the provided alphabet.
 *
 * Guarantees:
 * - Deterministic block-to-length mapping for unambiguous decoding.
 * - Strictly increasing encoded length per input block size.
 * - Supports any alphabet of length ≥ 2 with no repeated characters.
 *
 * Usage:
 * - Specializations include Base36 and Base62.
 * - Suitable for compact identifiers or opaque byte-to-text encoding.
 *
 * @property alphabet the symbol set used for encoding.
 * @property blockSize maximum bytes processed per block.
 */
open class BaseRadix(
    private val alphabet: String, val blockSize: Int = 32
) : ByteEncoding {

    init {
        require(alphabet.length > 1) { "Alphabet must contain at least 2 characters." }
        require(alphabet.toSet().size == alphabet.length) { "Alphabet must not contain duplicate characters." }
    }

    private val alphabetSize: Int = alphabet.length
    private val base: BigInteger = BigInteger.valueOf(alphabetSize.toLong())
    private val logBase: Double = log2(alphabetSize.toDouble())

    private val bigZero = BigInteger.ZERO
    private val bigFF = BigInteger.valueOf(0xFFL)

    // ------------------------------------------------------------
    // Alphabet lookup tables
    // ------------------------------------------------------------

    /**
     * Maps a UTF-16 code unit (Char.code) to its index in [alphabet], or -1 if not present.
     */
    private val inverseAlphabet: IntArray =
        IntArray(65536) { -1 }.also { lookup ->
            alphabet.forEachIndexed { index, c ->
                lookup[c.code] = index
            }
        }

    private fun charFromIndex(index: Int): Char = alphabet[index]

    private fun indexOfChar(c: Char): Int {
        val code = c.code
        return if (code < inverseAlphabet.size) inverseAlphabet[code] else -1
    }

    private val zeroChar: Char
        get() = charFromIndex(0)

    /**
     * Encoded lengths for input blocks of size 1..[blockSize].
     *
     * `lengths[i]` = encoded length for `i + 1` input bytes.
     */
    private val lengths: IntArray = IntArray(blockSize) { blockIndex ->
        val bytesCount = blockIndex + 1
        ceil((bytesCount * 8) / logBase).toInt()
    }.also { arr ->
        // Ensure strictly increasing encoded length for each additional input
        // byte, // to avoid ambiguity when decoding.
        for (i in 1 until arr.size) {
            val prev = arr[i - 1]
            while (arr[i] <= prev) {
                arr[i]++
            }
        }
    }

    /**
     * Inverse mapping from encoded length to input block length.
     *
     * `invLengths[y - 1]` = number of decoded bytes for an encoded length `y`.
     */
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

    private val maxEncodedBlockLength: Int
        get() = lengths.last()

    private val maxDecodedBlockLength: Int
        get() = invLengths.last()

    private fun encodedLengthForBytes(byteCount: Int): Int =
        lengths[byteCount - 1]

    private fun decodedBytesForLength(encodedLength: Int): Int =
        invLengths[encodedLength - 1]

    /**
     * Encode the given [input] byte array range into a base-[alphabetSize] string.
     */
    override fun encode(
        input: ByteArray, offset: Int, length: Int
    ): String {
        require(offset >= 0 && length >= 0 && offset + length <= input.size) {
            "Invalid offset/length: offset=$offset, length=$length, size=${input.size}"
        }

        val output = StringBuilder(length * 2)
        var inPos = offset
        var remaining = length
        var outPos = 0

        while (remaining > 0) {
            val inLen = min(blockSize, remaining)
            val outLen = encodedLengthForBytes(inLen)
            encodeBlock(input, inPos, inLen, output, outPos, outLen)
            inPos += inLen
            remaining -= inLen
            outPos += outLen
        }

        return output.toString()
    }

    /**
     * Convenience overload encoding the entire [input] array.
     */
    fun encode(input: ByteArray): String = encode(input, 0, input.size)

    /**
     * Decode the given [input] base-[alphabetSize] string back into a byte array.
     *
     * @throws IllegalArgumentException if an invalid character or block is encountered.
     */
    override fun decode(input: CharSequence): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        // Validate and segment length: full blocks + optional final partial block.
        val fullBlockLen = maxEncodedBlockLength
        val fullBlocks = input.length / fullBlockLen
        val lastBlockLen = input.length % fullBlockLen

        if (lastBlockLen != 0 && lastBlockLen !in lengths) {
            throw IllegalArgumentException("Invalid encoded length: ${input.length}")
        }

        val capacityRatio =
            ceil(maxDecodedBlockLength / maxEncodedBlockLength.toDouble()).toInt()
        val output = ByteArray(input.length * capacityRatio)

        var inPos = 0
        var outPos = 0

        repeat(fullBlocks) {
            val inLen = fullBlockLen
            val outLen = decodedBytesForLength(inLen)
            decodeBlock(input, inPos, inLen, output, outPos, outLen)
            inPos += inLen
            outPos += outLen
        }

        if (lastBlockLen != 0) {
            val inLen = lastBlockLen
            val outLen = decodedBytesForLength(inLen)
            decodeBlock(input, inPos, inLen, output, outPos, outLen)
            outPos += outLen
        }

        return output.copyOf(outPos)
    }

    // ------------------------------------------------------------
    // Block-level encode/decode
    // ------------------------------------------------------------

    /**
     * Encode a single block of [inLen] bytes from [input] starting at [inPos] into [output].
     *
     * The encoded block is written starting at [outPos] and occupies exactly [outLen] characters.
     */
    fun encodeBlock(
        input: ByteArray,
        inPos: Int = 0,
        inLen: Int = input.size,
        output: StringBuilder = StringBuilder(maxEncodedBlockLength),
        outPos: Int = 0,
        outLen: Int = encodedLengthForBytes(inLen)
    ): StringBuilder {
        var n = BigInteger(1, input, inPos, inLen)

        // Pre-fill with zero-character for consistent fixed-length output
        repeat(outLen) {
            output.append(zeroChar)
        }

        var writeIndex = outPos + outLen - 1
        while (n > bigZero && writeIndex >= outPos) {
            val remainder = n.mod(base)
            output.setCharAt(writeIndex, charFromIndex(remainder.toInt()))
            writeIndex--
            n = n.divide(base)
        }

        return output
    }

    /**
     * Decode a single encoded block of [inLen] characters from [input] starting at [inPos] into [output].
     *
     * The decoded bytes are written starting at [outPos] and occupy exactly [outLen] bytes.
     *
     * @throws IllegalArgumentException if invalid characters or inconsistent blocks are found.
     */
    fun decodeBlock(
        input: CharSequence,
        inPos: Int = 0,
        inLen: Int = input.length,
        output: ByteArray = ByteArray(decodedBytesForLength(inLen)),
        outPos: Int = 0,
        outLen: Int = decodedBytesForLength(inLen)
    ): ByteArray {
        var n = bigZero

        // Convert encoded characters to integer in base-[alphabetSize]
        for (i in inPos until (inPos + inLen)) {
            val c = input[i]
            val index = indexOfChar(c)
            if (index < 0) {
                val block = input.substring(inPos, inPos + inLen)
                throw IllegalArgumentException("Not an encoding char: '$c' in block '$block'.")
            }
            n = n.multiply(base).add(BigInteger.valueOf(index.toLong()))
        }

        // Extract bytes (big-endian)
        for (i in outLen - 1 downTo 0) {
            output[outPos + i] = n.and(bigFF).toByte()
            n = n.shiftRight(8)
        }

        // If we still have a non-zero number, the block did not fit in outLen bytes
        if (n != bigZero) {
            val block = input.substring(inPos, inPos + inLen)
            throw IllegalArgumentException("Invalid encoding block: '$block'.")
        }

        return output
    }
}
