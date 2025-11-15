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
}

fun ByteCodec.encode(input: String): String = encode(input.encodeToByteArray())
fun ByteCodec.decodeToString(encoded: CharSequence): String =
    decode(encoded).decodeToString()
