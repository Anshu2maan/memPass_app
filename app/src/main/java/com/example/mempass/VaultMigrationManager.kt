package com.example.mempass

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.mempass.common.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VaultMigrationManager @Inject constructor(
    private val application: Application,
    private val repository: VaultRepository,
    private val vaultManager: VaultManager,
    @Named("SecurityPrefs") private val prefs: SharedPreferences
) {
    private val TAG = "VaultMigrationManager"

    suspend fun rotateMasterKey(
        oldPin: CharArray,
        newPin: CharArray,
        onProgress: (String) -> Unit
    ): Result<CharArray> = withContext(Dispatchers.Default) {
        try {
            withContext(Dispatchers.Main) { onProgress("Verifying credentials...") }
            val currentMK = vaultManager.getKey() ?: throw Exception("Vault must be unlocked")
            
            val storedHashBase64 = prefs.getString(Constants.KEY_PIN_HASH, null)
            val storedHash = Base64.decode(storedHashBase64, Base64.DEFAULT)
            val type = prefs.getString(Constants.KEY_DERIVATION_TYPE, "argon2id")
            val saltStr = prefs.getString(Constants.KEY_ARGON2_SALT, null)
            val salt = if (saltStr != null) Base64.decode(saltStr, Base64.DEFAULT) else KeyManager.generateSalt()
            
            val oldPinKey = if (type == "argon2id") KeyManager.deriveKeyArgon2(oldPin, salt) 
                            else KeyManager.deriveKeySha256(oldPin)
            
            val decryptedVerify = CryptoUtils.decryptToChars(storedHash, oldPinKey!!)
            if (!decryptedVerify.contentEquals("VERIFY".toCharArray())) {
                return@withContext Result.failure(Exception("Incorrect old PIN"))
            }

            withContext(Dispatchers.Main) { onProgress("Generating new master key...") }
            val newMasterKeyBytes = KeyManager.generateSalt().let { it + KeyManager.generateSalt() }
            val newMasterKey = SecretKeySpec(newMasterKeyBytes, "AES")
            val newMasterKeyChars = CryptoUtils.bytesToChars(newMasterKeyBytes)

            withContext(Dispatchers.Main) { onProgress("Migrating passwords...") }
            val passwords = repository.allPasswords.first().map { entry ->
                entry.copy(
                    encryptedUsername = CryptoUtils.encrypt(CryptoUtils.decryptToChars(entry.encryptedUsername, currentMK), newMasterKey),
                    encryptedPassword = CryptoUtils.encrypt(CryptoUtils.decryptToChars(entry.encryptedPassword, currentMK), newMasterKey),
                    encryptedNotes = CryptoUtils.encrypt(CryptoUtils.decryptToChars(entry.encryptedNotes, currentMK), newMasterKey),
                    encryptedTotpSecret = entry.encryptedTotpSecret?.let { CryptoUtils.encrypt(CryptoUtils.decryptToChars(it, currentMK), newMasterKey) }
                )
            }

            withContext(Dispatchers.Main) { onProgress("Migrating documents...") }
            val allDocs = repository.allDocuments.first()
            val documents = allDocs.map { entry ->
                entry.copy(
                    encryptedFields = CryptoUtils.encrypt(CryptoUtils.decryptToChars(entry.encryptedFields, currentMK), newMasterKey),
                    encryptedNotes = CryptoUtils.encrypt(CryptoUtils.decryptToChars(entry.encryptedNotes, currentMK), newMasterKey)
                )
            }

            withContext(Dispatchers.Main) { onProgress("Migrating notes...") }
            val allNotes = repository.allNotes.first()
            val notes = allNotes.map { entry ->
                entry.copy(
                    encryptedContent = CryptoUtils.encrypt(CryptoUtils.decryptToChars(entry.encryptedContent, currentMK), newMasterKey)
                )
            }

            withContext(Dispatchers.Main) { onProgress("Migrating files...") }
            allDocs.forEach { doc ->
                doc.filePaths.split("|").filter { it.isNotEmpty() }.forEach { path ->
                    reEncryptFile(path, currentMK, newMasterKey)
                }
                doc.thumbnailPath?.let { reEncryptFile(it, currentMK, newMasterKey) }
            }
            allNotes.forEach { note ->
                note.snippetFilePaths.split("|").filter { it.isNotEmpty() }.forEach { path ->
                    reEncryptFile(path, currentMK, newMasterKey)
                }
            }

            withContext(Dispatchers.Main) { onProgress("Saving changes...") }
            repository.migrateVaultData(passwords, documents, notes)

            val newSalt = KeyManager.generateSalt()
            val newPinKey = KeyManager.deriveKeyArgon2(newPin, newSalt) ?: KeyManager.deriveKeySha256(newPin)
            
            val newEncryptedMKByPin = CryptoUtils.encrypt(newMasterKeyChars, newPinKey)
            val newWrappedMKByPin = KeystoreHelper.wrap(newEncryptedMKByPin)
            
            val verifyChars = "VERIFY".toCharArray()
            val newPinVerifyHash = CryptoUtils.encrypt(verifyChars, newPinKey)

            val recoveryKeyChars = KeyManager.generateRecoveryKey()
            val recoveryKeyKey = KeyManager.deriveKeySha256(recoveryKeyChars)
            val newEncryptedMKByRecovery = CryptoUtils.encrypt(newMasterKeyChars, recoveryKeyKey)
            val newWrappedMKByRecovery = KeystoreHelper.wrap(newEncryptedMKByRecovery)
            
            val encryptedRecoveryKey = CryptoUtils.encrypt(recoveryKeyChars, newMasterKey)
            val wrappedRecoveryKey = KeystoreHelper.wrap(encryptedRecoveryKey)
            
            val newRecoveryKeyBytesForSync = CryptoUtils.charToBytes(recoveryKeyChars)
            val wrappedRecoveryKeyForSync = KeystoreHelper.wrap(newRecoveryKeyBytesForSync)

            prefs.edit().apply {
                putString(Constants.KEY_PIN_HASH, Base64.encodeToString(newPinVerifyHash, Base64.DEFAULT))
                putString(Constants.KEY_MASTER_KEY_BY_PIN, newWrappedMKByPin)
                putString(Constants.KEY_MASTER_KEY_BY_RECOVERY, newWrappedMKByRecovery)
                putString(Constants.KEY_RECOVERY_KEY_HASH, wrappedRecoveryKey)
                putString(Constants.KEY_RECOVERY_KEY_STORED, wrappedRecoveryKeyForSync)
                putString(Constants.KEY_ARGON2_SALT, Base64.encodeToString(newSalt, Base64.DEFAULT))
                apply()
            }

            vaultManager.setKey(newMasterKey)
            CryptoUtils.wipe(newMasterKeyBytes, newMasterKeyChars, verifyChars, newRecoveryKeyBytesForSync)
            
            Result.success(recoveryKeyChars)

        } catch (e: Exception) {
            Log.e(TAG, "Key rotation failed", e)
            Result.failure(e)
        }
    }

    private suspend fun reEncryptFile(path: String, oldKey: SecretKeySpec, newKey: SecretKeySpec) {
        val file = File(path)
        if (!file.exists()) return
        val decryptedTemp = File(application.cacheDir, "dec_${UUID.randomUUID()}")
        val encryptedTemp = File(application.cacheDir, "enc_${UUID.randomUUID()}")
        try {
            FileEncryptor.decryptFileToFile(file, decryptedTemp, oldKey)
            FileEncryptor.encryptFile(decryptedTemp, encryptedTemp, newKey)
            if (encryptedTemp.exists()) {
                if (file.delete()) {
                    encryptedTemp.renameTo(file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to re-encrypt file: $path", e)
        } finally {
            if (decryptedTemp.exists()) decryptedTemp.delete()
            if (encryptedTemp.exists()) encryptedTemp.delete()
        }
    }
}
