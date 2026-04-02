package com.eignex.kencode

/**
 * Generic checksum interface that produces a fixed number of bytes.
 */
interface Checksum {
    val size: Int
    fun digest(data: ByteArray): ByteArray
}
