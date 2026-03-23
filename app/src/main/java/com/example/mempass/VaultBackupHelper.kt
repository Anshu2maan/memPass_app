package com.example.mempass

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.mempass.common.Constants
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VaultBackupHelper @Inject constructor(
    private val application: Application,
    private val repository: VaultRepository,
    private val driveHelper: DriveHelper,
    private val sharingUtils: SharingUtils,
    private val backupManager: BackupManager,
    private val authManager: VaultAuthManager,
    @Named("SecurityPrefs") private val prefs: SharedPreferences,
    @Named("HistoryPrefs") private val historyPrefs: SharedPreferences
) {
    private val TAG = "VaultBackupHelper"
    private val METADATA_NAME = "vault_backup.mempass"

    /**
     * Derives a consistent password for Cloud Sync using the Recovery Key.
     * This ensures that sync remains functional even if the user changes their PIN.
     */
    private fun getSyncPassword(): CharArray {
        return authManager.getCurrentRecoveryKey() 
            ?: throw IllegalStateException("Recovery Key not available. Please unlock vault first.")
    }

    suspend fun performDriveSync(
        context: Context,
        account: GoogleSignInAccount,
        key: SecretKeySpec,
        forceOverwrite: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val driveService = driveHelper.getDriveService(account)
            val syncPassword = getSyncPassword()
            
            val localPasswords = repository.allPasswords.first()
            val localDocs = repository.allDocuments.first()
            val localNotes = repository.allNotes.first()

            val tempFile = File(context.cacheDir, METADATA_NAME)
            val outputStream = FileOutputStream(tempFile)
            backupManager.exportToBackup(localPasswords, localDocs, localNotes, syncPassword, outputStream)
            outputStream.close()

            driveHelper.uploadFile(driveService, tempFile, METADATA_NAME, "appDataFolder")
            tempFile.delete()

            prefs.edit().apply {
                putLong(Constants.KEY_LAST_BACKUP_TIME, System.currentTimeMillis())
                apply()
            }
            withContext(Dispatchers.Main) { 
                onComplete(true, "Sync complete!") 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            withContext(Dispatchers.Main) { onComplete(false, "Sync failed: ${e.localizedMessage}") }
        }
    }

    suspend fun restoreFromDrive(
        context: Context,
        account: GoogleSignInAccount,
        key: SecretKeySpec,
        overwrite: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val driveService = driveHelper.getDriveService(account)
            val syncPassword = getSyncPassword()

            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$METADATA_NAME'")
                .execute()
            
            if (fileList.files.isEmpty()) {
                withContext(Dispatchers.Main) { onComplete(false, "No backup found on Drive") }
                return@withContext
            }

            val tempFile = File(context.cacheDir, "res_$METADATA_NAME")
            driveHelper.downloadFile(driveService, fileList.files[0].id, tempFile)
            
            val inputStream = FileInputStream(tempFile)
            val (passwords, documents, notes) = backupManager.importFromBackup(inputStream, syncPassword)
            inputStream.close()
            tempFile.delete()

            if (overwrite) repository.clearAllData(context)
            repository.upsertPasswords(passwords)
            repository.upsertDocuments(documents)
            repository.upsertNotes(notes)

            withContext(Dispatchers.Main) {
                onComplete(true, "Restore successful!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            withContext(Dispatchers.Main) { onComplete(false, "Restore failed: ${e.localizedMessage}") }
        }
    }

    suspend fun exportVaultManual(context: Context, backupPassword: CharArray, key: SecretKeySpec, onComplete: (Boolean, String) -> Unit) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "MemPass_Backup_$timeStamp.mempass"
            val tempFile = File(context.cacheDir, fileName)
            
            val outputStream = FileOutputStream(tempFile)
            backupManager.exportToBackup(
                repository.allPasswords.first(),
                repository.allDocuments.first(),
                repository.allNotes.first(),
                backupPassword,
                outputStream
            )
            outputStream.close()

            val logs = historyPrefs.getStringSet("export_logs", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            logs.add("$fileName | $timeStamp")
            historyPrefs.edit().putStringSet("export_logs", logs).apply()
            
            withContext(Dispatchers.Main) {
                // Best Engineering Practice: Raw copy for already encrypted manual backups
                sharingUtils.exportRawFile(tempFile.absolutePath, fileName)
                onComplete(true, "Backup saved to Downloads/MemPass")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual export failed", e)
            withContext(Dispatchers.Main) { onComplete(false, "Export Failed") }
        }
    }

    suspend fun importVaultManual(context: Context, uri: Uri, backupPassword: CharArray, key: SecretKeySpec, overwrite: Boolean = false, onComplete: (Boolean, Int, Int, Int) -> Unit, onError: (String) -> Unit) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Cannot open file")
            val (passwords, documents, notes) = backupManager.importFromBackup(inputStream, backupPassword)
            inputStream.close()

            if (overwrite) repository.clearAllData(context)
            repository.upsertPasswords(passwords)
            repository.upsertDocuments(documents)
            repository.upsertNotes(notes)
            
            withContext(Dispatchers.Main) {
                onComplete(true, passwords.size, documents.size, notes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Manual import failed", e)
            withContext(Dispatchers.Main) { onError("Import Failed: ${e.localizedMessage}") }
        }
    }
}
