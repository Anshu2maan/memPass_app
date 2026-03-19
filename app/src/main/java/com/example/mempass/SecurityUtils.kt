package com.example.mempass

import android.content.Context
import android.util.Log
import java.io.File

object SecurityUtils {
    private const val TAG = "SecurityUtils"

    /**
     * Clears all temporary files created by the app in cache and temp directories.
     * Should be called on app startup to ensure no decrypted data persists from previous sessions.
     */
    fun clearTemporaryFiles(context: Context) {
        try {
            // Clear internal cache
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()

            // Clear external cache if available
            context.externalCacheDir?.deleteRecursively()

            // Note: System temp directory (java.io.tmpdir) is usually handled by the OS, 
            // but we avoid using it for sensitive data now.
            
            Log.i(TAG, "Temporary files cleared successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear temporary files", e)
        }
    }
}
