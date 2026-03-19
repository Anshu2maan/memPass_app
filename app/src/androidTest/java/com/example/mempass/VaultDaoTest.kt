package com.example.mempass

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class VaultDaoTest {
    private lateinit var db: VaultDatabase
    private lateinit var dao: VaultDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VaultDatabase::class.java
        ).build()
        dao = db.vaultDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writePasswordAndReadInList() = runBlocking {
        val password = PasswordEntry(
            serviceName = "Google",
            encryptedUsername = "user_enc",
            encryptedPassword = "pass_enc",
            encryptedNotes = "notes_enc"
        )
        dao.insertPassword(password)
        val allPasswords = dao.getAllPasswords().first()
        assertEquals(allPasswords[0].serviceName, "Google")
    }

    @Test
    fun deletePasswordRemovesFromList() = runBlocking {
        val password = PasswordEntry(
            serviceName = "Facebook",
            encryptedUsername = "fb_user",
            encryptedPassword = "fb_pass",
            encryptedNotes = ""
        )
        val id = dao.insertPassword(password)
        val inserted = dao.getAllPasswords().first()[0]
        dao.deletePassword(inserted)
        val allPasswords = dao.getAllPasswords().first()
        assertTrue(allPasswords.isEmpty())
    }

    @Test
    fun upsertPasswordsHandlesConflicts() = runBlocking {
        val remoteId = "unique-id-123"
        val initial = PasswordEntry(
            remoteId = remoteId,
            serviceName = "Initial",
            encryptedUsername = "u1",
            encryptedPassword = "p1",
            encryptedNotes = ""
        )
        dao.insertPassword(initial)

        val update = PasswordEntry(
            remoteId = remoteId,
            serviceName = "Updated",
            encryptedUsername = "u2",
            encryptedPassword = "p2",
            encryptedNotes = "updated notes"
        )
        
        dao.upsertPasswords(listOf(update))
        
        val all = dao.getAllPasswords().first()
        assertEquals(1, all.size)
        assertEquals("Updated", all[0].serviceName)
        assertEquals("updated notes", all[0].encryptedNotes)
    }

    @Test
    fun writeAndReadNotes() = runBlocking {
        val note = NoteEntry(
            title = "Secret Note",
            encryptedContent = "content_enc",
            category = "Private"
        )
        dao.insertNote(note)
        val allNotes = dao.getAllNotes().first()
        assertEquals("Secret Note", allNotes[0].title)
        assertEquals("Private", allNotes[0].category)
    }
}
