package com.example.mempass

import android.util.Log
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.spec.SecretKeySpec

object KeyManager {
    private const val TAG = "KeyManager"
    
    // Argon2id Default Parameters (Mobile Optimized)
    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_MEMORY = 16384 // 16 MB
    private const val ARGON2_PARALLELISM = 1
    private const val SALT_SIZE = 16

    fun deriveKeySha256(pin: CharArray): SecretKeySpec {
        val pinBytes = charToBytes(pin)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(pinBytes)
            SecretKeySpec(bytes, "AES")
        } finally {
            Arrays.fill(pinBytes, 0.toByte())
        }
    }

    fun deriveKeyArgon2(pin: CharArray, salt: ByteArray): SecretKeySpec? {
        val pinBytes = charToBytes(pin)
        return try {
            Log.d(TAG, "Deriving key using Argon2id...")
            val argon2Kt = Argon2Kt()
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pinBytes,
                salt = salt,
                tCostInIterations = ARGON2_ITERATIONS,
                mCostInKibibyte = ARGON2_MEMORY,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = 32
            )
            val hashBytes = ByteArray(result.rawHash.remaining())
            result.rawHash.get(hashBytes)
            SecretKeySpec(hashBytes, "AES")
        } catch (e: Exception) {
            Log.e(TAG, "Argon2 derivation failed: ${e.message}", e)
            null
        } finally {
            Arrays.fill(pinBytes, 0.toByte())
        }
    }

    private fun charToBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun generateRecoveryKey(): CharArray {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        val key = (1..24)
            .map { chars[random.nextInt(chars.length)] }
            .chunked(4)
            .joinToString("-") { it.joinToString("") }
        return key.toCharArray()
    }
}
