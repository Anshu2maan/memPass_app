package com.example.mempass

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharingUtils @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "SharingUtils"

    private fun validateFilePath(path: String): File {
        val file = File(path)
        val canonicalPath = file.canonicalPath
        
        val allowedDirs = listOf(
            context.filesDir.canonicalPath,
            context.cacheDir.canonicalPath,
            context.getExternalFilesDir(null)?.canonicalPath
        ).filterNotNull()

        val isSafe = allowedDirs.any { canonicalPath.startsWith(it) }
        if (!isSafe) {
            throw SecurityException("Security Alert: Unauthorized file access attempt at $path")
        }
        return file
    }

    private fun getCleanDisplayName(displayName: String): String {
        return if (displayName.endsWith(".enc")) displayName.substringBeforeLast(".enc") else displayName
    }

    private fun getMimeType(fileName: String): String {
        val cleanName = getCleanDisplayName(fileName)
        val extension = cleanName.substringAfterLast(".", "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    /**
     * Security Recommendation: Decrypts directly to the destination stream 
     * to avoid leaving unencrypted data in the cache directory.
     */
    fun exportFile(internalPath: String, key: SecretKeySpec, displayName: String) {
        try {
            val sourceFile = validateFilePath(internalPath)
            val cleanName = getCleanDisplayName(displayName)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(cleanName))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MemPass")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        sourceFile.inputStream().use { input ->
                            FileEncryptor.decryptStreamToStream(input, output, key)
                        }
                    }
                    Toast.makeText(context, "Exported to Downloads/MemPass", Toast.LENGTH_SHORT).show()
                } ?: throw Exception("Could not create MediaStore entry")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val memPassDir = File(downloadsDir, "MemPass")
                if (!memPassDir.exists() && !memPassDir.mkdirs()) throw Exception("Could not create directory")
                
                val outputFile = File(memPassDir, cleanName)
                sourceFile.inputStream().use { input ->
                    outputFile.outputStream().use { output ->
                        FileEncryptor.decryptStreamToStream(input, output, key)
                    }
                }
                Toast.makeText(context, "Exported to Downloads/MemPass", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for $displayName", e)
            Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(internalPath: String, key: SecretKeySpec, displayName: String) {
        var tempFile: File? = null
        try {
            val sourceFile = validateFilePath(internalPath)
            val cleanName = getCleanDisplayName(displayName)
            val shareDir = File(context.cacheDir, "shared_files")
            if (!shareDir.exists()) shareDir.mkdirs()

            tempFile = File(shareDir, cleanName)
            FileEncryptor.decryptFileToFile(sourceFile, tempFile, key)
            
            sharePlainFile(tempFile.absolutePath, cleanName)
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
            Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePlainFile(plainPath: String, displayName: String) {
        try {
            val file = validateFilePath(plainPath)
            val contentUri = FileProvider.getUriForFile(context, "com.example.mempass.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(displayName)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
            Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun viewFile(internalPath: String, key: SecretKeySpec, displayName: String) {
        var tempFile: File? = null
        try {
            val sourceFile = validateFilePath(internalPath)
            val cleanName = getCleanDisplayName(displayName)
            val viewDir = File(context.cacheDir, "view_files")
            if (!viewDir.exists()) viewDir.mkdirs()
            
            viewDir.listFiles()?.forEach { it.delete() }
            
            tempFile = File(viewDir, cleanName)
            FileEncryptor.decryptFileToFile(sourceFile, tempFile, key)
            
            val contentUri = FileProvider.getUriForFile(context, "com.example.mempass.fileprovider", tempFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, getMimeType(cleanName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "View failed", e)
            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }
}
