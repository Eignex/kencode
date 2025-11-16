package com.eignex.kencode

import java.nio.ByteBuffer

interface ByteCodec {

    fun encode(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset
    ): String

    fun decode(input: CharSequence): ByteArray

    fun encode(buffer: ByteBuffer): String {
        if (buffer.hasArray()) {
            val arr = buffer.array()
            val off = buffer.arrayOffset() + buffer.position()
            val len = buffer.remaining()
            return encode(arr, off, len)
        }

        val dup = buffer.duplicate()
        val tmp = ByteArray(dup.remaining())
        dup.get(tmp)
        return encode(tmp)
    }

    fun decodeToByteBuffer(input: CharSequence): ByteBuffer =
        ByteBuffer.wrap(decode(input))

    fun encode(input: String): String = encode(input.encodeToByteArray())
    fun decodeAsString(encoded: CharSequence): String =
        decode(encoded).decodeToString()

    fun encode(number: Int): String {
        val bytes = ByteBuffer.allocate(Int.SIZE_BYTES)
            .putInt(number)
            .array()
        return encode(bytes)
    }

    fun decodeInt(input: CharSequence): Int {
        val bytes = decode(input)
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("No bytes decoded for Int.")
        }

        val size = Int.SIZE_BYTES

        return when {
            // Exact 4 bytes: direct
            bytes.size == size -> ByteBuffer.wrap(bytes).int

            // Fewer than 4 bytes: sign-extend on the left
            bytes.size < size -> {
                val sign = if (bytes[0] < 0) 0xFF.toByte() else 0x00.toByte()
                val full = ByteArray(size)
                val pad = size - bytes.size

                // Fill padding with sign
                for (i in 0 until pad) {
                    full[i] = sign
                }
                // Copy original bytes to the right
                System.arraycopy(bytes, 0, full, pad, bytes.size)

                ByteBuffer.wrap(full).int
            }

            // More than 4 bytes: ensure extra leading bytes are consistent sign extension
            else -> {
                val extra = bytes.size - size
                val sign = bytes[0]
                for (i in 0 until extra) {
                    if (bytes[i] != sign) {
                        throw IllegalArgumentException("Decoded value does not fit in Int.")
                    }
                }
                ByteBuffer.wrap(bytes, extra, size).int
            }
        }
    }

    fun encode(number: Long): String {
        val bytes = ByteBuffer.allocate(Long.SIZE_BYTES)
            .putLong(number)
            .array()
        return encode(bytes)
    }

    fun decodeLong(input: CharSequence): Long {
        val bytes = decode(input)
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("No bytes decoded for Long.")
        }

        val size = Long.SIZE_BYTES

        return when {
            // Exact 8 bytes: direct
            bytes.size == size -> ByteBuffer.wrap(bytes).long

            // Fewer than 8 bytes: sign-extend on the left
            bytes.size < size -> {
                val sign = if (bytes[0] < 0) 0xFF.toByte() else 0x00.toByte()
                val full = ByteArray(size)
                val pad = size - bytes.size

                for (i in 0 until pad) {
                    full[i] = sign
                }
                System.arraycopy(bytes, 0, full, pad, bytes.size)

                ByteBuffer.wrap(full).long
            }

            // More than 8 bytes: ensure extra leading bytes are consistent sign extension
            else -> {
                val extra = bytes.size - size
                val sign = bytes[0]
                for (i in 0 until extra) {
                    if (bytes[i] != sign) {
                        throw IllegalArgumentException("Decoded value does not fit in Long.")
                    }
                }
                ByteBuffer.wrap(bytes, extra, size).long
            }
        }
    }
}
