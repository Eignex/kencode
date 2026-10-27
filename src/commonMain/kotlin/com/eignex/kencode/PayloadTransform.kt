package com.eignex.kencode

/**
 * Byte transform between the binary format and the text codec, such as a checksum,
 * encryption, or error correction. `decode(encode(data))` must equal `data`.
 */
interface PayloadTransform {
    /** Transforms [data] before text encoding. */
    fun encode(data: ByteArray): ByteArray

    /** Inverse of [encode]. */
    fun decode(data: ByteArray): ByteArray
}

/** Checksum with a fixed-size digest. */
interface Checksum {
    /** Digest size in bytes. */
    val size: Int

    /** Returns the [size]-byte digest of [data]. */
    fun digest(data: ByteArray): ByteArray
}

/** Runs `this` then [next] on encode, and the reverse on decode. */
fun PayloadTransform.then(next: PayloadTransform): PayloadTransform = object : PayloadTransform {
    override fun encode(data: ByteArray): ByteArray = next.encode(this@then.encode(data))

    override fun decode(data: ByteArray): ByteArray = this@then.decode(next.decode(data))
}

/**
 * Replaces leading zero bytes with a `0x00` sentinel and a varint count, which pays off from
 * three zeros. Payloads starting with a non-zero byte pass through unchanged. At most 65,536
 * zeros are stripped.
 */
object CompactZeros : PayloadTransform {

    // Caps decoded expansion so a short token cannot request a huge allocation.
    private const val MAX_ZERO_RUN = 1 shl 16

    override fun encode(data: ByteArray): ByteArray {
        var k = 0
        while (k < data.size && k < MAX_ZERO_RUN && data[k] == 0.toByte()) k++
        if (k == 0) return data
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
        if (data[0] != 0.toByte()) return data
        val cursor = PackedPosition(1)
        val k = readVarInt(data, cursor)
        require(k in 1..MAX_ZERO_RUN) { "Invalid CompactZeros zero count: $k" }
        val dataStart = cursor.value
        // Encode strips every leading zero up to the limit.
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

/** Appends the digest on encode; verifies and strips it on decode. */
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
