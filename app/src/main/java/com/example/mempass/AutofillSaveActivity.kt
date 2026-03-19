package com.example.mempass

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mempass.common.Constants
import com.example.mempass.ui.theme.BrandIndigo
import com.example.mempass.ui.theme.MempassTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import java.util.Arrays
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class AutofillSaveActivity : FragmentActivity() {

    @Inject
    @Named("SecurityPrefs")
    lateinit var securityPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURITY FIX: Apply screen security if enabled in settings
        val isScreenSecurityEnabled = securityPrefs.getBoolean(Constants.KEY_SCREEN_SECURITY_ENABLED, true)
        if (isScreenSecurityEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        // SECURITY FIX: Retrieve sensitive data from in-memory cache using session ID
        val sessionId = intent.getStringExtra("autofill_session_id")
        val autofillData = sessionId?.let { AutofillCache.getAndRemove(it) }

        if (autofillData == null) {
            // If data is missing or session expired/already used, close activity
            finish()
            return
        }

        val (username, password, serviceName, packageName, webDomain) = autofillData

        setContent {
            val viewModel: VaultViewModel = hiltViewModel()
            var existingEntry by remember { mutableStateOf<PasswordEntry?>(null) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val all = viewModel.allPasswords.first()
                val usernameStr = String(username)
                existingEntry = all.find { entry -> 
                    // Improved matching for updates: consider package and domain
                    (entry.associatedPackageName == packageName || (webDomain != null && entry.associatedDomain == webDomain)) && run {
                        val decryptedChars = viewModel.decryptToChars(entry.encryptedUsername)
                        val result = String(decryptedChars) == usernameStr
                        CryptoUtils.wipe(decryptedChars)
                        result
                    }
                }
                isLoading = false
            }

            MempassTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        if (!isLoading) {
                            SavePromptCard(
                                serviceName = serviceName,
                                username = String(username),
                                isUpdate = existingEntry != null,
                                onSave = {
                                    if (viewModel.isUnlocked()) {
                                        val notesChars = if (existingEntry != null) {
                                            viewModel.decryptToChars(existingEntry!!.encryptedNotes)
                                        } else {
                                            getString(R.string.saved_via_autofill).toCharArray()
                                        }
                                        
                                        viewModel.savePassword(
                                            service = serviceName, 
                                            user = username, 
                                            pass = password, 
                                            notes = notesChars,
                                            associatedPackage = packageName,
                                            associatedDomain = webDomain,
                                            id = existingEntry?.id ?: 0
                                        )
                                        
                                        CryptoUtils.wipe(notesChars)
                                    } else {
                                        Toast.makeText(this@AutofillSaveActivity, getString(R.string.please_unlock_first), Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    // Clear cache data
                                    CryptoUtils.wipe(username, password)
                                    
                                    finish()
                                },
                                onDismiss = { 
                                    CryptoUtils.wipe(username, password)
                                    finish() 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavePromptCard(
    serviceName: String, 
    username: String, 
    isUpdate: Boolean,
    onSave: () -> Unit, 
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isUpdate) Icons.Default.Update else Icons.Default.Save, 
                contentDescription = null, 
                tint = BrandIndigo, 
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (isUpdate) stringResource(R.string.autofill_update_prompt_title) else stringResource(R.string.autofill_save_prompt_title), 
                fontSize = 20.sp, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isUpdate) stringResource(R.string.autofill_update_prompt_desc, serviceName) 
                       else stringResource(R.string.autofill_save_prompt_desc, serviceName), 
                fontSize = 14.sp, 
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.autofill_account_label), fontSize = 12.sp, color = BrandIndigo, fontWeight = FontWeight.Bold)
                    Text(username, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) {
                Text(if (isUpdate) stringResource(R.string.autofill_update_button) else stringResource(R.string.autofill_save_button))
            }
            
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.autofill_not_now), color = Color.Gray)
            }
        }
    }
}
