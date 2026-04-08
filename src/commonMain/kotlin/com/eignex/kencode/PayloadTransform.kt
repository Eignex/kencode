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
 * Chains two transforms into a pipeline.
 *
 * On encode: `this` is applied first, then [next].
 * On decode: [next] is reversed first, then `this`.
 *
 * Example: `CompactZeros.then(Crc16.asTransform())` compacts bytes, then appends a checksum.
 */
fun PayloadTransform.then(next: PayloadTransform): PayloadTransform =
    object : PayloadTransform {
        override fun encode(data: ByteArray): ByteArray =
            next.encode(this@then.encode(data))

        override fun decode(data: ByteArray): ByteArray =
            this@then.decode(next.decode(data))
    }

/**
 * Strips leading zero bytes before encoding and restores them on decode.
 *
 * When the payload starts with a non-zero byte (k=0), the data is returned unchanged.
 * Otherwise a sentinel `0x00` byte followed by a varint count is prepended to the
 * stripped payload. Net overhead: 0 bytes for k=0, +1 byte for k=2, −(k−2) bytes for k≥3.
 */
object CompactZeros : PayloadTransform {

    override fun encode(data: ByteArray): ByteArray {
        var k = 0
        while (k < data.size && data[k] == 0.toByte()) k++
        if (k == 0) return data
        // Sentinel byte 0x00 signals that a compact prefix follows.
        // Safe because the stripped data always starts with a non-zero byte.
        val count = varintEncode(k)
        val result = ByteArray(1 + count.size + data.size - k)
        result[0] = 0x00
        count.copyInto(result, destinationOffset = 1)
        data.copyInto(
            result,
            destinationOffset = 1 + count.size,
            startIndex = k
        )
        return result
    }

    override fun decode(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "Compact payload cannot be empty." }
        if (data[0] != 0.toByte()) return data // k=0, no prefix was written
        val (k, prefixLen) = PackedUtils.decodeVarInt(data, 1)
        val dataStart = 1 + prefixLen
        val result = ByteArray(k + data.size - dataStart)
        data.copyInto(result, destinationOffset = k, startIndex = dataStart)
        return result
    }

    private fun varintEncode(value: Int): ByteArray {
        val out = ByteOutput(5)
        PackedUtils.writeVarInt(value, out)
        return out.toByteArray()
    }
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
        val actual = data.copyOfRange(data.size - size, data.size)
        require(actual.contentEquals(digest(payload))) { "Checksum mismatch." }
        return payload
    }
}
