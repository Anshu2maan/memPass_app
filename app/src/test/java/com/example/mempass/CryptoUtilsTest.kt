package com.example.mempass

import android.util.Base64
import android.util.Log
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import java.util.Base64 as JavaBase64

class CryptoUtilsTest {

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0

        // Mock Android Base64 using Java's Base64
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            JavaBase64.getEncoder().encodeToString(it.invocation.args[0] as ByteArray)
        }
        every { Base64.decode(any<String>(), any()) } answers {
            JavaBase64.getDecoder().decode(it.invocation.args[0] as String)
        }
    }

    @Test
    fun testEncryptionDecryption() {
        val pin = "123456".toCharArray()
        val key = KeyManager.deriveKeySha256(pin)
        
        val originalData = "SensitiveData123"
        val encryptedData = CryptoUtils.encrypt(originalData, key)
        
        assertNotEquals(originalData, encryptedData)
        
        val decryptedData = CryptoUtils.decrypt(encryptedData, key)
        assertEquals(originalData, decryptedData)
    }

    @Test
    fun testLegacyDecryptionFallback() {
        val pin = "legacy_pin".toCharArray()
        val key = KeyManager.deriveKeySha256(pin)
        val data = "Legacy Secret"
        
        // Manual legacy encryption
        val cipher = javax.crypto.Cipher.getInstance("AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        val legacyEncryptedString = JavaBase64.getEncoder().encodeToString(encryptedBytes)
        
        // Decrypt using our util which should handle the fallback
        val decrypted = CryptoUtils.decrypt(legacyEncryptedString, key)
        assertEquals(data, decrypted)
    }

    @Test
    fun testKeyDerivationSha256() {
        val pin = "sha256_test".toCharArray()
        val key1 = KeyManager.deriveKeySha256(pin)
        val key2 = KeyManager.deriveKeySha256(pin)
        
        assertNotNull(key1)
        assertArrayEquals(key1.encoded, key2.encoded)
        
        // Manual verification
        val digest = MessageDigest.getInstance("SHA-256")
        // KeyManager.charToBytes internal logic used UTF-8
        val expectedBytes = digest.digest("sha256_test".toByteArray(Charsets.UTF_8))
        assertArrayEquals(expectedBytes, key1.encoded)
    }

    @Test
    fun testGenerateRecoveryKey() {
        val key = KeyManager.generateRecoveryKey()
        // Format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
        assertTrue(key.matches(Regex("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}")))
    }
}
