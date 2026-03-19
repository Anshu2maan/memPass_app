package com.example.mempass

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.mempass.common.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val dao: VaultDao,
    @Named("SecurityPrefs") private val prefs: SharedPreferences,
    private val context: Context // Injected context for path validation
) {
    private val TAG = "VaultRepository"

    val allPasswords: Flow<List<PasswordEntry>> = dao.getAllPasswords()
    val allDocuments: Flow<List<DocumentEntry>> = dao.getAllDocuments()
    val allNotes: Flow<List<NoteEntry>> = dao.getAllNotes()

    /**
     * Security check to prevent Directory Traversal attacks.
     * Ensures the file being accessed is within the app's private directories.
     */
    private fun isPathSafe(path: String): Boolean {
        return try {
            val file = File(path)
            val canonicalPath = file.canonicalPath
            val allowedDirs = listOf(
                context.filesDir.canonicalPath,
                context.cacheDir.canonicalPath
            )
            allowedDirs.any { canonicalPath.startsWith(it) }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun searchPasswords(query: String): List<PasswordEntry> {
        return dao.searchPasswords(query)
    }

    private fun markDirty() {
        prefs.edit().putBoolean(Constants.KEY_IS_VAULT_DIRTY, true).apply()
    }

    suspend fun insertPassword(entry: PasswordEntry) {
        dao.insertPassword(entry)
        markDirty()
    }

    suspend fun deletePassword(entry: PasswordEntry) {
        dao.deletePassword(entry)
        markDirty()
    }

    suspend fun insertDocument(entry: DocumentEntry) {
        dao.insertDocument(entry)
        markDirty()
    }
    
    suspend fun deleteDocument(entry: DocumentEntry) {
        dao.deleteDocument(entry)
        markDirty()
        val files = DocumentViewModel.splitPaths(entry.filePaths).toMutableList()
        entry.thumbnailPath?.let { files.add(it) }
        
        files.forEach { path ->
            try {
                if (isPathSafe(path)) {
                    val file = File(path)
                    if (file.exists() && !file.delete()) {
                        Log.w(TAG, "Failed to delete file")
                    }
                } else {
                    Log.e(TAG, "Security Alert: Blocked deletion of unauthorized path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting file", e)
            }
        }
    }

    suspend fun insertNote(entry: NoteEntry) {
        dao.insertNote(entry)
        markDirty()
    }

    suspend fun deleteNote(entry: NoteEntry) {
        dao.deleteNote(entry)
        markDirty()
        DocumentViewModel.splitPaths(entry.snippetFilePaths).forEach { path ->
            try {
                if (isPathSafe(path)) {
                    val file = File(path)
                    if (file.exists() && !file.delete()) {
                        Log.w(TAG, "Failed to delete snippet file")
                    }
                } else {
                    Log.e(TAG, "Security Alert: Blocked deletion of unauthorized path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting snippet file", e)
            }
        }
    }

    suspend fun upsertPasswords(incoming: List<PasswordEntry>) {
        if (incoming.isEmpty()) return
        val existing = allPasswords.first().associateBy { it.remoteId }
        val toInsert = incoming.map { item ->
            val existingItem = existing[item.remoteId]
            item.copy(id = existingItem?.id ?: 0)
        }
        dao.upsertPasswords(toInsert)
    }

    suspend fun upsertDocuments(incoming: List<DocumentEntry>) {
        if (incoming.isEmpty()) return
        val existing = allDocuments.first().associateBy { it.remoteId }
        val toInsert = incoming.map { item ->
            val existingItem = existing[item.remoteId]
            item.copy(id = existingItem?.id ?: 0)
        }
        dao.upsertDocuments(toInsert)
    }

    suspend fun upsertNotes(incoming: List<NoteEntry>) {
        if (incoming.isEmpty()) return
        val existing = allNotes.first().associateBy { it.remoteId }
        val toInsert = incoming.map { item ->
            val existingItem = existing[item.remoteId]
            item.copy(id = existingItem?.id ?: 0)
        }
        dao.upsertNotes(toInsert)
    }

    suspend fun migrateVaultData(
        passwords: List<PasswordEntry>,
        documents: List<DocumentEntry>,
        notes: List<NoteEntry>
    ) {
        dao.migrateAllData(passwords, documents, notes)
        markDirty()
    }

    suspend fun clearAllData(context: Context) {
        context.filesDir.listFiles()?.forEach { it.delete() }
        dao.clearPasswords()
        dao.clearDocuments()
        dao.clearNotes()
        prefs.edit().putBoolean(Constants.KEY_IS_VAULT_DIRTY, false).apply()
    }
}
