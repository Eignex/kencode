package com.eignex.kencode

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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

    // Example from readme

    @Serializable
    data class Payload(

        // only uses as many bytes as needed
        @PackedType(IntPacking.DEFAULT)
        val id: ULong,

        @PackedType(IntPacking.SIGNED) // zig-zag encodes small negatives efficiently
        val delta: Int,

        // these are packed into a bitset along with nullability flags
        val urgent: Boolean,
        val sensitive: Boolean,
        val external: Boolean,
        val handledAt: Instant?,

        // encoded as varint ordinal
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
        val message = EncodedFormat.encodeToString(payload)
        println(message)
        // 0fiXYI

        val result = EncodedFormat.decodeFromString<Payload>(message)
        assertEquals(result, payload)
    }

    @Serializable
    data class MapPayload(val map: Map<String, Int>)

    @Test
    fun `protobuf comparison`() {
        val payload = MapPayload(mapOf("k1" to 1285, "k2" to 9681))
        println(EncodedFormat(binaryFormat = ProtoBuf).encodeToString<MapPayload>(payload))
        println(EncodedFormat.encodeToString<MapPayload>(payload))
    }

    @Test
    fun `standalone readme`() {
        val bytes = "any byte data".encodeToByteArray()
        println(Base62.encode(bytes))
        println(Base36.encode(bytes))
        println(Base64.encode(bytes))
        println(Base85.encode(bytes))
    }

    @Serializable
    data class SensitiveData(val userData: Long)

    @Test
    fun `encryption serialization`() {
        Security.addProvider(BouncyCastleProvider())
        val random = SecureRandom()

        // This key is stored permanently so we can read payloads after jvm restart
        val keyBytes = ByteArray(16)
        random.nextBytes(keyBytes)
        val key = SecretKeySpec(keyBytes, "XTEA")

        // Wrap XTEA/CTR as a PayloadTransform.
        // Each encode prepends a fresh 8-byte IV; decode reads it back.
        val encryptCipher = Cipher.getInstance("XTEA/CTR/NoPadding", "BC")
        val decryptCipher = Cipher.getInstance("XTEA/CTR/NoPadding", "BC")
        val xteaTransform = object : PayloadTransform {
            override fun encode(data: ByteArray): ByteArray {
                val iv = ByteArray(8).also { random.nextBytes(it) }
                encryptCipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
                return iv + encryptCipher.doFinal(data)
            }

            override fun decode(data: ByteArray): ByteArray {
                val iv = data.copyOfRange(0, 8)
                decryptCipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
                return decryptCipher.doFinal(data.copyOfRange(8, data.size))
            }
        }

        val secureFormat = EncodedFormat { transform = xteaTransform }

        val payload = SensitiveData(random.nextLong())
        val token =
            secureFormat.encodeToString(SensitiveData.serializer(), payload)
        println(token)

        val result =
            secureFormat.decodeFromString(SensitiveData.serializer(), token)
        println(result)

        assertEquals(payload, result)
    }
}
