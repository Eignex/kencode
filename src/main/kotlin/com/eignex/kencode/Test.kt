package com.eignex.kencode

import kotlinx.serialization.*

@Serializable
data class Payload(
    @VarUInt
    val id: UInt,
    @VarInt
    val delta: Int,
    val flag1: Boolean,
    val flag2: Boolean,
    val flag3: Boolean,
    val payloadType: PayloadType
)

enum class PayloadType {
    TYPE1, TYPE2, TYPE3
}

fun main() {
    val bytes = BitPackedFormat.encodeToByteArray(
        Payload(
            123u, -2, true, false, true, PayloadType.TYPE1
        )
    )
    println(bytes.size)
    println(BitPackedFormat.decodeFromByteArray<Payload>(bytes))
}
