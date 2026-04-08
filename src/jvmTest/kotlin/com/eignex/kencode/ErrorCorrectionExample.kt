package com.eignex.kencode

import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorCorrectionExample {

    @Serializable
    data class RobustPayload(val id: Int, val label: String)

    @Test
    fun `reed-solomon ecc serialization`() {
        val parityBytes = 8  // corrects up to 4 corrupted bytes
        val field = GenericGF.QR_CODE_FIELD_256
        val encoder = ReedSolomonEncoder(field)
        val decoder = ReedSolomonDecoder(field)

        val eccTransform = object : PayloadTransform {
            override fun encode(data: ByteArray): ByteArray {
                val message = IntArray(data.size + parityBytes) { i ->
                    if (i < data.size) data[i].toInt() and 0xFF else 0
                }
                encoder.encode(message, parityBytes)
                return ByteArray(message.size) { message[it].toByte() }
            }

            override fun decode(data: ByteArray): ByteArray {
                val message = IntArray(data.size) { data[it].toInt() and 0xFF }
                decoder.decode(message, parityBytes)
                return ByteArray(data.size - parityBytes) { message[it].toByte() }
            }
        }

        val robustFormat = EncodedFormat { transform = eccTransform }

        val payload = RobustPayload(42, "hello")
        val token = robustFormat.encodeToString(RobustPayload.serializer(), payload)
        println(token)

        // Simulate up to 4 corrupted bytes in the raw binary
        val bytes = Base62.decode(token)
        bytes[0] = (bytes[0].toInt() xor 0xFF).toByte()
        bytes[2] = (bytes[2].toInt() xor 0xAB).toByte()
        bytes[4] = (bytes[4].toInt() xor 0x55).toByte()
        bytes[6] = (bytes[6].toInt() xor 0x12).toByte()
        val corrupted = Base62.encode(bytes)

        val recovered = robustFormat.decodeFromString(RobustPayload.serializer(), corrupted)
        assertEquals(payload, recovered)
    }
}
