package com.example.mempass

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BiometricHelper @Inject constructor(
    private val context: Context,
    @Named("SecurityPrefs") private val encryptedPrefs: SharedPreferences
) {
    private val TAG = "BiometricHelper"
    private val KEY_NAME = "mempass_biometric_key_v1"
    private val KEYSTORE_NAME = "AndroidKeyStore"
    
    // Keys used inside EncryptedSharedPreferences
    private val ENCRYPTED_PIN_KEY = "biometric_encrypted_pin"
    private val IV_KEY = "biometric_encryption_iv"
    private val BIOMETRIC_ENABLED_KEY = "biometric_enabled"
    private val GCM_TAG_LENGTH = 128

    fun isDeviceSecure(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguardManager.isDeviceSecure
    }

    fun isBiometricAvailable(): Int {
        val biometricManager = BiometricManager.from(context)
        val strongStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strongStatus == BiometricManager.BIOMETRIC_SUCCESS || strongStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return strongStatus
        }
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    }

    fun isBiometricEnrolled(): Boolean = isBiometricAvailable() == BiometricManager.BIOMETRIC_SUCCESS

    fun setBiometricEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(BIOMETRIC_ENABLED_KEY, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean = encryptedPrefs.getBoolean(BIOMETRIC_ENABLED_KEY, false) && isBiometricEnrolled()

    fun isBiometricSetup(): Boolean = encryptedPrefs.getString(ENCRYPTED_PIN_KEY, null) != null

    fun openBiometricSettings(activity: FragmentActivity) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        activity.startActivity(intent)
    }

    private fun getSecretKey(forceCreate: Boolean = false): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_NAME)
        keyStore.load(null)
        
        if (!forceCreate) {
            val key = try {
                keyStore.getKey(KEY_NAME, null)
            } catch (e: Exception) {
                null
            }
            if (key != null) return key as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME)
        val builder = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    private fun getCipher(): Cipher = Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}")

    fun encryptPinWithBiometric(activity: FragmentActivity, pin: CharArray, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isDeviceSecure()) {
            onError(context.getString(R.string.secure_lock_required))
            return
        }

        try {
            val cipher = getCipher()
            val key = try {
                getSecretKey()
            } catch (e: Exception) {
                getSecretKey(forceCreate = true)
            }
            
            cipher.init(Cipher.ENCRYPT_MODE, key)
            
            showBiometricPrompt(
                activity,
                BiometricPrompt.CryptoObject(cipher),
                title = context.getString(R.string.enable_fp),
                subtitle = context.getString(R.string.fp_subtitle),
                onSuccess = { result ->
                    // SECURITY FIX: Convert CharArray to ByteArray securely and zero it out after use (Finding #8)
                    val pinBytes = charToBytes(pin)
                    try {
                        val encryptedBytes = result.cryptoObject?.cipher?.doFinal(pinBytes)
                        val iv = result.cryptoObject?.cipher?.iv
                        if (encryptedBytes != null && iv != null) {
                            encryptedPrefs.edit()
                                .putString(ENCRYPTED_PIN_KEY, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
                                .putString(IV_KEY, Base64.encodeToString(iv, Base64.DEFAULT))
                                .apply()
                            setBiometricEnabled(true)
                            onSuccess()
                        } else {
                            onError(context.getString(R.string.chip_failure))
                        }
                    } finally {
                        Arrays.fill(pinBytes, 0.toByte())
                    }
                },
                onError = { code, err -> 
                    if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError("USER_WANTS_PIN")
                    } else {
                        onError(mapBiometricError(code, err.toString()))
                    }
                },
                onFailed = { /* Feedback provided by OS */ }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Biometric setup failed", e)
            onError(context.getString(R.string.setup_failed_generic, e.localizedMessage ?: ""))
        }
    }

    fun decryptPinWithBiometric(activity: FragmentActivity, onSuccess: (CharArray) -> Unit, onError: (String) -> Unit) {
        val encryptedPinBase64 = encryptedPrefs.getString(ENCRYPTED_PIN_KEY, null)
        val ivBase64 = encryptedPrefs.getString(IV_KEY, null)

        if (encryptedPinBase64 == null || ivBase64 == null) {
            onError(context.getString(R.string.no_fp_data))
            return
        }

        try {
            val encryptedPin = Base64.decode(encryptedPinBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val cipher = getCipher()
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))

            showBiometricPrompt(
                activity,
                BiometricPrompt.CryptoObject(cipher),
                title = context.getString(R.string.unlock_fp),
                subtitle = context.getString(R.string.scan_fp),
                onSuccess = { result ->
                    val decryptedBytes = result.cryptoObject?.cipher?.doFinal(encryptedPin)
                    if (decryptedBytes != null) {
                        // SECURITY FIX: Convert bytes to CharArray and zero out sensitive bytes (Finding #8)
                        val pinChars = bytesToChars(decryptedBytes)
                        Arrays.fill(decryptedBytes, 0.toByte())
                        onSuccess(pinChars)
                    } else {
                        onError(context.getString(R.string.no_vault_key))
                    }
                },
                onError = { code, err -> 
                    if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onError("USER_WANTS_PIN")
                    } else {
                        onError(mapBiometricError(code, err.toString()))
                    }
                },
                onFailed = { /* Feedback provided by OS */ }
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            setBiometricEnabled(false)
            encryptedPrefs.edit().remove(ENCRYPTED_PIN_KEY).apply()
            onError(context.getString(R.string.new_fp_detected))
        } catch (e: Exception) {
            Log.e(TAG, "Biometric unlock failed", e)
            onError(context.getString(R.string.unlock_failed_generic))
        }
    }

    private fun charToBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    }

    private fun bytesToChars(bytes: ByteArray): CharArray {
        val byteBuffer = ByteBuffer.wrap(bytes)
        val charBuffer = StandardCharsets.UTF_8.decode(byteBuffer)
        val chars = CharArray(charBuffer.remaining())
        charBuffer.get(chars)
        return chars
    }

    private fun mapBiometricError(code: Int, original: String): String {
        return when (code) {
            BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE -> context.getString(R.string.biometric_hw_error)
            BiometricPrompt.ERROR_LOCKOUT -> context.getString(R.string.lockout_error)
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> context.getString(R.string.permanent_lockout_error)
            BiometricPrompt.ERROR_USER_CANCELED -> context.getString(R.string.auth_cancelled)
            BiometricPrompt.ERROR_NO_BIOMETRICS -> context.getString(R.string.no_biometrics_enrolled)
            BiometricPrompt.ERROR_TIMEOUT -> context.getString(R.string.auth_timeout)
            else -> original
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        cryptoObject: BiometricPrompt.CryptoObject? = null,
        title: String = context.getString(R.string.unlock_fp),
        subtitle: String = context.getString(R.string.scan_fp),
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (Int, CharSequence) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errorCode, errString)
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailed()
            }
        })

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(context.getString(R.string.use_mempass_pin))
            .setConfirmationRequired(false)

        prompt.authenticate(promptInfoBuilder.build(), cryptoObject!!)
    }
}
