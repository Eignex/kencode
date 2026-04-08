package com.eignex.kencode

/**
 * Abstraction for bidirectional byte–text encodings (e.g., Base62, Base64).
 */
interface ByteEncoding {

    /**
     * Encode the given byte range into a text representation.
     */
    fun encode(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset
    ): String

    /**
     * Decode an encoded string back into the original bytes.
     */
    fun decode(input: CharSequence): ByteArray
}
