package com.example.mempass

import android.util.Log
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptographyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Modern Cryptography utility for MemPass.
 * Uses AES-GCM-256 for all operations to ensure Authenticated Encryption.
 */
object CryptoUtils {
    private const val TAG = "CryptoUtils"
    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_LENGTH = 128

    fun wipe(vararg arrays: Any?) {
        arrays.forEach {
            when (it) {
                is ByteArray -> Arrays.fill(it, 0.toByte())
                is CharArray -> Arrays.fill(it, '\u0000')
            }
        }
    }

    fun encrypt(data: CharArray, key: SecretKeySpec): ByteArray {
        val bytes = charToBytes(data)
        return try {
            encryptRaw(bytes, key)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failure")
            throw CryptographyException("Security error during encryption", e)
        } finally {
            wipe(bytes)
        }
    }

    fun decryptToChars(encryptedData: ByteArray, key: SecretKeySpec): CharArray {
        if (encryptedData.isEmpty()) return CharArray(0)
        return try {
            val decryptedBytes = decryptRaw(encryptedData, key)
            val chars = bytesToChars(decryptedBytes)
            wipe(decryptedBytes)
            chars
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failure")
            throw CryptographyException("Security error during decryption", e)
        }
    }

    /**
     * Decrypts data with one key and immediately re-encrypts it with another key.
     * The plaintext never exists as a String.
     */
    fun decryptAndReEncrypt(encryptedData: ByteArray, oldKey: SecretKeySpec, newKey: SecretKeySpec): ByteArray {
        if (encryptedData.isEmpty()) return ByteArray(0)
        val decryptedBytes = try {
            decryptRaw(encryptedData, oldKey)
        } catch (e: Exception) {
            Log.e(TAG, "Re-encryption decryption step failed")
            throw CryptographyException("Re-encryption failed at decryption step", e)
        }
        
        return try {
            encryptRaw(decryptedBytes, newKey)
        } catch (e: Exception) {
            Log.e(TAG, "Re-encryption encryption step failed")
            throw CryptographyException("Re-encryption failed at encryption step", e)
        } finally {
            wipe(decryptedBytes)
        }
    }

    fun encryptRaw(data: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_SIZE).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    fun decryptRaw(encryptedData: ByteArray, key: SecretKeySpec): ByteArray {
        if (encryptedData.size < IV_SIZE) throw CryptographyException("Malformed data")
        val iv = encryptedData.sliceArray(0 until IV_SIZE)
        val ciphertext = encryptedData.sliceArray(IV_SIZE until encryptedData.size)
        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        return cipher.doFinal(ciphertext)
    }

    fun charToBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }

    fun bytesToChars(bytes: ByteArray): CharArray {
        val byteBuffer = ByteBuffer.wrap(bytes)
        val charBuffer = StandardCharsets.UTF_8.decode(byteBuffer)
        val chars = CharArray(charBuffer.remaining())
        charBuffer.get(chars)
        return chars
    }
}
