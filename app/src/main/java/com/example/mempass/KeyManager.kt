package com.example.mempass

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.example.mempass.common.Constants
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
    private const val SALT_SIZE = 16

    /**
     * Derives a key using Argon2id with parameters defined in Constants.
     * Includes a fallback for low-end devices to prevent OOM.
     */
    fun deriveKeyArgon2(context: Context, pin: CharArray, salt: ByteArray): SecretKeySpec? {
        val pinBytes = charToBytes(pin)
        return try {
            val useLowEnd = isLowEndDevice(context)
            val memory = if (useLowEnd) Constants.ARGON2_MEMORY_LOW_END else Constants.ARGON2_MEMORY
            val iterations = if (useLowEnd) Constants.ARGON2_ITERATIONS_LOW_END else Constants.ARGON2_ITERATIONS
            val parallelism = Constants.ARGON2_PARALLELISM

            Log.d(TAG, "Deriving key using Argon2id (Memory: ${memory}KB, Iterations: ${iterations}, Low-end: $useLowEnd)...")
            
            val argon2Kt = Argon2Kt()
            val result = argon2Kt.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = pinBytes,
                salt = salt,
                tCostInIterations = iterations,
                mCostInKibibyte = memory,
                parallelism = parallelism,
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

    /**
     * Determines if the device should be treated as low-end (< 2GB RAM).
     */
    private fun isLowEndDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        // 2GB threshold in bytes
        return memoryInfo.totalMem < 2L * 1024 * 1024 * 1024
    }

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
