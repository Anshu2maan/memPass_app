package com.example.mempass

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM passwords ORDER BY createdAt DESC")
    fun getAllPasswords(): Flow<List<PasswordEntry>>

    @Query("SELECT * FROM passwords WHERE serviceName LIKE '%' || :query || '%'")
    suspend fun searchPasswords(query: String): List<PasswordEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(entry: PasswordEntry): Long

    @Upsert
    suspend fun upsertPasswords(entries: List<PasswordEntry>)

    @Delete
    suspend fun deletePassword(entry: PasswordEntry)

    @Query("DELETE FROM passwords")
    suspend fun clearPasswords()

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntry>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Int): DocumentEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(entry: DocumentEntry): Long

    @Upsert
    suspend fun upsertDocuments(entries: List<DocumentEntry>)

    @Delete
    suspend fun deleteDocument(entry: DocumentEntry)

    @Query("DELETE FROM documents")
    suspend fun clearDocuments()

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(entry: NoteEntry): Long

    @Upsert
    suspend fun upsertNotes(entries: List<NoteEntry>)

    @Delete
    suspend fun deleteNote(entry: NoteEntry)

    @Query("DELETE FROM notes")
    suspend fun clearNotes()

    // Transactional Migration Helpers
    @Transaction
    suspend fun migrateAllData(
        passwords: List<PasswordEntry>,
        documents: List<DocumentEntry>,
        notes: List<NoteEntry>
    ) {
        upsertPasswords(passwords)
        upsertDocuments(documents)
        upsertNotes(notes)
    }

    @Query("SELECT * FROM passwords WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getPasswordByRemoteId(remoteId: String): PasswordEntry?

    @Query("SELECT * FROM documents WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getDocumentByRemoteId(remoteId: String): DocumentEntry?

    @Query("SELECT * FROM notes WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getNoteByRemoteId(remoteId: String): NoteEntry?
}
