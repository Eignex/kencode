package com.eignex.kencode

/**
 * Transforms a binary payload before base-encoding and after base-decoding.
 *
 * Implementations can perform integrity checks (checksums), authentication,
 * encryption, or error-correcting codes — anything that maps bytes to bytes.
 *
 * Transforms must be inverse of each other: `decode(encode(data)).contentEquals(data)`.
 */
interface PayloadTransform {
    fun encode(data: ByteArray): ByteArray
    fun decode(data: ByteArray): ByteArray
}

/**
 * Wraps this [Checksum] as a [PayloadTransform] that appends the digest on encode
 * and strips and verifies it on decode.
 */
fun Checksum.asTransform(): PayloadTransform = object : PayloadTransform {
    override fun encode(data: ByteArray): ByteArray = data + digest(data)

    override fun decode(data: ByteArray): ByteArray {
        require(data.size >= size) {
            "Input too short to contain checksum: expected at least $size bytes but got ${data.size}."
        }
        val payload = data.copyOfRange(0, data.size - size)
        val actual  = data.copyOfRange(data.size - size, data.size)
        require(actual.contentEquals(digest(payload))) { "Checksum mismatch." }
        return payload
    }
}
