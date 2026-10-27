package com.eignex.kencode

/** Bidirectional byte–text codec such as [Base62] or [Base64]. */
interface ByteEncoding {

    /** Encodes [length] bytes of [input] starting at [offset]. */
    fun encode(input: ByteArray, offset: Int = 0, length: Int = input.size - offset): String

    /** Decodes [input] back into bytes. */
    fun decode(input: CharSequence): ByteArray
}

internal fun requireRange(input: ByteArray, offset: Int, length: Int) {
    require(offset >= 0 && length >= 0 && offset + length <= input.size) {
        "Invalid offset/length for input of size ${input.size}"
    }
}

/** Validates a byte-range alphabet and returns its decode table: char code to digit, or -1. */
internal fun byteAlphabetTable(alphabet: CharArray, name: String, size: Int): IntArray {
    require(alphabet.size == size) { "$name requires an alphabet of length $size" }
    require(alphabet.all { it.code < 256 }) { "$name alphabet characters must be below U+0100" }
    require(alphabet.toSet().size == alphabet.size) { "$name alphabet must not contain duplicate characters" }
    return IntArray(256) { -1 }.apply {
        alphabet.forEachIndexed { i, c -> this[c.code] = i }
    }
}
