package com.example.mempass

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveHelper @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "DriveHelper"
    private val METADATA_FILE = "vault_metadata.bin"
    private val ATTACHMENTS_FOLDER = "attachments"

    fun getSignInIntent() = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
    ).signInIntent

    fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = account.account
        }

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory(),
            credential
        ).setApplicationName("MemPass").build()
    }

    /**
     * Incremental Sync: Get or Create Folder in AppData
     */
    suspend fun getOrCreateFolder(driveService: Drive, folderName: String, parentId: String = "appDataFolder"): String = withContext(Dispatchers.IO) {
        val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and '$parentId' in parents and trashed = false"
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .execute()

        if (result.files.isNotEmpty()) {
            return@withContext result.files[0].id
        }

        val metadata = File().apply {
            name = folderName
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf(parentId)
        }
        val folder = driveService.files().create(metadata).setFields("id").execute()
        folder.id
    }

    /**
     * Incremental Sync: List all files in a specific folder
     */
    suspend fun listFiles(driveService: Drive, folderId: String): List<File> = withContext(Dispatchers.IO) {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("'$folderId' in parents and trashed = false")
            .setFields("files(id, name, md5Checksum, size, modifiedTime)")
            .execute()
        result.files ?: emptyList()
    }

    /**
     * Incremental Sync: Upload or Update a file
     */
    suspend fun uploadFile(driveService: Drive, localFile: java.io.File, remoteName: String, parentId: String): String = withContext(Dispatchers.IO) {
        val content = FileContent("application/octet-stream", localFile)
        val query = "name = '$remoteName' and '$parentId' in parents and trashed = false"
        val existingFiles = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .execute()

        if (existingFiles.files.isNotEmpty()) {
            val fileId = existingFiles.files[0].id
            driveService.files().update(fileId, null, content).execute()
            return@withContext fileId
        } else {
            val metadata = File().apply {
                name = remoteName
                parents = listOf(parentId)
            }
            val uploadedFile = driveService.files().create(metadata, content).setFields("id").execute()
            return@withContext uploadedFile.id
        }
    }

    /**
     * Incremental Sync: Download a specific file
     */
    suspend fun downloadFile(driveService: Drive, fileId: String, destination: java.io.File) = withContext(Dispatchers.IO) {
        FileOutputStream(destination).use { output ->
            driveService.files().get(fileId).executeMediaAndDownloadTo(output)
        }
    }

    // --- Legacy Methods (Kept for compatibility during migration) ---

    fun uploadBackupFile(driveService: Drive, localFile: java.io.File) {
        try {
            val content = FileContent("application/octet-stream", localFile)
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'backup.mempass'")
                .execute()
            
            val files = fileList.files
            if (files != null && files.isNotEmpty()) {
                driveService.files().update(files[0].id, null, content).execute()
            } else {
                val metadata = File().apply {
                    name = "backup.mempass"
                    parents = Collections.singletonList("appDataFolder")
                }
                driveService.files().create(metadata, content).execute()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload to Drive", e)
            throw e
        }
    }

    fun downloadBackupFileToTemp(driveService: Drive): Uri? {
        return try {
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'backup.mempass'")
                .execute()
            val files = fileList.files
            if (files == null || files.isEmpty()) return null
            val tempFile = java.io.File(context.cacheDir, "drive_backup_restore.mempass")
            FileOutputStream(tempFile).use { outputStream ->
                driveService.files().get(files[0].id).executeMediaAndDownloadTo(outputStream)
            }
            FileProvider.getUriForFile(context, "com.example.mempass.fileprovider", tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from Drive", e)
            null
        }
    }
}
