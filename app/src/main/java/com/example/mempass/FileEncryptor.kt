package com.example.mempass

import android.util.Log
import java.io.*
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object FileEncryptor {
    private const val TAG = "FileEncryptor"
    private const val AES_GCM_ALGO = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_LENGTH = 128
    private const val BUFFER_SIZE = 8192

    fun encryptFile(source: File, destination: File, key: SecretKeySpec) {
        if (!source.exists()) throw FileNotFoundException("Source not found")
        
        val freeSpace = destination.parentFile?.freeSpace ?: 0L
        if (freeSpace < (source.length() * 1.1).toLong()) {
            throw IOException("Low disk space")
        }

        try {
            source.inputStream().use { input ->
                destination.outputStream().use { out ->
                    encryptStreamToStream(input, out, key)
                }
            }
        } catch (e: Exception) {
            if (destination.exists()) destination.delete()
            Log.e(TAG, "Encryption failed")
            throw e
        }
    }

    fun encryptStreamToFile(input: InputStream, destination: File, key: SecretKeySpec) {
        destination.outputStream().use { out ->
            encryptStreamToStream(input, out, key)
        }
    }

    fun encryptStreamToStream(input: InputStream, output: OutputStream, key: SecretKeySpec) {
        try {
            val iv = ByteArray(IV_SIZE)
            java.security.SecureRandom().nextBytes(iv)
            output.write(iv)

            val cipher = Cipher.getInstance(AES_GCM_ALGO)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val cipherOut = CipherOutputStream(output, cipher)
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                cipherOut.write(buffer, 0, bytesRead)
            }
            cipherOut.flush()
            cipherOut.close() 
        } catch (e: Exception) {
            Log.e(TAG, "Stream encryption failed")
            throw e
        }
    }

    fun decryptFile(source: File, key: SecretKeySpec): ByteArray {
        if (!source.exists()) throw FileNotFoundException("Encrypted file missing")
        
        return try {
            val fis = FileInputStream(source)
            val iv = ByteArray(IV_SIZE)
            val ivRead = fis.read(iv)
            if (ivRead < IV_SIZE) throw IOException("Missing IV")
            
            val cipher = Cipher.getInstance(AES_GCM_ALGO)
            val spec = GCMParameterSpec(TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            
            CipherInputStream(fis, cipher).use { cis ->
                cis.readBytes()
            }
        } catch (e: AEADBadTagException) {
            Log.e(TAG, "Decryption failed: Tag mismatch")
            throw GeneralSecurityException("Integrity check failed", e)
        } catch (e: Exception) {
            Log.e(TAG, "File decryption failed")
            throw e
        }
    }

    fun decryptBytes(encryptedData: ByteArray, key: SecretKeySpec): ByteArray {
        val bis = ByteArrayInputStream(encryptedData)
        val iv = ByteArray(IV_SIZE)
        val bytesRead = bis.read(iv)
        if (bytesRead < IV_SIZE) throw IOException("Missing IV")

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return try {
            CipherInputStream(bis, cipher).use { it.readBytes() }
        } catch (e: Exception) {
            if (e.cause is AEADBadTagException) {
                throw GeneralSecurityException("Integrity check failed")
            }
            throw e
        }
    }

    fun decryptFileToFile(source: File, destination: File, key: SecretKeySpec) {
        try {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    decryptStreamToStream(input, output, key)
                }
            }
        } catch (e: Exception) {
            if (destination.exists()) destination.delete()
            throw e
        }
    }

    fun decryptStreamToStream(input: InputStream, output: OutputStream, key: SecretKeySpec) {
        val iv = ByteArray(IV_SIZE)
        val bytesRead = input.read(iv)
        if (bytesRead < IV_SIZE) throw IOException("Missing IV")

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        try {
            CipherInputStream(input, cipher).use { cipherIn ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (cipherIn.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } catch (e: Exception) {
            if (e.cause is AEADBadTagException) {
                throw GeneralSecurityException("Integrity check failed")
            }
            throw e
        }
    }

    fun getDecryptionStream(input: InputStream, key: SecretKeySpec): InputStream {
        val iv = ByteArray(IV_SIZE)
        val bytesRead = input.read(iv)
        if (bytesRead < IV_SIZE) throw IOException("Missing IV")

        val cipher = Cipher.getInstance(AES_GCM_ALGO)
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return CipherInputStream(input, cipher)
    }
}
