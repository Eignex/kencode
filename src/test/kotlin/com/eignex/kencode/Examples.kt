package com.eignex.kencode

import PackedFormat
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.bouncycastle.jce.provider.BouncyCastleProvider
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
            mapOf("k1" to 1285, "k2" to 9681)
        )
        val format = EncodedFormat(binaryFormat = ProtoBuf)
        val encoded = format.encodeToString(payload)
        val result = format.decodeFromString<ProtoBufRequired>(encoded)
        assertEquals(payload, result)
        println(encoded)
    }

    @Test
    fun `standalone readme`() {
        val bytes = "any byte data".encodeToByteArray()
        println(Base36.encode(bytes))
        println(Base62.encode(bytes))
        println(Base64.encode(bytes))
        println(Base85.encode(bytes))
    }

    @Serializable
    data class SensitiveData(val userData: Long)

    @Test
    fun `encryption serialization`() {

        // Initialization
        Security.addProvider(BouncyCastleProvider())
        val random = SecureRandom()

        // This key is stored permanently so we can read payloads after jvm restart
        val keyBytes = ByteArray(16)
        random.nextBytes(keyBytes)
        val key = SecretKeySpec(keyBytes, "XTEA")
        val cipher = Cipher.getInstance("XTEA/CTR/NoPadding", "BC")

        // Encrypt one payload
        // we use 8 bytes as the initialization vector
        val payload = SensitiveData(random.nextLong())
        val iv8 = ByteArray(8)
        random.nextBytes(iv8)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv8))

        // This particular encryption adds 16-bytes in an initialization vector
        // regardless of how big the payload is.
        val encrypted =
            iv8 + cipher.doFinal(PackedFormat.encodeToByteArray(payload))
        val encoded = Base62.encode(encrypted)
        println(encoded)

        // This recovers the initial payload
        val iv8received = encrypted.copyOfRange(0, 8)
        val received = encrypted.copyOfRange(8, encrypted.size)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv8received))
        val decoded = Base62.decode(encoded)
        val decrypted = cipher.doFinal(received)
        val result: SensitiveData = PackedFormat.decodeFromByteArray(decrypted)
        println(result)

        assertEquals(payload, result)
    }
}
