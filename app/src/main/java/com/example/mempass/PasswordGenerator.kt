package com.example.mempass

import com.nulabinc.zxcvbn.Zxcvbn
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PasswordGenerator {
    private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    /**
     * Generates a password deterministically from a master phrase and service name.
     * Uses HMAC-SHA256 to derive entropy, replacing weak java.util.Random.
     */
    fun generateDeterministicPassword(
        masterPhrase: CharArray,
        service: String,
        version: Int,
        length: Int
    ): CharArray {
        val input = "${service.lowercase()}:v$version"
        val hmac = Mac.getInstance("HmacSHA256")
        
        // Convert CharArray to bytes securely
        val phraseBytes = masterPhrase.map { it.code.toByte() }.toByteArray()
        val secretKey = SecretKeySpec(phraseBytes, "HmacSHA256")
        hmac.init(secretKey)
        
        // Generate entropy pool using HMAC
        var entropy = hmac.doFinal(input.toByteArray())
        
        val result = CharArray(length)
        val allChars = LOWERCASE + UPPERCASE + DIGITS + SYMBOLS
        
        // 1. Fill result with characters derived from HMAC bytes
        for (i in 0 until length) {
            // If we need more entropy for long passwords, re-hash
            if (i >= entropy.size) {
                entropy = hmac.doFinal(entropy)
            }
            val byteVal = entropy[i % entropy.size].toInt() and 0xFF
            
            // Guarantee complexity for first 4 slots, then use full set
            result[i] = when (i) {
                0 -> LOWERCASE[byteVal % LOWERCASE.length]
                1 -> UPPERCASE[byteVal % UPPERCASE.length]
                2 -> DIGITS[byteVal % DIGITS.length]
                3 -> SYMBOLS[byteVal % SYMBOLS.length]
                else -> allChars[byteVal % allChars.length]
            }
        }
        
        // 2. Deterministic Shuffle (Fisher-Yates) using a second HMAC pass for shuffle entropy
        // This ensures the shuffle is as strong as the hash itself.
        var shuffleEntropy = hmac.doFinal(entropy + "shuffle".toByteArray())
        for (i in length - 1 downTo 1) {
            if ((length - 1 - i) >= shuffleEntropy.size) {
                shuffleEntropy = hmac.doFinal(shuffleEntropy)
            }
            val byteVal = shuffleEntropy[(length - 1 - i) % shuffleEntropy.size].toInt() and 0xFF
            val j = byteVal % (i + 1)
            
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }
        
        // Securely wipe sensitive byte array
        phraseBytes.fill(0)
        return result
    }

    /**
     * Improved Random Generator that uses cryptographically secure SecureRandom.
     */
    fun generateRandomPassword(length: Int): CharArray {
        val random = SecureRandom()
        val result = CharArray(length)

        // Guarantee complexity
        result[0] = LOWERCASE[random.nextInt(LOWERCASE.length)]
        result[1] = UPPERCASE[random.nextInt(UPPERCASE.length)]
        result[2] = DIGITS[random.nextInt(DIGITS.length)]
        result[3] = SYMBOLS[random.nextInt(SYMBOLS.length)]

        val allChars = LOWERCASE + UPPERCASE + DIGITS + SYMBOLS
        for (i in 4 until length) {
            result[i] = allChars[random.nextInt(allChars.length)]
        }

        // Secure Shuffle
        for (i in result.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }

        return result
    }

    data class StrengthInfo(
        val score: Float,
        val label: String,
        val crackTimeDisplay: String,
        val suggestions: List<String>,
        val warning: String?
    )

    fun calculateStrengthDetailed(password: CharArray): StrengthInfo {
        if (password.isEmpty()) return StrengthInfo(0f, "Empty", "", emptyList(), null)
        
        val zxcvbn = Zxcvbn()
        val tempPassword = String(password)
        val result = zxcvbn.measure(tempPassword)
        
        val (score, label) = when (result.score) {
            0 -> 0.1f to "Very Weak"
            1 -> 0.3f to "Weak"
            2 -> 0.6f to "Fair"
            3 -> 0.8f to "Strong"
            4 -> 1.0f to "Very Strong"
            else -> 0.0f to "Unknown"
        }

        return StrengthInfo(
            score = score,
            label = label,
            crackTimeDisplay = result.crackTimesDisplay.offlineFastHashing1e10PerSecond,
            suggestions = result.feedback.suggestions,
            warning = result.feedback.warning
        )
    }

    fun calculateStrength(password: CharArray): Pair<Float, String> {
        val info = calculateStrengthDetailed(password)
        return info.score to info.label
    }
}
