package com.example.mempass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class OcrHelper @Inject constructor(@ApplicationContext private val context: Context) {
    private val TAG = "OcrHelper"
    private val MAX_OCR_DIMENSION = 2048f 
    private val MAX_PDF_PAGES = 5 

    suspend fun extractMergedInfo(filePaths: List<String>, key: SecretKeySpec): Map<String, String> = withContext(Dispatchers.IO) {
        val mergedResults = mutableMapOf<String, String>()
        filePaths.forEach { path ->
            try {
                val info = extractInfoFromFile(path, key)
                mergedResults.putAll(info)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract info from file")
            }
        }
        mergedResults
    }

    suspend fun extractInfoFromFile(filePath: String, key: SecretKeySpec): Map<String, String> {
        val fullText = performOcrOperation(filePath, key)
        return parseVisionText(fullText)
    }

    suspend fun extractText(filePath: String, key: SecretKeySpec): String {
        return performOcrOperation(filePath, key)
    }

    private suspend fun performOcrOperation(filePath: String, key: SecretKeySpec): String = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "ocr_tmp_${System.nanoTime()}.tmp")
        // Refactor: Create recognizer once and pass it down (Fixes Finding #25)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext "Error: File not found"
            
            FileEncryptor.decryptFileToFile(file, tempFile, key)
            
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext "Error: Decryption failed or empty file"
            }

            val fullText = StringBuilder()
            if (filePath.lowercase().endsWith(".pdf")) {
                processPdf(tempFile, fullText, recognizer)
            } else {
                processImage(tempFile, fullText, recognizer)
            }
            
            val result = fullText.toString()
            if (result.isBlank()) "Error: No text detected" else result
            
        } catch (e: Exception) {
            Log.e(TAG, "OCR Operation failed")
            when (e) {
                is SecurityException -> "Error: PDF is protected"
                is IOException -> "Error: Storage issue"
                else -> "Error: OCR process failed"
            }
        } finally {
            recognizer.close()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun processPdf(pdfFile: File, output: StringBuilder, recognizer: TextRecognizer) {
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pagesToProcess = min(renderer.pageCount, MAX_PDF_PAGES)
                    for (i in 0 until pagesToProcess) {
                        renderer.openPage(i).use { page ->
                            val scale = min(MAX_OCR_DIMENSION / page.width, MAX_OCR_DIMENSION / page.height).coerceAtMost(2.0f)
                            val width = (page.width * scale).toInt()
                            val height = (page.height * scale).toInt()
                            
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val image = InputImage.fromBitmap(bitmap, 0)
                                val result = recognizer.process(image).await()
                                output.append(result.text).append("\n")
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF Processing failed")
            output.append("Error: PDF processing failure")
        }
    }

    private suspend fun processImage(imageFile: File, output: StringBuilder, recognizer: TextRecognizer) {
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            val result = recognizer.process(image).await()
            output.append(result.text)
        } catch (e: Exception) {
            Log.e(TAG, "Image Processing failed")
        }
    }

    private fun parseVisionText(text: String): Map<String, String> {
        if (text.startsWith("Error:")) return mapOf("Error" to text.removePrefix("Error: "))
        val result = mutableMapOf<String, String>()
        
        val aadharRegex = "\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b".toRegex()
        val panRegex = "\\b[A-Z]{5}[0-9]{4}[A-Z]\\b".toRegex()
        val passportRegex = "\\b[A-Z][0-9]{7}\\b".toRegex()
        val dlRegex = "\\b[A-Z]{2}[0-9]{2}[0-9A-Z]{11}\\b".toRegex()
        val dobRegex = "(?i)(dob|date of birth)[:\\s]*(\\d{2}[/|-]\\d{2}[/|-]\\d{4})".toRegex()
        val expiryRegex = "(?i)(expiry|valid till)[:\\s]*(\\d{2}[/|-]\\d{2}[/|-]\\d{4})".toRegex()

        aadharRegex.find(text)?.let { result["Aadhar Number"] = it.value.replace(" ", "") }
        panRegex.find(text)?.let { result["PAN Number"] = it.value }
        passportRegex.find(text)?.let { result["Passport Number"] = it.value }
        dlRegex.find(text)?.let { result["Driving License"] = it.value }
        
        dobRegex.find(text)?.let { result["Date of Birth"] = it.groupValues[2] }
        expiryRegex.find(text)?.let { result["Expiry Date"] = it.groupValues[2] }
        
        if (!result.containsKey("Expiry Date")) {
            val genericDateRegex = "\\b\\d{2}[/|-]\\d{2}[/|-]\\d{4}\\b".toRegex()
            genericDateRegex.findAll(text).lastOrNull()?.let { 
                if (it.value != result["Date of Birth"]) {
                    result["Potential Expiry"] = it.value
                }
            }
        }
        
        return result
    }
}
