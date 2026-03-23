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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _isOcrProcessing = MutableStateFlow(false)
    val isOcrProcessing: StateFlow<Boolean> = _isOcrProcessing

    private val _ocrResults = MutableStateFlow<Map<String, String>?>(null)
    val ocrResults: StateFlow<Map<String, String>?> = _ocrResults

    fun clearOcrResults() {
        _ocrResults.value = null
    }

    fun performOcrOnFiles(filePaths: List<String>) {
        val key = getVaultKey() ?: return
        viewModelScope.launch {
            _isOcrProcessing.value = true
            try {
                val results = ocrHelper.extractMergedInfo(filePaths, key)
                _ocrResults.value = results
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "OCR failed", e)
                emitCryptoError("OCR failed: ${e.message}")
            } finally {
                _isOcrProcessing.value = false
            }
        }
    }

    private suspend fun generateAndSaveThumbnailFromPlainFile(plainFile: File): String? {
        val key = getVaultKey() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = ImageUtils.decodeSampledBitmap(plainFile.absolutePath, 300, 300) ?: return@withContext null
                val thumbnailFile = File(getApplication<Application>().filesDir, "thumb_${UUID.randomUUID()}.enc")
                val tempThumb = File(getApplication<Application>().cacheDir, "temp_thumb_${UUID.randomUUID()}")
                
                FileOutputStream(tempThumb).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }
                
                FileEncryptor.encryptFile(tempThumb, thumbnailFile, key)
                if (tempThumb.exists()) tempThumb.delete()
                thumbnailFile.absolutePath
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Thumbnail generation failed", e)
                null
            }
        }
    }

    suspend fun getDecryptedThumbnail(path: String): android.graphics.Bitmap? {
        val key = getVaultKey() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val encryptedFile = File(path)
                if (!encryptedFile.exists()) return@withContext null
                
                val decryptedBytes = FileEncryptor.decryptFile(encryptedFile, key)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                
                // Security: Wipe decrypted bytes from memory
                java.util.Arrays.fill(decryptedBytes, 0.toByte())
                
                bitmap
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Failed to decrypt thumbnail", e)
                null
            }
        }
    }

    fun saveDocument(title: String, type: String, fieldsJson: CharArray, notes: CharArray, filePaths: List<String>, expiryDate: Long? = null, isFavorite: Boolean = false, id: Int = 0, onComplete: () -> Unit = {}) {
        val key = getVaultKey() ?: run {
            onComplete() 
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    var thumbnailPath: String? = null
                    val allDocs = repository.allDocuments.first()
                    
                    if (id != 0) {
                        val oldDoc = allDocs.find { it.id == id }
                        oldDoc?.let { old ->
                            val oldPaths = splitPaths(old.filePaths).toSet()
                            val newPaths = filePaths.toSet()
                            
                            (oldPaths - newPaths).forEach { fileUtils.deleteFileIfExists(it) }
                            
                            thumbnailPath = old.thumbnailPath
                            // If first file changed, we might need a new thumbnail
                            if (filePaths.firstOrNull() != oldPaths.firstOrNull()) {
                                // Handled below by checking if thumbnailPath is still null
                                if (old.thumbnailPath != null) {
                                    fileUtils.deleteFileIfExists(old.thumbnailPath)
                                    thumbnailPath = null
                                }
                            }
                        }
                    }
                    
                    // Note: If thumbnail is null, it will be generated in saveUriToInternalEncrypted 
                    // or we could trigger a re-gen here if needed from encrypted source (less efficient)
                    // But usually saveUriToInternalEncrypted handles the new file imports.

                    val doc = DocumentEntry(
                        id = id, title = title, documentType = type,
                        encryptedFields = CryptoUtils.encrypt(fieldsJson, key),
                        encryptedNotes = CryptoUtils.encrypt(notes, key),
                        filePaths = filePaths.joinToString(PATH_SEPARATOR),
                        thumbnailPath = thumbnailPath,
                        expiryDate = expiryDate,
                        isFavorite = isFavorite
                    )
                    
                    repository.insertDocument(doc)
                    
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Critical error saving document", e)
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun toggleFavorite(entry: DocumentEntry) {
        viewModelScope.launch { repository.insertDocument(entry.copy(isFavorite = !entry.isFavorite)) }
    }

    fun deleteDocument(entry: DocumentEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            splitPaths(entry.filePaths).forEach { fileUtils.deleteFileIfExists(it) }
            fileUtils.deleteFileIfExists(entry.thumbnailPath ?: "")
            repository.deleteDocument(entry)
        }
    }
    
    suspend fun saveUriToInternalEncrypted(uri: Uri, quality: QualityOption = QualityOption.Original): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val key = getVaultKey() ?: return@withContext null
        val context = getApplication<Application>()
        
        // 1. Copy Uri to Temp
        val tempFile = fileUtils.uriToFile(uri)
        var fileToEncrypt = tempFile
        val extension = tempFile.extension.lowercase()

        // 2. Generate Thumbnail while file is plain-text (Most Efficient)
        val thumbnailPath = if (extension != "pdf") {
            generateAndSaveThumbnailFromPlainFile(tempFile)
        } else null

        // 3. Optional Compression
        if (quality.sizeKb != null) {
            val compressedFile = File(context.cacheDir, "save_comp_${UUID.randomUUID()}.$extension")
            val success = if (extension == "pdf") {
                pdfCompressor.compressPdfToTargetSize(tempFile, compressedFile, quality.sizeKb)
            } else if (extension == "jpg" || extension == "jpeg" || extension == "png") {
                imageCompressor.compressImageToTargetSize(tempFile, compressedFile, quality.sizeKb)
            } else false
            
            if (success) {
                fileToEncrypt = compressedFile
            }
        }

        // 4. Encrypt
        val destination = File(context.filesDir, "doc_${UUID.randomUUID()}.$extension.enc")
        try {
            FileEncryptor.encryptFile(fileToEncrypt, destination, key)
            Pair(destination.absolutePath, thumbnailPath)
        } catch (e: Exception) {
            Log.e("DocumentViewModel", "Encryption failed", e)
            null
        } finally {
            if (tempFile.exists()) tempFile.delete()
            if (fileToEncrypt != tempFile && fileToEncrypt.exists()) fileToEncrypt.delete()
        }
    }

    fun deleteOrphanedFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            fileUtils.deleteFileIfExists(path)
        }
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
                
                val tempDecrypted = File(context.cacheDir, "temp_share_${System.currentTimeMillis()}_$displayName$suffix")
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
                    
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(60000)
                        if (tempDecrypted.exists()) tempDecrypted.delete()
                        if (finalFileToShare != tempDecrypted && finalFileToShare.exists()) finalFileToShare.delete()
                    }
                    
                } catch (e: Exception) {
                    Log.e("DocumentViewModel", "Share failed", e)
                    emitCryptoError("Share failed: Data might be corrupted")
                }
            }
        }
    }
}
