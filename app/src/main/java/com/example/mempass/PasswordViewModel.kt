package com.example.mempass

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class PasswordViewModel @Inject constructor(
    application: Application,
    repository: VaultRepository,
    @Named("SecurityPrefs") prefs: SharedPreferences,
    @Named("HistoryPrefs") historyPrefs: SharedPreferences,
    vaultManager: VaultManager,
    authManager: VaultAuthManager,
    biometricHelper: BiometricHelper,
    cameraHelper: CameraHelper
) : BaseVaultViewModel(application, repository, prefs, historyPrefs, vaultManager, authManager, biometricHelper, cameraHelper) {
    val allPasswords: Flow<List<PasswordEntry>> = repository.allPasswords

    fun savePassword(
        service: String, 
        user: CharArray, 
        pass: CharArray, 
        notes: CharArray, 
        totpSecret: CharArray? = null, 
        associatedPackage: String? = null,
        associatedDomain: String? = null,
        isFavorite: Boolean = false, 
        id: Int = 0
    ) {
        val key = getVaultKey() ?: return
        viewModelScope.launch {
            try {
                val entry = PasswordEntry(
                    id = id, 
                    serviceName = service,
                    encryptedUsername = CryptoUtils.encrypt(user, key),
                    encryptedPassword = CryptoUtils.encrypt(pass, key),
                    encryptedNotes = CryptoUtils.encrypt(notes, key),
                    encryptedTotpSecret = totpSecret?.let { CryptoUtils.encrypt(it, key) },
                    associatedPackageName = associatedPackage,
                    associatedDomain = associatedDomain,
                    isFavorite = isFavorite
                )
                repository.insertPassword(entry)
            } catch (e: Exception) {
                Log.e("PasswordViewModel", "Failed to save password due to encryption error", e)
            }
        }
    }

    fun toggleFavorite(entry: PasswordEntry) {
        viewModelScope.launch { repository.insertPassword(entry.copy(isFavorite = !entry.isFavorite)) }
    }

    fun deletePassword(entry: PasswordEntry) {
        viewModelScope.launch { repository.deletePassword(entry) }
    }
}
