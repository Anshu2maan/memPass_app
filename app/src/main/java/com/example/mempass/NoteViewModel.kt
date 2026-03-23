package com.example.mempass

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class NoteViewModel @Inject constructor(
    application: Application,
    repository: VaultRepository,
    @Named("SecurityPrefs") prefs: SharedPreferences,
    @Named("HistoryPrefs") historyPrefs: SharedPreferences,
    vaultManager: VaultManager,
    authManager: VaultAuthManager,
    biometricHelper: BiometricHelper,
    cameraHelper: CameraHelper,
    val fileUtils: FileUtils,
    val sharingUtils: SharingUtils
) : BaseVaultViewModel(application, repository, prefs, historyPrefs, vaultManager, authManager, biometricHelper, cameraHelper) {

    companion object {
        const val PATH_SEPARATOR = "|"
    }

    val allNotes: Flow<List<NoteEntry>> = repository.allNotes.map { notes ->
        val currentTime = System.currentTimeMillis()
        val (expired, valid) = notes.partition { it.selfDestructAt != null && it.selfDestructAt!! <= currentTime }
        
        if (expired.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                expired.forEach { deleteNote(it) }
            }
        }
        valid
    }
    
    private val _unlockedNoteIds = MutableStateFlow<Set<Int>>(emptySet())
    val unlockedNoteIds: StateFlow<Set<Int>> = _unlockedNoteIds

    override fun lockVault() {
        super.lockVault()
        _unlockedNoteIds.value = emptySet()
    }

    fun markNoteAsUnlocked(id: Int) {
        _unlockedNoteIds.value = _unlockedNoteIds.value + id
    }

    fun isNoteUnlockedInSession(id: Int): Boolean {
        return _unlockedNoteIds.value.contains(id)
    }

    fun saveNote(
        title: String, 
        content: CharArray, 
        category: String, 
        colorHex: String, 
        isChecklist: Boolean = false, 
        tags: String = "", 
        snippetFilePaths: List<String> = emptyList(), 
        selfDestructAt: Long? = null, 
        isLocked: Boolean = false,
        isFavorite: Boolean = false,
        id: Int = 0,
        onComplete: () -> Unit = {}
    ) {
        val key = getVaultKey() ?: return
        
        // SECURITY FIX: Encrypt synchronously BEFORE the coroutine starts.
        // This prevents a race condition where the UI wipes the source CharArrays 
        // before the background thread can encrypt them.
        val encryptedContent = try {
            CryptoUtils.encrypt(content, key)
        } catch (e: Exception) {
            Log.e("NoteViewModel", "Encryption failed", e)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var existingRemoteId: String? = null
                if (id != 0) {
                    val oldNote = repository.allNotes.first().find { it.id == id }
                    oldNote?.let { old ->
                        existingRemoteId = old.remoteId
                        val oldPaths = old.snippetFilePaths.split(PATH_SEPARATOR).filter { it.isNotEmpty() }.toSet()
                        val newPaths = snippetFilePaths.toSet()
                        (oldPaths - newPaths).forEach { fileUtils.deleteFileIfExists(it) }
                    }
                }
                val cleanedTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(",")
                val noteEntry = NoteEntry(
                    id = id, remoteId = existingRemoteId ?: UUID.randomUUID().toString(),
                    title = title.trim(), encryptedContent = encryptedContent,
                    category = category, colorHex = colorHex, isChecklist = isChecklist,
                    tags = cleanedTags, snippetFilePaths = snippetFilePaths.joinToString(PATH_SEPARATOR),
                    selfDestructAt = selfDestructAt, isLocked = isLocked, isFavorite = isFavorite
                )
                repository.insertNote(noteEntry)

                val workManager = WorkManager.getInstance(getApplication())
                val uniqueWorkName = "self_destruct_${noteEntry.remoteId}"
                if (selfDestructAt != null) {
                    val delay = selfDestructAt - System.currentTimeMillis()
                    if (delay > 0) {
                        val workRequest = OneTimeWorkRequestBuilder<NoteSelfDestructWorker>()
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag("self_destruct").build()
                        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequest)
                    } else { deleteNote(noteEntry) }
                } else { workManager.cancelUniqueWork(uniqueWorkName) }
                
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                Log.e("NoteViewModel", "Failed to save note", e)
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun toggleFavorite(entry: NoteEntry) {
        viewModelScope.launch { repository.insertNote(entry.copy(isFavorite = !entry.isFavorite)) }
    }

    fun deleteNote(entry: NoteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            entry.snippetFilePaths.split(PATH_SEPARATOR).filter { it.isNotEmpty() }.forEach { fileUtils.deleteFileIfExists(it) }
            WorkManager.getInstance(getApplication()).cancelUniqueWork("self_destruct_${entry.remoteId}")
            repository.deleteNote(entry)
        }
    }

    fun deleteOrphanedFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            fileUtils.deleteFileIfExists(path)
        }
    }
    
    suspend fun uriToFile(uri: Uri): File {
        return fileUtils.uriToFile(uri)
    }
    
    suspend fun encryptAndSave(file: File, fileName: String): String {
        val key = getVaultKey() ?: return ""
        val destination = File(getApplication<Application>().filesDir, "$fileName.enc")
        FileEncryptor.encryptFile(file, destination, key)
        if (file.exists()) file.delete()
        return destination.absolutePath
    }
}
