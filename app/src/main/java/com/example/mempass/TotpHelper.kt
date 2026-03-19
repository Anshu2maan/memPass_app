package com.example.mempass

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.util.Log
import java.nio.ByteBuffer
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object TotpHelper {
    private const val TAG = "TotpHelper"

    /**
     * Checks if the device is configured to use network-provided time.
     * This is a proxy for ensuring the clock is reasonably accurate for TOTP.
     */
    fun isTimeAutomatic(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a 6-digit TOTP code based on the provided secret.
     * Manual implementation to avoid library dependency issues.
     */
    fun generateTotp(secret: CharArray): String {
        if (secret.isEmpty()) return "------"
        val keyBytes = try {
            decodeBase32(secret)
        } catch (e: Exception) {
            Log.e(TAG, "Base32 decoding failed: ${e.message}")
            return "------"
        }

        return try {
            val timeStep = System.currentTimeMillis() / 1000 / 30
            
            val data = ByteBuffer.allocate(8).putLong(timeStep).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
            val hash = mac.doFinal(data)
            
            val offset = hash[hash.size - 1].toInt() and 0xf
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                         ((hash[offset + 1].toInt() and 0xff) shl 16) or
                         ((hash[offset + 2].toInt() and 0xff) shl 8) or
                         (hash[offset + 3].toInt() and 0xff)
            
            val otp = binary % 10.0.pow(6).toInt()
            otp.toString().padStart(6, '0')
        } catch (e: Exception) {
            Log.e(TAG, "TOTP Generation failed: ${e.message}")
            "------"
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    /**
     * Extracts the 'secret' parameter from an otpauth:// URI.
     */
    fun extractSecretFromUri(uri: String): String? {
        return try {
            val androidUri = Uri.parse(uri)
            if (androidUri.scheme == "otpauth" || androidUri.scheme == "totp") {
                androidUri.getQueryParameter("secret")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isValidSecret(secret: CharArray): Boolean {
        if (secret.isEmpty()) return false
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        return secret.all { it.uppercaseChar() in alphabet || it.isWhitespace() }
    }

    /**
     * Internal Base32 decoder.
     * Throws IllegalArgumentException on invalid input.
     */
    private fun decodeBase32(secret: CharArray): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val iKey = IntArray(256) { -1 }
        for (i in alphabet.indices) iKey[alphabet[i].code] = i

        var buffer = 0
        var bitsLeft = 0
        var index = 0
        // Base32: 8 chars -> 5 bytes. 1 char -> 5 bits.
        val out = ByteArray((secret.size * 5) / 8)

        for (char in secret) {
            if (char.isWhitespace() || char == '=') continue
            
            val charCode = char.uppercaseChar().code
            val valIndex = if (charCode < iKey.size) iKey[charCode] else -1
            
            if (valIndex == -1) {
                throw IllegalArgumentException("Invalid Base32 character: $char")
            }
            
            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5
            if (bitsLeft >= 8) {
                if (index < out.size) {
                    out[index++] = (buffer shr (bitsLeft - 8)).toByte()
                }
                bitsLeft -= 8
            }
        }
        return out.copyOf(index)
    }
}
