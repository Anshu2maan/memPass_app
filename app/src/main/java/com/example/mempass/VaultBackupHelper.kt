package com.example.mempass

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.example.mempass.common.Constants
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.Drive
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
    @Named("SecurityPrefs") private val prefs: SharedPreferences,
    @Named("HistoryPrefs") private val historyPrefs: SharedPreferences
) {
    private val TAG = "VaultBackupHelper"
    private val METADATA_NAME = "vault_backup.mempass"

    suspend fun performDriveSync(
        context: Context,
        account: GoogleSignInAccount,
        key: SecretKeySpec,
        backupPassword: CharArray,
        forceOverwrite: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val driveService = driveHelper.getDriveService(account)
            
            val localPasswords = repository.allPasswords.first()
            val localDocs = repository.allDocuments.first()
            val localNotes = repository.allNotes.first()

            val tempFile = File(context.cacheDir, METADATA_NAME)
            val outputStream = FileOutputStream(tempFile)
            backupManager.exportToBackup(localPasswords, localDocs, localNotes, backupPassword, outputStream)
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
        backupPassword: CharArray,
        overwrite: Boolean = false,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val driveService = driveHelper.getDriveService(account)

            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$METADATA_NAME'")
                .execute()
            
            if (fileList.files.isEmpty()) {
                withContext(Dispatchers.Main) { onComplete(false, "No backup found") }
                return@withContext
            }

            val tempFile = File(context.cacheDir, "res_$METADATA_NAME")
            driveHelper.downloadFile(driveService, fileList.files[0].id, tempFile)
            
            val inputStream = FileInputStream(tempFile)
            val (passwords, documents, notes) = backupManager.importFromBackup(inputStream, backupPassword)
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
                sharingUtils.exportFile(tempFile.absolutePath, key, fileName)
                onComplete(true, "Backup saved")
            }
        } catch (e: Exception) {
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
            withContext(Dispatchers.Main) { onError("Import Failed: ${e.localizedMessage}") }
        }
    }
}
