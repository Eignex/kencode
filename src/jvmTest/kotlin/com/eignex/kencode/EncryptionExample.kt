package com.eignex.kencode

import kotlinx.serialization.Serializable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class EncryptionExample {

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
