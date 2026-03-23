package com.example.mempass

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mempass.common.Constants
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named

data class ImportResult(val p: Int, val d: Int, val n: Int)

open class BaseVaultViewModel(
    application: Application,
    protected val repository: VaultRepository,
    @Named("SecurityPrefs") protected val prefs: SharedPreferences,
    @Named("HistoryPrefs") protected val historyPrefs: SharedPreferences,
    protected val vaultManager: VaultManager,
    protected val authManager: VaultAuthManager,
    val biometricHelper: BiometricHelper,
    val cameraHelper: CameraHelper
) : AndroidViewModel(application) {
    protected val TAG = this.javaClass.simpleName

    private val _isUnlocked = MutableStateFlow(vaultManager.isUnlocked())
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private val _themePreference = MutableStateFlow(prefs.getInt(Constants.KEY_THEME_PREF, 0))
    val themePreference: StateFlow<Int> = _themePreference

    private val _lockoutTimeRemaining = MutableStateFlow(0L)
    val lockoutTimeRemaining: StateFlow<Long> = _lockoutTimeRemaining

    private val _cryptoError = MutableSharedFlow<String>()
    val cryptoError: SharedFlow<String> = _cryptoError

    protected fun emitCryptoError(message: String) {
        viewModelScope.launch {
            _cryptoError.emit(message)
        }
    }

    // Temporary storage for PIN during transitions
    private var _tempPin: CharArray? = null

    fun setTempPin(pin: CharArray) {
        _tempPin = pin.copyOf()
    }

    fun getTempPin(): CharArray? = _tempPin

    fun clearTempPin() {
        _tempPin?.let { Arrays.fill(it, ' ') }
        _tempPin = null
    }

    fun setThemePreference(pref: Int) {
        _themePreference.value = pref
        prefs.edit().putInt(Constants.KEY_THEME_PREF, pref).apply()
    }

    fun isFirstTime(): Boolean = authManager.isFirstTime()

    fun isUnlocked(): Boolean {
        val unlocked = vaultManager.isUnlocked()
        if (_isUnlocked.value != unlocked) _isUnlocked.value = unlocked
        return unlocked
    }

    open fun lockVault() {
        vaultManager.clearKey()
        _isUnlocked.value = false
    }

    fun getVaultKey(): SecretKeySpec? {
        val key = vaultManager.getKey()
        val unlocked = key != null
        if (_isUnlocked.value != unlocked) _isUnlocked.value = unlocked
        return key
    }

    fun decryptToChars(encryptedData: ByteArray): CharArray {
        if (encryptedData.isEmpty()) return CharArray(0)
        val key = getVaultKey() ?: return CharArray(0)
        return try {
            CryptoUtils.decryptToChars(encryptedData, key)
        } catch (e: CryptographyException) {
            Log.e(TAG, "Decryption failed", e)
            emitCryptoError(e.message ?: "Decryption failed")
            CharArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during decryption", e)
            CharArray(0)
        }
    }

    fun isLockedOut(): Boolean {
        return authManager.isLockedOut { remaining ->
            _lockoutTimeRemaining.value = remaining
        }
    }

    fun setupVault(pin: CharArray): CharArray {
        val recoveryKey = authManager.setupVault(pin)
        _isUnlocked.value = true
        return recoveryKey
    }

    fun unlockVault(pin: CharArray): Boolean {
        if (isLockedOut()) return false
        val success = authManager.unlockVault(pin)
        if (success) _isUnlocked.value = true
        return success
    }

    fun resetPinWithRecoveryKey(recoveryKey: CharArray, newPin: CharArray): Boolean {
        val success = authManager.resetPinWithRecoveryKey(recoveryKey, newPin)
        if (success) _isUnlocked.value = true
        return success
    }

    fun regenerateRecoveryKey(): CharArray? = authManager.regenerateRecoveryKey()

    fun getCurrentRecoveryKey(): CharArray? = authManager.getCurrentRecoveryKey()

    fun getExportLogs(): List<String> {
        return historyPrefs.getStringSet("export_logs", emptySet())?.toList() ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        clearTempPin()
    }
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    application: Application,
    repository: VaultRepository,
    @Named("SecurityPrefs") prefs: SharedPreferences,
    @Named("HistoryPrefs") historyPrefs: SharedPreferences,
    vaultManager: VaultManager,
    authManager: VaultAuthManager,
    biometricHelper: BiometricHelper,
    cameraHelper: CameraHelper,
    private val healthManager: VaultHealthManager,
    private val backupHelper: VaultBackupHelper,
    private val migrationManager: VaultMigrationManager,
    val fileUtils: FileUtils
) : BaseVaultViewModel(application, repository, prefs, historyPrefs, vaultManager, authManager, biometricHelper, cameraHelper) {

    val allPasswords: Flow<List<PasswordEntry>> = repository.allPasswords
    val allDocuments: Flow<List<DocumentEntry>> = repository.allDocuments
    val allNotes: Flow<List<NoteEntry>> = repository.allNotes

    private val _isBackupProcessing = MutableStateFlow(false)
    val isBackupProcessing: StateFlow<Boolean> = _isBackupProcessing

    val vaultHealth: Flow<VaultHealth> = healthManager.getVaultHealth(allPasswords, allDocuments, ::decryptToChars)
    val securityTips: Flow<List<SecurityTip>> = healthManager.getSecurityTips(allPasswords, allDocuments, ::decryptToChars)

    private fun runWithBackupLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isBackupProcessing.value = true
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Backup operation failed", e)
                emitCryptoError(e.localizedMessage ?: "Operation failed")
            } finally {
                _isBackupProcessing.value = false
            }
        }
    }

    fun savePassword(
        service: String, 
        user: CharArray, 
        pass: CharArray, 
        notes: CharArray, 
        totpSecret: CharArray? = null, 
        associatedPackage: String? = null,
        associatedDomain: String? = null,
        id: Int = 0
    ) {
        val key = getVaultKey() ?: return
        
        // SECURITY FIX: Encrypt synchronously BEFORE the coroutine starts.
        // This prevents a race condition where the UI wipes the source CharArrays 
        // before the background thread can encrypt them.
        val encUser = CryptoUtils.encrypt(user, key)
        val encPass = CryptoUtils.encrypt(pass, key)
        val encNotes = CryptoUtils.encrypt(notes, key)
        val encTotp = totpSecret?.let { CryptoUtils.encrypt(it, key) }

        viewModelScope.launch {
            try {
                repository.insertPassword(PasswordEntry(
                    id = id, serviceName = service,
                    encryptedUsername = encUser,
                    encryptedPassword = encPass,
                    encryptedNotes = encNotes,
                    encryptedTotpSecret = encTotp,
                    associatedPackageName = associatedPackage,
                    associatedDomain = associatedDomain
                ))
            } catch (e: CryptographyException) {
                Log.e("VaultViewModel", "Failed to save password due to crypto error", e)
                emitCryptoError("Failed to save: ${e.message}")
            } catch (e: Exception) {
                Log.e("VaultViewModel", "Failed to save password", e)
            }
        }
    }

    fun deletePassword(entry: PasswordEntry) {
        viewModelScope.launch {
            repository.deletePassword(entry)
        }
    }

    /**
     * Best Engineering Practice: Drive Sync now uses an internal stable Sync Key derived from 
     * the Recovery Key. The UI no longer needs to provide a password, making it seamless.
     */
    fun performDriveSync(context: Context, account: GoogleSignInAccount, forceOverwrite: Boolean = false, onComplete: (Boolean, String) -> Unit) {
        val key = getVaultKey() ?: return
        runWithBackupLoading {
            backupHelper.performDriveSync(context, account, key, forceOverwrite, onComplete)
        }
    }

    fun restoreFromDrive(context: Context, account: GoogleSignInAccount, overwrite: Boolean = false, onComplete: (Boolean, String) -> Unit) {
        val key = getVaultKey() ?: return
        runWithBackupLoading {
            backupHelper.restoreFromDrive(context, account, key, overwrite, onComplete)
        }
    }

    fun exportVaultManual(context: Context, backupPassword: CharArray, onComplete: (Boolean, String) -> Unit) {
        val key = getVaultKey() ?: return
        runWithBackupLoading {
            backupHelper.exportVaultManual(context, backupPassword, key, onComplete)
        }
    }

    fun importVaultManual(context: Context, uri: Uri, backupPassword: CharArray, overwrite: Boolean = false, onComplete: (Boolean, Int, Int, Int) -> Unit, onError: (String) -> Unit) {
        val key = getVaultKey() ?: return
        runWithBackupLoading {
            backupHelper.importVaultManual(context, uri, backupPassword, key, overwrite, onComplete, onError)
        }
    }

    fun destroyVault(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllData(getApplication())
            prefs.edit().clear().apply()
            lockVault()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun rotateMasterKey(
        oldPin: CharArray,
        newPin: CharArray,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String, CharArray?) -> Unit
    ) {
        viewModelScope.launch {
            val result = migrationManager.rotateMasterKey(oldPin, newPin, onProgress)
            result.onSuccess { recoveryKey ->
                onComplete(true, "Rotation successful.", recoveryKey)
            }.onFailure { error ->
                onComplete(false, "Rotation failed: ${error.message}", null)
            }
        }
    }
}
