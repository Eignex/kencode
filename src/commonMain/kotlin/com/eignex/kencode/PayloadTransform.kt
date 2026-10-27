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
    /** Transforms [data] on the way out, before base-encoding. */
    fun encode(data: ByteArray): ByteArray

    /** Inverse of [encode]: restores the original payload after base-decoding. */
    fun decode(data: ByteArray): ByteArray
}

/**
 * Generic checksum interface that produces a fixed number of bytes.
 */
interface Checksum {
    /** Number of bytes produced by [digest]. */
    val size: Int

    /** Computes the checksum of [data] and returns it as a [size]-byte array. */
    fun digest(data: ByteArray): ByteArray
}

/**
 * Chains two transforms into a pipeline.
 *
 * On encode: `this` is applied first, then [next].
 * On decode: [next] is reversed first, then `this`.
 *
 * Example: `CompactZeros.then(Crc16.asTransform())` compacts bytes, then appends a checksum.
 */
fun PayloadTransform.then(next: PayloadTransform): PayloadTransform = object : PayloadTransform {
    override fun encode(data: ByteArray): ByteArray = next.encode(this@then.encode(data))

    override fun decode(data: ByteArray): ByteArray = this@then.decode(next.decode(data))
}

/**
 * Strips leading zero bytes before encoding and restores them on decode.
 *
 * When the payload starts with a non-zero byte (k=0), the data is returned unchanged.
 * Otherwise a sentinel `0x00` byte followed by a varint count is prepended to the
 * stripped payload. Net overhead: 0 bytes for k=0, +1 byte for k=1, 0 bytes for k=2, and
 * −(k−2) bytes for 3≤k≤127; longer runs need a wider count, saving a byte or two less.
 * At most 65,536 zeros are stripped; any further zeros stay in the payload.
 */
object CompactZeros : PayloadTransform {

    // Bounds how much a decoded payload can expand, so a short malicious token cannot
    // request a huge allocation. Longer zero runs keep the rest in the stripped payload.
    private const val MAX_ZERO_RUN = 1 shl 16

    override fun encode(data: ByteArray): ByteArray {
        var k = 0
        while (k < data.size && k < MAX_ZERO_RUN && data[k] == 0.toByte()) k++
        if (k == 0) return data
        // Sentinel byte 0x00 signals that a compact prefix follows.
        // Safe because the stripped data starts with a non-zero byte unless k hit MAX_ZERO_RUN.
        val count = varintEncode(k)
        val result = ByteArray(1 + count.size + data.size - k)
        result[0] = 0x00
        count.copyInto(result, destinationOffset = 1)
        data.copyInto(
            result,
            destinationOffset = 1 + count.size,
            startIndex = k,
        )
        return result
    }

    override fun decode(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        if (data[0] != 0.toByte()) return data // k=0, no prefix was written
        val cursor = PackedPosition(1)
        val k = readVarInt(data, cursor)
        require(k in 1..MAX_ZERO_RUN) { "Invalid CompactZeros zero count: $k" }
        val dataStart = cursor.value
        // Encode strips every leading zero up to the limit, so a shorter run must end the zeros.
        require(k == MAX_ZERO_RUN || dataStart == data.size || data[dataStart] != 0.toByte()) {
            "Non-canonical CompactZeros prefix: payload continues with a zero byte"
        }
        val result = ByteArray(k + data.size - dataStart)
        data.copyInto(result, destinationOffset = k, startIndex = dataStart)
        return result
    }

    private fun varintEncode(value: Int): ByteArray {
        val out = ByteOutput(5)
        writeVarInt(value, out)
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
