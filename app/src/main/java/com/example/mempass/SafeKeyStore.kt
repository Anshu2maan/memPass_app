package com.example.mempass

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafeKeyStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val keyAlias = "db_encryption_key_v1"
    private val provider = "AndroidKeyStore"
    private val sharedPrefsName = "db_security_prefs"
    private val encryptedKeyKey = "encrypted_db_key"
    private val ivKey = "db_iv"

    fun getDatabasePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE)
        val encryptedKey = prefs.getString(encryptedKeyKey, null)
        val iv = prefs.getString(ivKey, null)

        return if (encryptedKey != null && iv != null) {
            decryptKey(encryptedKey, iv)
        } else {
            val newKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
            encryptAndSaveKey(newKey)
            newKey
        }
    }

    private fun encryptAndSaveKey(key: ByteArray) {
        generateMasterKeyIfMissing()
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        val masterKey = (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(key)

        context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE).edit()
            .putString(encryptedKeyKey, android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT))
            .putString(ivKey, android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT))
            .apply()
    }

    private fun decryptKey(encryptedStr: String, ivStr: String): ByteArray {
        val encrypted = android.util.Base64.decode(encryptedStr, android.util.Base64.DEFAULT)
        val iv = android.util.Base64.decode(ivStr, android.util.Base64.DEFAULT)

        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        val masterKey = (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun generateMasterKeyIfMissing() {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }
    }
}
