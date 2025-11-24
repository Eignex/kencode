package com.eignex.kencode

/**
 * Abstraction for byte→string and string→byte encoders.
 */
interface ByteEncoding {

    fun encode(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset
    ): String

    fun decode(input: CharSequence): ByteArray
}
