package com.example.mempass

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreHelper {
    private const val KEY_ALIAS = "mempass_master_wrapper"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Wraps (Encrypts) raw bytes using Keystore-backed key.
     * Returns a Base64 string safe for SharedPreferences.
     */
    fun wrap(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        // Combine IV and Encrypted data: [IV_SIZE(1 byte) | IV | ENCRYPTED_DATA]
        val result = ByteArray(1 + iv.size + encrypted.size)
        result[0] = iv.size.toByte()
        System.arraycopy(iv, 0, result, 1, iv.size)
        System.arraycopy(encrypted, 0, result, 1 + iv.size, encrypted.size)
        
        return Base64.encodeToString(result, Base64.NO_WRAP)
    }

    /**
     * Unwraps (Decrypts) data using Keystore-backed key and returns raw bytes.
     */
    fun unwrapToBytes(wrappedData: String): ByteArray? {
        return try {
            val decoded = Base64.decode(wrappedData, Base64.NO_WRAP)
            val ivSize = decoded[0].toInt()
            val iv = decoded.copyOfRange(1, 1 + ivSize)
            val encrypted = decoded.copyOfRange(1 + ivSize, decoded.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * @deprecated Use unwrapToBytes for binary data.
     */
    @Deprecated("Use unwrapToBytes")
    fun unwrap(wrappedData: String): String? {
        val bytes = unwrapToBytes(wrappedData) ?: return null
        return String(bytes, Charsets.UTF_8)
    }
}
