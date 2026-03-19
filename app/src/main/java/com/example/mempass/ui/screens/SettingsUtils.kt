package com.example.mempass.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object SettingsUtils {
    
    fun openAutofillSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Direct: Provider Selection Screen
                val intent = Intent("android.settings.AUTOFILL_SETTINGS")
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    // Fallback: Request Prompt
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Toast.makeText(context, "Search for 'Autofill' in System Settings", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Toast.makeText(context, "Autofill not supported", Toast.LENGTH_SHORT).show()
        }
    }
}
