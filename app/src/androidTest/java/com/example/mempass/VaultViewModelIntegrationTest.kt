package com.example.mempass

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultViewModelIntegrationTest {

    private lateinit var viewModel: VaultViewModel
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun setup() {
        // ViewModel uses a real DB but we can ensure it's cleared if needed
        // For a true unit test, we'd mock the repository, but this is an Integration Test.
        viewModel = VaultViewModel(context)
    }

    @Test
    fun testVaultSetupAndUnlock() {
        val pin = "123456"
        val recoveryKey = viewModel.setupVault(pin)
        
        assertTrue(recoveryKey.isNotEmpty())
        assertTrue(viewModel.isUnlocked())
        
        // Lock and then unlock with correct PIN
        viewModel.lockVault()
        assertFalse(viewModel.isUnlocked())
        
        val unlockSuccess = viewModel.unlockVault(pin)
        assertTrue(unlockSuccess)
        assertTrue(viewModel.isUnlocked())
        
        // Test wrong PIN
        viewModel.lockVault()
        val wrongUnlock = viewModel.unlockVault("wrong_pin")
        assertFalse(wrongUnlock)
    }

    @Test
    fun testSaveAndDeletePassword() = runBlocking {
        // Ensure unlocked
        viewModel.setupVault("1234")
        
        viewModel.savePassword("Amazon", "user@test.com", "password123", "Some notes")
        
        // Wait for flow to emit
        val passwords = viewModel.allPasswords.first()
        val amazonPass = passwords.find { it.serviceName == "Amazon" }
        
        assertTrue(amazonPass != null)
        assertEquals("Amazon", amazonPass?.serviceName)
        
        // Delete
        amazonPass?.let { viewModel.deletePassword(it) }
        
        val passwordsAfterDelete = viewModel.allPasswords.first()
        assertFalse(passwordsAfterDelete.any { it.serviceName == "Amazon" })
    }

    @Test
    fun testVaultHealthCalculation() = runBlocking {
        viewModel.setupVault("1111")
        
        // Initial health with no data
        val initialHealth = viewModel.vaultHealth.first()
        assertTrue(initialHealth.overallScore > 0) // Should have some score based on security settings
        
        // Adding a weak password should affect health
        viewModel.savePassword("WeakService", "user", "123", "") // Very weak
        
        val healthAfterWeakPass = viewModel.vaultHealth.first()
        // The score calculation is complex, but it should be reflected in factors
        val passFactor = healthAfterWeakPass.factors.find { it.name.contains("Password", ignoreCase = true) }
        assertTrue(passFactor?.status == "Weak")
    }
}
