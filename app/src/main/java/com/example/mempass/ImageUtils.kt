package com.example.mempass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.crypto.spec.SecretKeySpec

object ImageUtils {
    private const val TAG = "ImageUtils"

    /**
     * Unified Compression Logic: Iterative quality reduction + scaling if needed.
     */
    fun compressImageToTarget(
        context: Context,
        uri: Uri,
        targetSizeKb: Long
    ): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            val targetSizeBytes = targetSizeKb * 1024
            val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            
            var quality = 100
            val stream = ByteArrayOutputStream()
            
            // Step 1: Quality Reduction
            do {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                if (stream.size() <= targetSizeBytes || quality <= 10) break
                quality -= 10
            } while (true)

            // Step 2: Scaling if still too large
            if (stream.size() > targetSizeBytes) {
                var scale = 0.8f
                var currentBitmap = bitmap
                while (stream.size() > targetSizeBytes && scale > 0.1f) {
                    stream.reset()
                    val width = (bitmap.width * scale).toInt()
                    val height = (bitmap.height * scale).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(currentBitmap, width, height, true)
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    scale -= 0.1f
                }
            }

            FileOutputStream(outputFile).use { it.write(stream.toByteArray()) }
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
            null
        }
    }

    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun renderPdfPageToBitmap(tempFile: File, reqWidth: Int, reqHeight: Int, pageIndex: Int = 0): Bitmap? {
        return try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount > pageIndex) {
                        renderer.openPage(pageIndex).use { page ->
                            val scale = Math.min(reqWidth.toFloat() / page.width, reqHeight.toFloat() / page.height)
                            val bmpWidth = (page.width * scale).toInt().coerceAtLeast(1)
                            val bmpHeight = (page.height * scale).toInt().coerceAtLeast(1)
                            
                            val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF rendering failed", e)
            null
        }
    }

    /**
     * Helper to render PDF from bytes (useful for encrypted PDFs)
     */
    fun renderPdfBytesToBitmap(context: Context, bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val tempFile = File(context.cacheDir, "pdf_thumb_temp.pdf")
        return try {
            tempFile.writeBytes(bytes)
            renderPdfPageToBitmap(tempFile, reqWidth, reqHeight, 0)
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    fun decodeSampledBitmapFromEncryptedFile(file: File, key: SecretKeySpec, reqWidth: Int, reqHeight: Int, context: Context? = null): Bitmap? {
        return try {
            val decryptedBytes = FileEncryptor.decryptFile(file, key)
            
            // First check if it's a PDF by magic bytes (optional but safer) or just try
            // Here we check if context is provided to try PDF rendering
            if (context != null) {
                // Simplistic PDF check: %PDF-
                if (decryptedBytes.size > 4 && decryptedBytes[0] == 0x25.toByte() && decryptedBytes[1] == 0x50.toByte() && 
                    decryptedBytes[2] == 0x44.toByte() && decryptedBytes[3] == 0x46.toByte()) {
                    return renderPdfBytesToBitmap(context, decryptedBytes, reqWidth, reqHeight)
                }
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
            
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(decryptedBytes, 0, decryptedBytes.size, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode encrypted bitmap", e)
            null
        }
    }
}
