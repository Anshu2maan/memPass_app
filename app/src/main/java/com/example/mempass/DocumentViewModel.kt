package com.example.mempass

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.mempass.ui.components.QualityOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DocumentViewModel @Inject constructor(
    application: Application,
    repository: VaultRepository,
    @Named("SecurityPrefs") prefs: SharedPreferences,
    @Named("HistoryPrefs") historyPrefs: SharedPreferences,
    vaultManager: VaultManager,
    authManager: VaultAuthManager,
    biometricHelper: BiometricHelper,
    cameraHelper: CameraHelper,
    val fileUtils: FileUtils,
    val sharingUtils: SharingUtils,
    val ocrHelper: OcrHelper,
    private val imageCompressor: ImageCompressor,
    private val pdfCompressor: PdfCompressor
) : BaseVaultViewModel(application, repository, prefs, historyPrefs, vaultManager, authManager, biometricHelper, cameraHelper) {

    companion object {
        const val PATH_SEPARATOR = "|"
        
        fun splitPaths(paths: String?): List<String> {
            if (paths.isNullOrEmpty()) return emptyList()
            return paths.split("|", ",").filter { it.isNotBlank() }.map { it.trim() }
        }
    }

    val allDocuments: Flow<List<DocumentEntry>> = repository.allDocuments

    suspend fun generateAndSaveThumbnail(filePath: String): String? {
        val key = getVaultKey() ?: return null
        return try {
            val bitmap = fileUtils.getThumbnail(filePath, 300, 300) ?: return null
            val thumbnailFile = File(getApplication<Application>().filesDir, "thumb_${UUID.randomUUID()}.enc")
            val tempFile = File(getApplication<Application>().cacheDir, "temp_thumb")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
            }
            FileEncryptor.encryptFile(tempFile, thumbnailFile, key)
            if (tempFile.exists()) tempFile.delete()
            thumbnailFile.absolutePath
        } catch (e: Throwable) {
            Log.e("DocumentViewModel", "Thumbnail generation failed", e)
            null
        }
    }

    fun saveDocument(title: String, type: String, fieldsJson: CharArray, notes: CharArray, filePaths: List<String>, expiryDate: Long? = null, id: Int = 0, onComplete: () -> Unit = {}) {
        val key = getVaultKey() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Using NonCancellable to ensure the save process completes even if the ViewModel is cleared
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    var thumbnailPath: String? = null
                    if (id != 0) {
                        val oldDoc = repository.allDocuments.first().find { it.id == id }
                        oldDoc?.let { old ->
                            val oldPaths = splitPaths(old.filePaths).toSet()
                            val newPaths = filePaths.toSet()
                            (oldPaths - newPaths).forEach { fileUtils.deleteFileIfExists(it) }
                            thumbnailPath = old.thumbnailPath
                            if (oldPaths != newPaths) {
                                fileUtils.deleteFileIfExists(old.thumbnailPath ?: "")
                                thumbnailPath = null
                            }
                        }
                    }
                    
                    if (thumbnailPath == null && filePaths.isNotEmpty()) {
                        thumbnailPath = try {
                            generateAndSaveThumbnail(filePaths.first())
                        } catch (e: Exception) {
                            Log.e("DocumentViewModel", "Failed to generate thumbnail during save", e)
                            null
                        }
                    }

                    val doc = DocumentEntry(
                        id = id, title = title, documentType = type,
                        encryptedFields = CryptoUtils.encrypt(fieldsJson, key),
                        encryptedNotes = CryptoUtils.encrypt(notes, key),
                        filePaths = filePaths.joinToString(PATH_SEPARATOR),
                        thumbnailPath = thumbnailPath,
                        expiryDate = expiryDate
                    )
                    
                    repository.insertDocument(doc)
                    
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                }
            } catch (e: CryptographyException) {
                Log.e("DocumentViewModel", "Failed to save document due to crypto error", e)
                emitCryptoError("Failed to save document: ${e.message}")
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Critical error saving document", e)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    fun deleteDocument(entry: DocumentEntry) {
        viewModelScope.launch {
            repository.deleteDocument(entry)
        }
    }
    
    suspend fun saveUriToInternalEncrypted(uri: Uri): String? {
        val key = getVaultKey() ?: return null
        val file = fileUtils.uriToFile(uri)
        val destination = File(getApplication<Application>().filesDir, "doc_${UUID.randomUUID()}.enc")
        FileEncryptor.encryptFile(file, destination, key)
        if (file.exists()) file.delete()
        return destination.absolutePath
    }

    fun shareDocument(filePath: String, displayName: String, quality: QualityOption) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val key = getVaultKey() ?: return@launch
            
            val originalEncryptedFile = File(filePath)
            if (!originalEncryptedFile.exists()) return@launch

            withContext(Dispatchers.IO) {
                val fileName = filePath.substringAfterLast("/")
                val nameWithoutEnc = if (fileName.endsWith(".enc")) fileName.substringBeforeLast(".enc") else fileName
                val originalExtension = nameWithoutEnc.substringAfterLast(".", "").lowercase()
                
                val suffix = if (originalExtension.isNotEmpty()) ".$originalExtension" else ""
                
                val tempDecrypted = File(context.cacheDir, "temp_decrypted_${System.currentTimeMillis()}_$displayName$suffix")
                try {
                    FileEncryptor.decryptFileToFile(originalEncryptedFile, tempDecrypted, key)

                    var finalFileToShare = tempDecrypted
                    var finalExtension = if (originalExtension.isEmpty()) "bin" else originalExtension

                    if (quality.sizeKb != null) {
                        val isPdf = originalExtension == "pdf"
                        val exportExtension = if (isPdf) "pdf" else "jpg"
                        val compressedFile = File(context.cacheDir, "export_${quality.sizeKb}kb_${System.currentTimeMillis()}_$displayName.$exportExtension")
                        
                        val success = if (isPdf) {
                            pdfCompressor.compressPdfToTargetSize(tempDecrypted, compressedFile, quality.sizeKb)
                        } else {
                            imageCompressor.compressImageToTargetSize(tempDecrypted, compressedFile, quality.sizeKb)
                        }
                        
                        if (success) {
                            finalFileToShare = compressedFile
                            finalExtension = exportExtension
                        }
                    }

                    val sharingDisplayName = if (displayName.lowercase().endsWith(".$finalExtension")) {
                        displayName
                    } else {
                        "$displayName.$finalExtension"
                    }

                    sharingUtils.shareFile(finalFileToShare.absolutePath, key, sharingDisplayName)
                    
                } catch (e: Exception) {
                    Log.e("DocumentViewModel", "Share failed", e)
                    emitCryptoError("Share failed: Data might be corrupted")
                }
            }
        }
    }
}
