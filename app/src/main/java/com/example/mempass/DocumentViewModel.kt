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
import java.io.RandomAccessFile
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

    init {
        clearOldTemporaryFiles()
    }

    private fun clearOldTemporaryFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.listFiles { file -> 
                file.name.startsWith("temp_share_") || file.name.startsWith("export_") || file.name.startsWith("temp_thumb_")
            }?.forEach { it.delete() }
        }
    }

    private fun wipeFile(file: File) {
        try {
            if (file.exists()) {
                val raf = RandomAccessFile(file, "rws")
                val length = raf.length()
                val blank = ByteArray(8192)
                var written: Long = 0
                while (written < length) {
                    val toWrite = if (length - written < 8192) (length - written).toInt() else 8192
                    raf.write(blank, 0, toWrite)
                    written += toWrite
                }
                raf.close()
                file.delete()
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

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
                wipeFile(tempThumb)
                thumbnailFile.absolutePath
            } catch (e: Exception) {
                Log.e("DocumentViewModel", "Thumbnail generation failed", e)
                null
            }
        }
    }

    private val thumbnailCache = mutableMapOf<String, android.graphics.Bitmap>()

    suspend fun getDecryptedThumbnail(path: String): android.graphics.Bitmap? {
        thumbnailCache[path]?.let { return it }
        val key = getVaultKey() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val encryptedFile = File(path)
                if (!encryptedFile.exists()) return@withContext null
                
                val decryptedBytes = FileEncryptor.decryptFile(encryptedFile, key)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size)
                
                java.util.Arrays.fill(decryptedBytes, 0.toByte())
                
                if (bitmap != null) {
                    thumbnailCache[path] = bitmap
                }
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
        
        val encFields = CryptoUtils.encrypt(fieldsJson, key)
        val encNotes = CryptoUtils.encrypt(notes, key)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    var currentThumbnailPath: String? = null
                    val allDocs = repository.allDocuments.first()
                    
                    if (id != 0) {
                        val oldDoc = allDocs.find { it.id == id }
                        oldDoc?.let { old ->
                            val oldPaths = splitPaths(old.filePaths)
                            val newPaths = filePaths
                            
                            val deletedPaths = oldPaths.toSet() - newPaths.toSet()
                            deletedPaths.forEach { fileUtils.deleteFileIfExists(it) }
                            
                            currentThumbnailPath = old.thumbnailPath
                            
                            // Fix 3: Handle thumbnail regeneration if first file changed
                            if (newPaths.isNotEmpty() && (oldPaths.isEmpty() || newPaths[0] != oldPaths[0])) {
                                if (old.thumbnailPath != null) {
                                    fileUtils.deleteFileIfExists(old.thumbnailPath)
                                    thumbnailCache.remove(old.thumbnailPath)
                                }
                                
                                // Generate new thumbnail from the new first file
                                val firstFile = File(newPaths[0])
                                if (firstFile.exists()) {
                                    val tempDecrypted = File(getApplication<Application>().cacheDir, "temp_thumb_gen_${UUID.randomUUID()}")
                                    try {
                                        FileEncryptor.decryptFileToFile(firstFile, tempDecrypted, key)
                                        currentThumbnailPath = generateAndSaveThumbnailFromPlainFile(tempDecrypted)
                                    } finally {
                                        wipeFile(tempDecrypted)
                                    }
                                } else {
                                    currentThumbnailPath = null
                                }
                            }
                        }
                    } else if (filePaths.isNotEmpty()) {
                        // For new documents, saveUriToInternalEncrypted already handles the thumbnail
                        // But if filePaths was manually constructed, we might need to handle it.
                        // However, standard flow uses saveUriToInternalEncrypted.
                    }

                    val doc = DocumentEntry(
                        id = id, title = title, documentType = type,
                        encryptedFields = encFields,
                        encryptedNotes = encNotes,
                        filePaths = filePaths.joinToString(PATH_SEPARATOR),
                        thumbnailPath = currentThumbnailPath,
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

    fun toggleFavoriteForDocuments(entries: List<DocumentEntry>, favorite: Boolean) {
        viewModelScope.launch {
            entries.forEach { entry ->
                repository.insertDocument(entry.copy(isFavorite = favorite))
            }
        }
    }

    fun deleteDocument(entry: DocumentEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            splitPaths(entry.filePaths).forEach { fileUtils.deleteFileIfExists(it) }
            if (entry.thumbnailPath != null) {
                fileUtils.deleteFileIfExists(entry.thumbnailPath)
                thumbnailCache.remove(entry.thumbnailPath)
            }
            repository.deleteDocument(entry)
        }
    }

    fun deleteDocuments(entries: List<DocumentEntry>) {
        viewModelScope.launch(Dispatchers.IO) {
            entries.forEach { entry ->
                splitPaths(entry.filePaths).forEach { fileUtils.deleteFileIfExists(it) }
                if (entry.thumbnailPath != null) {
                    fileUtils.deleteFileIfExists(entry.thumbnailPath)
                    thumbnailCache.remove(entry.thumbnailPath)
                }
                repository.deleteDocument(entry)
            }
        }
    }
    
    suspend fun saveUriToInternalEncrypted(uri: Uri, quality: QualityOption = QualityOption.Original): Pair<String, String?>? = withContext(Dispatchers.IO) {
        val key = getVaultKey() ?: return@withContext null
        val context = getApplication<Application>()
        
        val tempFile = fileUtils.uriToFile(uri)
        var fileToEncrypt = tempFile
        val extension = tempFile.extension.lowercase()

        val thumbnailPath = if (extension != "pdf") {
            generateAndSaveThumbnailFromPlainFile(tempFile)
        } else null

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

        val destination = File(context.filesDir, "doc_${UUID.randomUUID()}.$extension.enc")
        try {
            FileEncryptor.encryptFile(fileToEncrypt, destination, key)
            Pair(destination.absolutePath, thumbnailPath)
        } catch (e: Exception) {
            Log.e("DocumentViewModel", "Encryption failed", e)
            null
        } finally {
            if (tempFile.exists()) wipeFile(tempFile)
            if (fileToEncrypt != tempFile && fileToEncrypt.exists()) wipeFile(fileToEncrypt)
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

                    sharingUtils.sharePlainFile(finalFileToShare.absolutePath, sharingDisplayName)
                    
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(60000)
                        wipeFile(tempDecrypted)
                        if (finalFileToShare != tempDecrypted && finalFileToShare.exists()) wipeFile(finalFileToShare)
                    }
                    
                } catch (e: Exception) {
                    Log.e("DocumentViewModel", "Share failed", e)
                    emitCryptoError("Share failed: Data might be corrupted")
                }
            }
        }
    }
}
