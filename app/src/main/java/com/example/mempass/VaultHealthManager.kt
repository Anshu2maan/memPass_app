package com.example.mempass

import android.app.Application
import android.content.SharedPreferences
import com.example.mempass.common.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VaultHealthManager @Inject constructor(
    private val application: Application,
    @Named("SecurityPrefs") private val prefs: SharedPreferences,
    private val biometricHelper: BiometricHelper
) {

    fun getVaultHealth(
        allPasswords: Flow<List<PasswordEntry>>,
        allDocuments: Flow<List<DocumentEntry>>,
        decryptToChars: (ByteArray) -> CharArray
    ): Flow<VaultHealth> = combine(allPasswords, allDocuments) { passwords, documents ->
        val factors = mutableListOf<HealthFactor>()
        val now = System.currentTimeMillis()
        
        // 1. Password Security (40%)
        var passScore = 1.0f
        if (passwords.isNotEmpty()) {
            val decryptedPasses = passwords.map { decryptToChars(it.encryptedPassword) }
            val weakCount = decryptedPasses.count { 
                val result = PasswordGenerator.calculateStrength(it).first < Constants.WEAK_PASSWORD_THRESHOLD
                CryptoUtils.wipe(it)
                result
            }
            
            // Note: Unique check is hard with CharArray without converting to String. 
            // For health check, we'll skip reuse detection or use a hash-based approach if needed.
            // For now, let's focus on strength.
            val uniquePasses = passwords.size // Placeholder
            
            val sixMonths = 180L * 24 * 3600 * 1000
            val oldPassCount = passwords.count { it.createdAt < now - sixMonths }
            val mfaCount = passwords.count { it.encryptedTotpSecret != null }
            
            val strengthPart = (passwords.size - weakCount).toFloat() / passwords.size
            val agePart = (passwords.size - oldPassCount).toFloat() / passwords.size
            val mfaPart = mfaCount.toFloat() / passwords.size
            
            passScore = (strengthPart * 0.5f) + (agePart * 0.3f) + (mfaPart * 0.2f)
            
            factors.add(HealthFactor(
                application.getString(R.string.passwords), passScore,
                if(passScore > 0.8f) "Strong" else if(passScore > 0.5f) "Moderate" else "Weak",
                if(weakCount > 0) "Fix $weakCount weak passwords." else "Password security is good."
            ))
        } else {
            factors.add(HealthFactor(application.getString(R.string.passwords), 1.0f, "No Data", "Add passwords to analyze."))
        }

        // 2. Document Validity (30%)
        var docScore = 1.0f
        if (documents.isNotEmpty()) {
            val thirtyDays = 30L * 24 * 3600 * 1000
            val expiredCount = documents.count { it.expiryDate != null && it.expiryDate!! > 0 && it.expiryDate!! < now }
            val expiringSoon = documents.count { it.expiryDate != null && it.expiryDate!! > 0 && it.expiryDate!! < now + thirtyDays && it.expiryDate!! >= now }
            
            docScore = (documents.size - (expiredCount + (expiringSoon * 0.5f))).toFloat() / documents.size
            docScore = docScore.coerceIn(0.0f, 1.0f)
            
            factors.add(HealthFactor(
                application.getString(R.string.documents), docScore,
                if(expiredCount > 0) "Critical" else if(expiringSoon > 0) "Expiring" else "Valid",
                if(expiredCount > 0) "$expiredCount docs expired!" else if(expiringSoon > 0) "$expiringSoon docs expiring soon." else "All documents are valid."
            ))
        } else {
            factors.add(HealthFactor(application.getString(R.string.documents), 1.0f, "No Data", "Add documents with expiry dates."))
        }

        // 3. Account Security (30%)
        val bioEnabled = biometricHelper.isBiometricEnabled()
        val lastBackup = prefs.getLong(Constants.KEY_LAST_BACKUP_TIME, 0L)
        val backupRecent = now - lastBackup < Constants.BACKUP_REMINDER_DAYS * 24 * 3600 * 1000L
        
        val accScore = (if(bioEnabled) 0.5f else 0.0f) + (if(backupRecent) 0.5f else 0.0f)
        factors.add(HealthFactor(
            "Access & Backup", accScore,
            if(accScore == 1.0f) "Excellent" else if(accScore >= 0.5f) "Fair" else "Risky",
            if(!bioEnabled) application.getString(R.string.biometric_disabled) else if(!backupRecent) application.getString(R.string.no_backup_found) else "Account security is optimal."
        ))

        val totalItems = passwords.size + documents.size
        val itemBonus = if(totalItems >= 10) 0.05f else 0.0f

        val overallScore = ((passScore * 0.4f) + (docScore * 0.3f) + (accScore * 0.3f) + itemBonus).coerceIn(0.0f, 1.0f)
        
        VaultHealth(overallScore, factors)
    }

    fun getSecurityTips(
        allPasswords: Flow<List<PasswordEntry>>,
        allDocuments: Flow<List<DocumentEntry>>,
        decryptToChars: (ByteArray) -> CharArray
    ): Flow<List<SecurityTip>> = combine(allPasswords, allDocuments) { passwords, documents ->
        val tips = mutableListOf<SecurityTip>()
        val now = System.currentTimeMillis()

        val failedAttempts = prefs.getInt(Constants.KEY_FAILED_ATTEMPTS, 0)
        if (failedAttempts >= Constants.MAX_FAILED_ATTEMPTS) {
            tips.add(SecurityTip("$failedAttempts failed attempts recorded since last login", TipType.WARNING))
        }

        val weakCount = passwords.count {
            val decrypted = decryptToChars(it.encryptedPassword)
            val result = PasswordGenerator.calculateStrength(decrypted).first < Constants.WEAK_PASSWORD_THRESHOLD
            CryptoUtils.wipe(decrypted)
            result
        }
        if (weakCount > 0) {
            tips.add(SecurityTip(application.getString(R.string.weak_password_warning, weakCount), TipType.WARNING, "password_list"))
        }

        val expiryWarningMs = Constants.EXPIRY_WARNING_DAYS * 24 * 3600 * 1000L
        documents.filter { it.expiryDate != null && it.expiryDate!! > 0 }.forEach { doc ->
            val expiry = doc.expiryDate!!
            if (expiry < now + expiryWarningMs) {
                val daysLeft = ((expiry - now) / (24 * 3600 * 1000)).toInt()
                val msg = if (daysLeft <= 0) 
                    application.getString(R.string.expired_doc_warning, doc.title)
                else 
                    application.getString(R.string.expiring_doc_warning, doc.title, daysLeft)
                tips.add(SecurityTip(msg, TipType.WARNING, "document_list"))
            }
        }

        if (!biometricHelper.isBiometricEnrolled()) {
            tips.add(SecurityTip(application.getString(R.string.biometric_not_setup), TipType.INFO, "settings"))
        } else if (!biometricHelper.isBiometricEnabled()) {
            tips.add(SecurityTip(application.getString(R.string.biometric_disabled), TipType.INFO, "settings"))
        }

        val lastBackup = prefs.getLong(Constants.KEY_LAST_BACKUP_TIME, 0L)
        if (lastBackup == 0L) {
            tips.add(SecurityTip(application.getString(R.string.no_backup_found), TipType.WARNING, "settings"))
        } else {
            val daysSinceBackup = ((now - lastBackup) / (24 * 3600 * 1000)).toInt()
            if (daysSinceBackup >= Constants.BACKUP_REMINDER_DAYS) {
                tips.add(SecurityTip(application.getString(R.string.last_backup_days, daysSinceBackup), TipType.INFO, "settings"))
            }
        }

        if (tips.isEmpty()) {
            tips.add(SecurityTip(application.getString(R.string.vault_secure), TipType.SUCCESS))
        }
        tips
    }
}
