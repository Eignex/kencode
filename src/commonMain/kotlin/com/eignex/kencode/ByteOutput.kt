package com.eignex.kencode

internal class ByteOutput(initialCapacity: Int = 64) {
    private var buf = ByteArray(initialCapacity)
    private var size = 0

    fun write(b: Int) {
        ensureCapacity(size + 1)
        buf[size++] = b.toByte()
    }

    fun write(bytes: ByteArray) {
        ensureCapacity(size + bytes.size)
        bytes.copyInto(buf, size)
        size += bytes.size
    }

    fun writeTo(other: ByteOutput) = other.write(toByteArray())

    fun toByteArray(): ByteArray = buf.copyOf(size)

    fun reset() {
        size = 0
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= buf.size) return
        var newSize = buf.size * 2
        while (newSize < needed) newSize *= 2
        buf = buf.copyOf(newSize)
    }
}
