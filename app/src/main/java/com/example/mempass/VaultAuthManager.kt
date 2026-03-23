package com.example.mempass

import android.app.Application
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.example.mempass.common.Constants
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Handles the business logic for Vault Authentication:
 * Setup, Unlocking, PIN recovery, and Lockout management.
 */
@Singleton
class VaultAuthManager @Inject constructor(
    private val application: Application,
    private val vaultManager: VaultManager,
    @Named("SecurityPrefs") private val prefs: SharedPreferences
) {
    private val TAG = "VaultAuthManager"

    fun isFirstTime(): Boolean = 
        prefs.getString(Constants.KEY_MASTER_KEY_BY_PIN, null) == null && 
        prefs.getString(Constants.KEY_PIN_HASH, null) == null

    fun isLockedOut(onTimeRemaining: (Long) -> Unit): Boolean {
        val lockoutUntil = prefs.getLong(Constants.KEY_LOCKOUT_UNTIL, 0L)
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime < lockoutUntil) {
            onTimeRemaining(lockoutUntil - currentTime)
            return true
        }
        return false
    }

    fun incrementFailedAttempts() {
        val attempts = prefs.getInt(Constants.KEY_FAILED_ATTEMPTS, 0) + 1
        val editor = prefs.edit().putInt(Constants.KEY_FAILED_ATTEMPTS, attempts)
        
        if (attempts >= Constants.MAX_FAILED_ATTEMPTS) {
            val exponent = (attempts - Constants.MAX_FAILED_ATTEMPTS).toDouble()
            val lockoutDuration = (Constants.BASE_LOCKOUT_MS * Math.pow(2.0, exponent)).toLong()
                .coerceAtMost(Constants.MAX_LOCKOUT_MS)
            
            editor.putLong(Constants.KEY_LOCKOUT_UNTIL, SystemClock.elapsedRealtime() + lockoutDuration)
            Log.w(TAG, "Vault locked for ${lockoutDuration / 1000}s due to too many failed attempts")
        }
        editor.apply()
    }

    fun setupVault(pin: CharArray): CharArray {
        val masterKeyBytes = KeyManager.generateSalt().let { it + KeyManager.generateSalt() }
        val masterKey = SecretKeySpec(masterKeyBytes, "AES")
        val masterKeyChars = CryptoUtils.bytesToChars(masterKeyBytes)

        val salt = KeyManager.generateSalt()
        val pinKey = KeyManager.deriveKeyArgon2(application, pin, salt) ?: KeyManager.deriveKeySha256(pin)
        
        val encryptedMasterKeyByPin = CryptoUtils.encrypt(masterKeyChars, pinKey)
        val recoveryKeyChars = KeyManager.generateRecoveryKey()
        val recoveryKeyKey = KeyManager.deriveKeySha256(recoveryKeyChars)
        val encryptedMasterKeyByRecovery = CryptoUtils.encrypt(masterKeyChars, recoveryKeyKey)
        
        val wrappedMasterKeyByPin = KeystoreHelper.wrap(encryptedMasterKeyByPin)
        val wrappedMasterKeyByRecovery = KeystoreHelper.wrap(encryptedMasterKeyByRecovery)

        val encryptedRecoveryKey = CryptoUtils.encrypt(recoveryKeyChars, masterKey)
        val wrappedRecoveryKey = KeystoreHelper.wrap(encryptedRecoveryKey)

        val recoveryKeyBytes = CryptoUtils.charToBytes(recoveryKeyChars)
        val wrappedRecoveryKeyForSync = KeystoreHelper.wrap(recoveryKeyBytes)
        
        val verifyChars = "VERIFY".toCharArray()
        val pinVerifyHash = CryptoUtils.encrypt(verifyChars, pinKey)
        
        vaultManager.setKey(masterKey)

        prefs.edit().apply {
            putString(Constants.KEY_PIN_HASH, Base64.encodeToString(pinVerifyHash, Base64.DEFAULT))
            putString(Constants.KEY_MASTER_KEY_BY_PIN, wrappedMasterKeyByPin)
            putString(Constants.KEY_MASTER_KEY_BY_RECOVERY, wrappedMasterKeyByRecovery)
            putString(Constants.KEY_RECOVERY_KEY_HASH, wrappedRecoveryKey)
            putString(Constants.KEY_RECOVERY_KEY_STORED, wrappedRecoveryKeyForSync)
            putString(Constants.KEY_DERIVATION_TYPE, "argon2id")
            putString(Constants.KEY_ARGON2_SALT, Base64.encodeToString(salt, Base64.DEFAULT))
            putInt(Constants.KEY_FAILED_ATTEMPTS, 0)
            putLong(Constants.KEY_LOCKOUT_UNTIL, 0L)
            apply()
        }
        
        CryptoUtils.wipe(masterKeyBytes, masterKeyChars, verifyChars, recoveryKeyBytes)
        return recoveryKeyChars
    }

    fun unlockVault(pin: CharArray): Boolean {
        val wrappedMasterKeyByPin = prefs.getString(Constants.KEY_MASTER_KEY_BY_PIN, null) ?: return false
        val encryptedMasterKeyByPin = KeystoreHelper.unwrapToBytes(wrappedMasterKeyByPin) ?: return false
        
        val storedHashBase64 = prefs.getString(Constants.KEY_PIN_HASH, null) ?: return false
        val storedHash = Base64.decode(storedHashBase64, Base64.DEFAULT)
        
        val type = prefs.getString(Constants.KEY_DERIVATION_TYPE, "sha256")
        val saltStr = prefs.getString(Constants.KEY_ARGON2_SALT, null)
        
        return try {
            var pinKey: SecretKeySpec? = null
            if (type == "argon2id" && saltStr != null) {
                val salt = Base64.decode(saltStr, Base64.DEFAULT)
                pinKey = KeyManager.deriveKeyArgon2(application, pin, salt)
            }
            if (pinKey == null) pinKey = KeyManager.deriveKeySha256(pin)

            val decryptedVerify = CryptoUtils.decryptToChars(storedHash, pinKey!!)
            if (decryptedVerify.contentEquals("VERIFY".toCharArray())) {
                val masterKeyChars = CryptoUtils.decryptToChars(encryptedMasterKeyByPin, pinKey)
                val masterKeyBytes = CryptoUtils.charToBytes(masterKeyChars)
                val masterKey = SecretKeySpec(masterKeyBytes, "AES")
                vaultManager.setKey(masterKey)
                
                prefs.edit().apply {
                    putInt(Constants.KEY_FAILED_ATTEMPTS, 0)
                    putLong(Constants.KEY_LOCKOUT_UNTIL, 0L)
                    apply()
                }
                CryptoUtils.wipe(decryptedVerify, masterKeyChars, masterKeyBytes)
                true
            } else {
                CryptoUtils.wipe(decryptedVerify)
                incrementFailedAttempts()
                false
            }
        } catch (e: Exception) {
            incrementFailedAttempts()
            false
        }
    }

    fun resetPinWithRecoveryKey(recoveryKey: CharArray, newPin: CharArray): Boolean {
        return try {
            val wrappedMasterKeyByRecovery = prefs.getString(Constants.KEY_MASTER_KEY_BY_RECOVERY, null) ?: return false
            val encryptedMasterKeyByRecovery = KeystoreHelper.unwrapToBytes(wrappedMasterKeyByRecovery) ?: return false

            val recoveryKeyKey = KeyManager.deriveKeySha256(recoveryKey)
            val masterKeyChars = CryptoUtils.decryptToChars(encryptedMasterKeyByRecovery, recoveryKeyKey)
            if (masterKeyChars.isEmpty()) return false
            
            val masterKeyBytes = CryptoUtils.charToBytes(masterKeyChars)
            val masterKey = SecretKeySpec(masterKeyBytes, "AES")

            val salt = KeyManager.generateSalt()
            val newPinKey = KeyManager.deriveKeyArgon2(application, newPin, salt) ?: KeyManager.deriveKeySha256(newPin)
            
            val newEncryptedMasterKeyByPin = CryptoUtils.encrypt(masterKeyChars, newPinKey)
            val newWrappedMasterKeyByPin = KeystoreHelper.wrap(newEncryptedMasterKeyByPin)
            
            val verifyChars = "VERIFY".toCharArray()
            val newPinVerifyHash = CryptoUtils.encrypt(verifyChars, newPinKey)

            val currentWrappedRecovery = prefs.getString(Constants.KEY_RECOVERY_KEY_HASH, null)
            var newWrappedRecovery = currentWrappedRecovery
            if (currentWrappedRecovery == null) {
                val encryptedRecoveryKey = CryptoUtils.encrypt(recoveryKey, masterKey)
                newWrappedRecovery = KeystoreHelper.wrap(encryptedRecoveryKey)
            }

            prefs.edit().apply {
                putString(Constants.KEY_PIN_HASH, Base64.encodeToString(newPinVerifyHash, Base64.DEFAULT))
                putString(Constants.KEY_MASTER_KEY_BY_PIN, newWrappedMasterKeyByPin)
                putString(Constants.KEY_RECOVERY_KEY_HASH, newWrappedRecovery)
                putString(Constants.KEY_DERIVATION_TYPE, "argon2id")
                putString(Constants.KEY_ARGON2_SALT, Base64.encodeToString(salt, Base64.DEFAULT))
                putInt(Constants.KEY_FAILED_ATTEMPTS, 0)
                putLong(Constants.KEY_LOCKOUT_UNTIL, 0L)
                apply()
            }
            
            vaultManager.setKey(masterKey)
            CryptoUtils.wipe(masterKeyChars, masterKeyBytes, verifyChars)
            true
        } catch (e: Exception) { 
            Log.e(TAG, "Recovery failed", e)
            false 
        }
    }

    fun regenerateRecoveryKey(): CharArray? {
        val masterKey = vaultManager.getKey() ?: return null
        val masterKeyBytes = masterKey.encoded
        val masterKeyChars = CryptoUtils.bytesToChars(masterKeyBytes)

        val newRecoveryKeyChars = KeyManager.generateRecoveryKey()
        val recoveryKeyKey = KeyManager.deriveKeySha256(newRecoveryKeyChars)
        
        val encryptedMasterKeyByRecovery = CryptoUtils.encrypt(masterKeyChars, recoveryKeyKey)
        val wrappedMasterKeyByRecovery = KeystoreHelper.wrap(encryptedMasterKeyByRecovery)
        
        val encryptedRecoveryKey = CryptoUtils.encrypt(newRecoveryKeyChars, masterKey)
        val wrappedRecoveryKey = KeystoreHelper.wrap(encryptedRecoveryKey)

        val recoveryKeyBytes = CryptoUtils.charToBytes(newRecoveryKeyChars)
        val wrappedRecoveryKeyForSync = KeystoreHelper.wrap(recoveryKeyBytes)

        prefs.edit().apply {
            putString(Constants.KEY_MASTER_KEY_BY_RECOVERY, wrappedMasterKeyByRecovery)
            putString(Constants.KEY_RECOVERY_KEY_HASH, wrappedRecoveryKey)
            putString(Constants.KEY_RECOVERY_KEY_STORED, wrappedRecoveryKeyForSync)
            apply()
        }
        
        CryptoUtils.wipe(masterKeyBytes, masterKeyChars, recoveryKeyBytes)
        return newRecoveryKeyChars
    }

    fun getCurrentRecoveryKey(): CharArray? {
        val masterKey = vaultManager.getKey() ?: return null
        val wrappedKey = prefs.getString(Constants.KEY_RECOVERY_KEY_HASH, null) ?: return null
        val encryptedKey = KeystoreHelper.unwrapToBytes(wrappedKey) ?: return null
        return try {
            CryptoUtils.decryptToChars(encryptedKey, masterKey)
        } catch (e: Exception) {
            null
        }
    }
}
