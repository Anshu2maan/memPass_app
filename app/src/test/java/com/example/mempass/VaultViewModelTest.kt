package com.example.mempass

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModelTest {

    @MockK
    lateinit var application: Application

    @MockK
    lateinit var sharedPreferences: SharedPreferences

    @MockK
    lateinit var historyPrefs: SharedPreferences

    @MockK
    lateinit var editor: SharedPreferences.Editor

    @MockK
    lateinit var repository: VaultRepository

    @MockK
    lateinit var vaultManager: VaultManager

    @MockK
    lateinit var biometricHelper: BiometricHelper

    @MockK
    lateinit var cameraHelper: CameraHelper

    @MockK
    lateinit var healthManager: VaultHealthManager

    @MockK
    lateinit var backupHelper: VaultBackupHelper

    private lateinit var viewModel: VaultViewModel

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // Mock Android Logs
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any(), any()) } returns 0

        // Mock SystemClock for lockout logic
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1000000L

        // Mock Base64 (Android version)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "mocked_base64"
        every { Base64.decode(any<String>(), any()) } returns "mocked_bytes".toByteArray()

        // Setup initial mocks for constructor
        every { sharedPreferences.getInt(any(), any()) } returns 0
        every { sharedPreferences.getString(any(), any()) } returns null
        every { sharedPreferences.getLong(any(), any()) } returns 0L
        every { sharedPreferences.edit() } returns editor
        
        every { historyPrefs.getStringSet(any(), any()) } returns emptySet()
        
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } just Runs

        // Mock Repository and flows
        every { repository.allPasswords } returns flowOf(emptyList())
        every { repository.allDocuments } returns flowOf(emptyList())
        every { repository.allNotes } returns flowOf(emptyList())

        // Mock HealthManager flows
        every { healthManager.getVaultHealth(any(), any(), any()) } returns flowOf(mockk(relaxed = true))
        every { healthManager.getSecurityTips(any(), any(), any()) } returns flowOf(emptyList())

        // Mock KeyManager
        mockkObject(KeyManager)
        every { KeyManager.generateSalt() } returns "salt".toByteArray()
        every { KeyManager.deriveKeyArgon2(any(), any()) } returns SecretKeySpec("32byteslongmockkey32byteslongmock".toByteArray(), "AES")
        every { KeyManager.deriveKeySha256(any()) } returns SecretKeySpec("32byteslongmockkey32byteslongmock".toByteArray(), "AES")
        every { KeyManager.generateRecoveryKey() } returns "ABCD-1234-EFGH-5678"

        // Mock CryptoUtils
        mockkObject(CryptoUtils)
        every { CryptoUtils.encrypt(any(), any()) } returns "encrypted_verify_string"
        every { CryptoUtils.decrypt(any(), any()) } returns "VERIFY"

        // Mock VaultManager
        every { vaultManager.setKey(any()) } just Runs
        every { vaultManager.getKey() } returns null
        every { vaultManager.isUnlocked() } returns false
        every { vaultManager.clearKey() } just Runs

        viewModel = VaultViewModel(
            application, repository, sharedPreferences, historyPrefs, 
            vaultManager, biometricHelper, cameraHelper, healthManager, backupHelper
        )
    }

    @Test
    fun `test isFirstTime returns true when no pin_hash stored`() {
        every { sharedPreferences.getString(any(), null) } returns null
        assertTrue(viewModel.isFirstTime())
    }

    @Test
    fun `test setupVault saves hash and returns recovery key`() {
        val recoveryKey = viewModel.setupVault("123456".toCharArray())
        
        assertEquals("ABCD-1234-EFGH-5678", recoveryKey)
        verify { editor.putString(any(), any()) }
        verify { editor.apply() }
    }

    @Test
    fun `test unlockVault returns true for correct pin`() {
        every { sharedPreferences.getString(any(), null) } returns "valid_hash"
        every { sharedPreferences.getString(any(), "sha256") } returns "argon2id"
        every { sharedPreferences.getString(any(), null) } returns "some_salt"
        
        // Mock successful verification
        every { CryptoUtils.decrypt(any(), any()) } returns "VERIFY"

        val result = viewModel.unlockVault("123456".toCharArray())
        
        assertTrue(result)
        verify { editor.putInt(any(), 0) }
    }

    @Test
    fun `test unlockVault returns false for wrong pin`() {
        every { sharedPreferences.getString(any(), null) } returns "valid_hash"
        every { sharedPreferences.getInt(any(), 0) } returns 0
        
        // Mock failed verification
        every { CryptoUtils.decrypt(any(), any()) } returns "WRONG"

        val result = viewModel.unlockVault("wrong_pin".toCharArray())
        
        assertFalse(result)
        verify { editor.putInt(any(), 1) }
    }
}
