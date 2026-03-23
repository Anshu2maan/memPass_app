package com.example.mempass

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.webkit.MimeTypeMap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUtils @Inject constructor(@ApplicationContext private val context: Context) : ComponentCallbacks2 {
    private val TAG = "FileUtils"

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 
    
    private val thumbnailCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() { thumbnailCache.evictAll() }
    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            thumbnailCache.evictAll()
        }
    }

    /**
     * Now supports Decryption on the fly for thumbnails.
     */
    fun getThumbnail(filePath: String, reqWidth: Int, reqHeight: Int, vaultKey: SecretKeySpec? = null): Bitmap? {
        val cacheKey = "${filePath}_${reqWidth}_$reqHeight"
        thumbnailCache.get(cacheKey)?.let { return it }

        val file = File(filePath)
        if (!file.exists()) return null

        return try {
            val bitmap = if (vaultKey != null && filePath.endsWith(".enc")) {
                // If encrypted, use our specific decrypting decoder
                ImageUtils.decodeSampledBitmapFromEncryptedFile(file, vaultKey, reqWidth, reqHeight)
            } else if (filePath.lowercase().endsWith(".pdf")) {
                ImageUtils.renderPdfPageToBitmap(file, reqWidth, reqHeight, 0)
            } else {
                ImageUtils.decodeSampledBitmap(file.absolutePath, reqWidth, reqHeight)
            }

            if (bitmap != null) {
                thumbnailCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail generation failed", e)
            null
        }
    }

    fun deleteFileIfExists(path: String) {
        try {
            val file = File(path)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
        }
    }

    suspend fun uriToFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open input stream for URI: $uri")
        
        val extension = getFileExtension(uri) ?: ""
        val suffix = if (extension.isNotEmpty()) ".$extension" else ""
        val tempFile = File(context.cacheDir, "temp_${UUID.randomUUID()}$suffix")
        
        try {
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
        
        tempFile
    }

    private fun getFileExtension(uri: Uri): String? {
        return if (uri.scheme == "content") {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
        } else {
            MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(uri.path ?: "")).toString())
        }
    }
}
