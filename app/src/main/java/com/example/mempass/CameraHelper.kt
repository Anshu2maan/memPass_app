package com.example.mempass

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class CameraHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("IntruderPrefs") private val intruderPrefs: SharedPreferences
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val INTRUDER_KEY_ALIAS = "intruder_encryption_key_v2"
    }

    fun getIntruderFolder(): File {
        val folder = File(context.filesDir, "intruders")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /**
     * Gets or creates a persistent key for intruder photos.
     * Securely wrapped using Android Keystore and stored in EncryptedSharedPreferences.
     */
    fun getIntruderKey(): SecretKeySpec {
        val storedKey = intruderPrefs.getString(INTRUDER_KEY_ALIAS, null)
        
        if (storedKey != null) {
            // 1. Try to unwrap using Keystore
            val unwrappedBytes = KeystoreHelper.unwrapToBytes(storedKey)
            if (unwrappedBytes != null) {
                val key = SecretKeySpec(unwrappedBytes, "AES")
                Arrays.fill(unwrappedBytes, 0.toByte()) // SECURITY: Wipe raw bytes from memory
                return key
            }

            // 2. Migration Path: If unwrap fails or it's a new pref file, generate fresh
            return generateAndStoreSecureKey()
        } else {
            // 3. Fresh install: Generate and store securely
            return generateAndStoreSecureKey()
        }
    }

    private fun generateAndStoreSecureKey(): SecretKeySpec {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        val key = SecretKeySpec(keyBytes, "AES")
        
        // Wrap the key using Keystore before storing
        val wrappedKey = KeystoreHelper.wrap(keyBytes)
        intruderPrefs.edit().putString(INTRUDER_KEY_ALIAS, wrappedKey).apply()
        
        Arrays.fill(keyBytes, 0.toByte()) // SECURITY: Wipe raw bytes from memory
        return key
    }

    fun captureIntruderPhoto(lifecycleOwner: LifecycleOwner, onSaved: (String) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)
                
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val tempPhotoFile = File(context.cacheDir, "temp_intruder_$timeStamp.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempPhotoFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e("CameraHelper", "Photo capture failed")
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            val key = getIntruderKey()
                            val encryptedFile = File(getIntruderFolder(), "intruder_$timeStamp.jpg.enc")
                            try {
                                FileEncryptor.encryptFile(tempPhotoFile, encryptedFile, key)
                                if (tempPhotoFile.exists()) tempPhotoFile.delete()
                                onSaved(encryptedFile.absolutePath)
                            } catch (e: Exception) {
                                Log.e("CameraHelper", "Failed to encrypt photo")
                            } finally {
                                cameraProvider.unbindAll()
                            }
                        }
                    }
                )
            } catch (exc: Exception) {
                Log.e("CameraHelper", "Use case binding failed")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun getIntruderLogs(): List<File> {
        val logs = getIntruderFolder().listFiles { file ->
            file.name.endsWith(".enc")
        }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        
        if (logs.size > 50) {
            logs.drop(50).forEach { it.delete() }
            return logs.take(50)
        }
        return logs
    }

    fun deleteIntruderLog(file: File) {
        if (file.exists()) file.delete()
    }
}
