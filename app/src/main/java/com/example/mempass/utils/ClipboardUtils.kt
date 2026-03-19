package com.example.mempass.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mempass.ClipboardClearWorker
import java.util.concurrent.TimeUnit

object ClipboardUtils {
    fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        
        clipboard.setPrimaryClip(clip)
        
        // Schedule auto-clear after 30 seconds using WorkManager for reliability
        val clearRequest = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        
        WorkManager.getInstance(context).enqueueUniqueWork(
            "clipboard_clear",
            ExistingWorkPolicy.REPLACE,
            clearRequest
        )
    }
}
