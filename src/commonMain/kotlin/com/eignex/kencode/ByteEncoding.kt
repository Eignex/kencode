package com.eignex.kencode

/**
 * Abstraction for bidirectional byte–text encodings (e.g., Base62, Base64).
 */
interface ByteEncoding {

    /**
     * Encode the given byte range into a text representation.
     */
    fun encode(input: ByteArray, offset: Int = 0, length: Int = input.size - offset): String

    /**
     * Decode an encoded string back into the original bytes.
     */
    fun decode(input: CharSequence): ByteArray
}

internal fun requireRange(input: ByteArray, offset: Int, length: Int) {
    require(offset >= 0 && length >= 0 && offset + length <= input.size) {
        "Invalid offset/length for input of size ${input.size}"
    }
}

/**
 * Validates a fixed-size, byte-range alphabet for codec [name] and returns its decode table,
 * mapping each char code below 256 to its digit, or -1 when absent.
 */
internal fun byteAlphabetTable(alphabet: CharArray, name: String, size: Int): IntArray {
    require(alphabet.size == size) { "$name requires an alphabet of length $size" }
    require(alphabet.all { it.code < 256 }) { "$name alphabet characters must be below U+0100" }
    require(alphabet.toSet().size == alphabet.size) { "$name alphabet must not contain duplicate characters" }
    return IntArray(256) { -1 }.apply {
        alphabet.forEachIndexed { i, c -> this[c.code] = i }
    }
}
