package com.eignex.kencode

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
