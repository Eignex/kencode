package com.eignex.kencode

/**
 * Generic checksum interface that produces a fixed number of bytes.
 *
 * @property size number of checksum bytes produced.
 *
 * Implementations must define how the checksum is computed over a byte range.
 */
interface Checksum {

    /** Number of bytes produced by this checksum. */
    val size: Int

    /**
     * Compute checksum over data[offset until offset+length].
     */
    fun compute(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset
    ): ByteArray
}
