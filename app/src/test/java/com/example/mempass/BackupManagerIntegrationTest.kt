package com.example.mempass

import android.content.Context
import android.util.Base64
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.spec.SecretKeySpec
import java.util.Base64 as JavaBase64

class BackupManagerIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0

        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            JavaBase64.getEncoder().encodeToString(it.invocation.args[0] as ByteArray)
        }
        every { Base64.decode(any<String>(), any()) } answers {
            JavaBase64.getDecoder().decode(it.invocation.args[0] as String)
        }
    }

    @Test
    fun testBackupAndRestoreFlow() = runBlocking {
        val vaultKey = SecretKeySpec("32byteslongmockkey32byteslongmock".toByteArray(), "AES")
        val backupFile = File(tempFolder.root, "test_backup.mempass")
        val backupPass = "backup_secret_123"

        val passwords = listOf(
            PasswordEntry(serviceName = "TestService", encryptedUsername = "user_enc", encryptedPassword = "pass_enc", encryptedNotes = "")
        )
        val documents = emptyList<DocumentEntry>()
        val notes = emptyList<NoteEntry>()

        // 1. Create Backup
        val backupSuccess = BackupManager.createFullBackup(
            context, backupFile, backupPass, vaultKey, passwords, documents, notes
        )
        assertTrue("Backup should be successful", backupSuccess)
        assertTrue("Backup file should exist", backupFile.exists())
    }
}
