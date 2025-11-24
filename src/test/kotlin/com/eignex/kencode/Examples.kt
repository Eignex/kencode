package com.eignex.kencode

import PackedFormat
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
class Examples {

    // Exmaple from readme

    @Serializable
    data class Payload constructor(

        // only uses as many bytes as needed
        @VarUInt
        val id: ULong,

        @VarInt // does zig-zag to encode small negatives efficiently
        val delta: Int,

        // these are packed into a bitset along with nullability flags
        val urgent: Boolean,
        val sensitive: Boolean,
        val external: Boolean,
        val handledAt: Instant?,

        // encoded as VarUInt
        val type: PayloadType
    )

    enum class PayloadType {
        TYPE1, TYPE2, TYPE3
    }

    @Test
    fun `example serialization`() {
        val payload = Payload(
            id = 123u,
            delta = -2,
            urgent = true,
            sensitive = false,
            external = true,
            handledAt = null,
            type = PayloadType.TYPE1
        )
        println(EncodedFormat.encodeToString(payload))
        // 0fiXYI

        val result = EncodedFormat.decodeFromString<Payload>("0fiXYI")
        assertEquals(result, payload)
    }

    @Serializable
    data class ProtoBufRequired(val map: Map<String, Int>)

    @Test
    fun `protobuf serialization`() {
        // This setup can handle arbitrary payloads (limited by ProtoBuf)
        val payload = ProtoBufRequired(
            mapOf("k1" to 1285901292, "k2" to 968911021)
        )
        val format = EncodedFormat(binaryFormat = ProtoBuf)
        val encoded = format.encodeToString(payload)
        val result = format.decodeFromString<ProtoBufRequired>(encoded)
        assertEquals(payload, result)
    }

    @Serializable
    data class SensitiveData(val userData: Long)

    @Test
    fun `encryption serialization`() {
        val random = SecureRandom()

        // This can permanent or generated at startup if you only share with one server
        val privateKey = ByteArray(16)
        random.nextBytes(privateKey)
        val cipher = StreamCipher(privateKey)

        // Payload is just 8-byte
        val payload = SensitiveData(random.nextLong())

        // This particular encryption adds 16-bytes in an initialization vector
        // regardless of how big the payload is.
        val encrypted =
            cipher.encrypt(PackedFormat.encodeToByteArray(payload), random)
        val encoded = Base62.encode(encrypted)
        println(encoded)

        // This recovers the initial payload
        val decoded = Base62.decode(encoded)
        val decrypted = cipher.decrypt(decoded)
        val result: SensitiveData = PackedFormat.decodeFromByteArray(decrypted)
        println(result)
        assertEquals(payload, result)
    }
}


class StreamCipher(
    privateKey: ByteArray
) {
    private val key = SecretKeySpec(privateKey, "XTEA")

    private val cipher: Cipher = Cipher.getInstance("XTEA/CTR/NoPadding", "BC")

    companion object {
        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun encrypt(
        data: ByteArray,
        random: SecureRandom
    ): ByteArray {
        val iv8 = ByteArray(8)
        random.nextBytes(iv8)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv8))
        val encryptedData = cipher.doFinal(data)
        return ByteBuffer
            .allocate(iv8.size + encryptedData.size)
            .put(iv8)
            .put(encryptedData)
            .array()
    }

    fun decrypt(data: ByteArray): ByteArray {
        val iv8 = data.copyOfRange(0, 8)
        val ctReceived = data.copyOfRange(8, data.size)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv8))
        return cipher.doFinal(ctReceived)
    }
}
