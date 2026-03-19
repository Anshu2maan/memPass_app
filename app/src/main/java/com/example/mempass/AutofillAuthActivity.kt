package com.example.mempass

import android.app.assist.AssistStructure
import android.content.SharedPreferences
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.util.Log
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mempass.common.Constants
import com.example.mempass.ui.theme.MempassTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class AutofillAuthActivity : ComponentActivity() {
    private var structure: AssistStructure? = null

    @Inject
    @Named("SecurityPrefs")
    lateinit var securityPrefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SECURITY FIX (#9): Apply screen security if enabled in settings
        val isScreenSecurityEnabled = securityPrefs.getBoolean(Constants.KEY_SCREEN_SECURITY_ENABLED, true)
        if (isScreenSecurityEnabled) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        structure = intent.getParcelableExtra(android.view.autofill.AutofillManager.EXTRA_ASSIST_STRUCTURE)

        setContent {
            val viewModel: VaultViewModel = hiltViewModel()
            MempassTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.unlock_mempass), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        
                        var pin by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6) pin = it },
                            label = { Text(stringResource(R.string.master_pin_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Button(
                            onClick = { handleUnlock(pin, viewModel) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.unlock_button_text))
                        }
                    }
                }
            }
        }
    }

    private fun handleUnlock(pin: String, viewModel: VaultViewModel) {
        val pinChars = pin.toCharArray()
        if (viewModel.unlockVault(pinChars)) {
            Arrays.fill(pinChars, ' ')
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val packageName = structure?.activityComponent?.packageName ?: ""
                    
                    var webDomain: String? = null
                    val fields = mutableMapOf<String, AutofillId>()
                    
                    structure?.let { 
                        for (i in 0 until it.windowNodeCount) {
                            val root = it.getWindowNodeAt(i).rootViewNode
                            findFieldsAndDomain(root, fields) { domain ->
                                if (webDomain == null) webDomain = domain
                            }
                        }
                    }

                    val allPasswords = viewModel.allPasswords.first()
                    
                    val matchesWithScores = allPasswords.map { entry ->
                        entry to MemPassAutofillService.calculateMatchScore(entry, packageName, webDomain)
                    }.filter { pair -> 
                        pair.second >= 0.6f 
                    }.sortedByDescending { pair -> 
                        pair.second 
                    }

                    if (matchesWithScores.isNotEmpty()) {
                        val match = matchesWithScores.first().first
                        val key = viewModel.getVaultKey()
                        if (key != null) {
                            try {
                                val usernameChars = CryptoUtils.decryptToChars(match.encryptedUsername, key)
                                val passwordChars = CryptoUtils.decryptToChars(match.encryptedPassword, key)
                                
                                val username = String(usernameChars)
                                val password = String(passwordChars)
                                
                                Arrays.fill(usernameChars, ' ')
                                Arrays.fill(passwordChars, ' ')

                                val responseBuilder = FillResponse.Builder()
                                val presentation = RemoteViews(this@AutofillAuthActivity.packageName, R.layout.autofill_inline_suggestion).apply {
                                    setTextViewText(R.id.autofill_service, match.serviceName)
                                    setTextViewText(R.id.autofill_username, username)
                                }

                                val datasetBuilder = Dataset.Builder(presentation)
                                fields.forEach { (type, id) ->
                                    val value = if (type == "password") password else username
                                    datasetBuilder.setValue(id, AutofillValue.forText(value))
                                }

                                val response = responseBuilder.addDataset(datasetBuilder.build()).build()
                                
                                val replyIntent = android.content.Intent().apply {
                                    putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
                                }
                                setResult(RESULT_OK, replyIntent)
                                finish()
                            } catch (e: Exception) {
                                Log.e("AutofillAuth", "Failed to prepare response")
                                finish()
                            }
                        }
                    } else {
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("AutofillAuth", "Error during auth fill")
                    finish()
                }
            }
        }
    }

    private fun findFieldsAndDomain(node: AssistStructure.ViewNode, fields: MutableMap<String, AutofillId>, onDomainFound: (String) -> Unit) {
        node.webDomain?.let { onDomainFound(it) }
        
        val hints = node.autofillHints?.map { it.lowercase() }
        val idEntry = node.idEntry?.lowercase() ?: ""
        
        if (node.autofillId != null) {
            val isPassword = hints?.any { it.contains("password") } == true || idEntry.contains("password")
            val isUsername = hints?.any { it.contains("username") || it.contains("email") } == true || 
                             idEntry.contains("username") || idEntry.contains("email") || idEntry.contains("login")

            if (isPassword) fields["password"] = node.autofillId!!
            else if (isUsername) fields["username"] = node.autofillId!!
        }

        for (i in 0 until node.childCount) {
            findFieldsAndDomain(node.getChildAt(i), fields, onDomainFound)
        }
    }
}
