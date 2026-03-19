package com.example.mempass

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressor @Inject constructor() {
    private val TAG = "ImageCompressor"

    suspend fun compressImageToTargetSize(
        sourceFile: File,
        targetFile: File,
        targetSizeKb: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)
            
            // Initial downsample if image is huge to avoid OOM
            var inSampleSize = 1
            if (options.outWidth > 4000 || options.outHeight > 4000) {
                inSampleSize = 2
            }
            
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }) ?: return@withContext false
            
            val targetSizeBytes = targetSizeKb * 1024
            var currentQuality = 90
            var stream = ByteArrayOutputStream()
            
            // Iterative approach: First try reducing quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
            
            if (stream.size() > targetSizeBytes) {
                // Step 1: Reduce quality down to 30
                while (stream.size() > targetSizeBytes && currentQuality > 30) {
                    stream.reset()
                    currentQuality -= 10
                    bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
                }
            }

            // Step 2: If still too large, aggressively downscale
            if (stream.size() > targetSizeBytes) {
                var scale = 0.8f
                var currentBitmap = bitmap
                while (stream.size() > targetSizeBytes && scale > 0.05f) {
                    stream.reset()
                    val width = (bitmap.width * scale).toInt()
                    val height = (bitmap.height * scale).toInt()
                    
                    if (width < 50 || height < 50) break // Don't go below thumbnail size

                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, stream)
                    
                    if (currentBitmap != bitmap) currentBitmap.recycle()
                    currentBitmap = scaledBitmap
                    scale *= 0.7f // Geometric reduction for faster convergence
                }
            }

            FileOutputStream(targetFile).use { it.write(stream.toByteArray()) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Image compression failed", e)
            false
        }
    }
}
