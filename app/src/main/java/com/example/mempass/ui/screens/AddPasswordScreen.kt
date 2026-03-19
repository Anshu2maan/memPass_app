package com.example.mempass.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.mempass.CryptoUtils
import com.example.mempass.PasswordGenerator
import com.example.mempass.PasswordViewModel
import com.example.mempass.R
import com.example.mempass.TotpHelper
import com.example.mempass.ui.components.*
import com.example.mempass.ui.theme.*
import java.util.Arrays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPasswordScreen(navController: NavHostController, viewModel: PasswordViewModel, editId: Int = 0) {
    val context = LocalContext.current
    val passwords by viewModel.allPasswords.collectAsState(initial = emptyList())
    val existing = if(editId != 0) passwords.find { it.id == editId } else null

    var service by remember(existing) { mutableStateOf(existing?.serviceName ?: "") }
    var user by remember(existing) { 
        val chars = if(existing != null) viewModel.decryptToChars(existing.encryptedUsername) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    var pass by remember(existing) { 
        val chars = if(existing != null) viewModel.decryptToChars(existing.encryptedPassword) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    var notes by remember(existing) { 
        val chars = if(existing != null) viewModel.decryptToChars(existing.encryptedNotes) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    var totpSecret by remember(existing) { 
        val chars = if(existing?.encryptedTotpSecret != null) viewModel.decryptToChars(existing.encryptedTotpSecret!!) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    
    var showDeterministicDialog by remember { mutableStateOf(false) }
    var showRandomDialog by remember { mutableStateOf(false) }
    var isPassVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { 
            TopAppBar(
                title = { Text(stringResource(if(editId == 0) R.string.new_password else R.string.edit_account), fontWeight = FontWeight.Bold) }, 
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }, 
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            ) 
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState())) {
            PremiumTextField(value = service, onValueChange = { service = it }, label = stringResource(R.string.service_label), icon = Icons.Default.Language)
            Spacer(Modifier.height(16.dp))
            PremiumTextField(value = user, onValueChange = { user = it }, label = stringResource(R.string.username_email), icon = Icons.Default.Person)
            Spacer(Modifier.height(16.dp))
            
            Column {
                OutlinedTextField(
                    value = pass, 
                    onValueChange = { pass = it }, 
                    label = { Text(stringResource(R.string.password_label)) },
                    leadingIcon = { Icon(Icons.Default.Password, null, tint = BrandIndigo) },
                    trailingIcon = {
                        IconButton(onClick = { isPassVisible = !isPassVisible }) {
                            Icon(if(isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = Color.Gray)
                        }
                    },
                    visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp), 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!viewModel.isUnlocked()) {
                                Toast.makeText(context, context.getString(R.string.vault_locked_error), Toast.LENGTH_SHORT).show()
                            } else if (service.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.service_empty_error), Toast.LENGTH_SHORT).show()
                            } else {
                                showDeterministicDialog = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrandIndigo)
                    ) {
                        Icon(Icons.Default.VpnKey, null, modifier = Modifier.size(18.dp), tint = BrandIndigo)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.deterministic), fontSize = 12.sp, color = BrandIndigo)
                    }

                    Button(
                        onClick = { showRandomDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.random), fontSize = 12.sp)
                    }
                }
            }
            
            Text(
                stringResource(R.string.deterministic_desc),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            if (pass.isNotEmpty()) {
                PasswordStrengthInfo(pass)
            }

            Spacer(Modifier.height(16.dp))
            PremiumTextField(
                value = totpSecret, 
                onValueChange = { totpSecret = it.uppercase().replace(" ", "") }, 
                label = stringResource(R.string.totp_secret_label), 
                icon = Icons.Default.VpnKey
            )
            if (totpSecret.isNotEmpty()) {
                val secretChars = totpSecret.toCharArray()
                if (!TotpHelper.isValidSecret(secretChars)) {
                    Text(stringResource(R.string.invalid_totp_secret), color = BrandRose, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp))
                }
                CryptoUtils.wipe(secretChars)
            }
            
            Spacer(Modifier.height(16.dp))
            PremiumTextField(value = notes, onValueChange = { notes = it }, label = stringResource(R.string.private_notes), icon = Icons.AutoMirrored.Filled.Notes)

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { 
                    if(service.isNotEmpty()) { 
                        val userChars = user.toCharArray()
                        val passChars = pass.toCharArray()
                        val notesChars = notes.toCharArray()
                        val totpChars = if(totpSecret.isEmpty()) null else totpSecret.toCharArray()

                        viewModel.savePassword(service, userChars, passChars, notesChars, totpChars, id = editId)
                        
                        CryptoUtils.wipe(userChars, passChars, notesChars, totpChars)

                        navController.popBackStack() 
                    } 
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) { Text(stringResource(if(editId == 0) R.string.save_securely else R.string.update_details), fontWeight = FontWeight.Bold) }
        }
    }

    if (showDeterministicDialog) {
        DeterministicPasswordDialog(
            service = service,
            viewModel = viewModel,
            onDismiss = { showDeterministicDialog = false },
            onPasswordGenerated = { 
                pass = it
                showDeterministicDialog = false
            }
        )
    }

    if (showRandomDialog) {
        PasswordGeneratorDialog(
            onDismiss = { showRandomDialog = false },
            onUsePassword = { 
                pass = it
                showRandomDialog = false
            }
        )
    }
}

@Composable
fun PasswordStrengthInfo(password: String) {
    val passChars = password.toCharArray()
    val info = PasswordGenerator.calculateStrengthDetailed(passChars)
    CryptoUtils.wipe(passChars)
    
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { info.score }, 
        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), 
        color = if(info.score < 0.5f) BrandRose else if(info.score < 0.8f) BrandAmber else BrandEmerald,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
    Text(stringResource(R.string.strength_label, info.label), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(info.score < 0.5f) BrandRose else if(info.score < 0.8f) BrandAmber else BrandEmerald)
    
    if (info.crackTimeDisplay.isNotEmpty()) {
        Text(stringResource(R.string.crack_time, info.crackTimeDisplay), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }

    if (info.warning != null) {
        Text("⚠️ ${info.warning}", fontSize = 11.sp, color = BrandRose, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }

    info.suggestions.forEach { suggestion ->
        Text("• $suggestion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun DeterministicPasswordDialog(
    service: String,
    viewModel: PasswordViewModel,
    onDismiss: () -> Unit,
    onPasswordGenerated: (String) -> Unit
) {
    val context = LocalContext.current
    var masterPinInput by remember { mutableStateOf("") }
    var detVersion by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.gen_det_pass_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(stringResource(R.string.gen_det_pass_desc, service), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = masterPinInput,
                    onValueChange = { if(it.length <= 6) masterPinInput = it },
                    label = { Text(stringResource(R.string.master_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.algo_version), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(12.dp))
                    AssistChip(onClick = { detVersion = if(detVersion == 1) 2 else 1 }, label = { Text(stringResource(R.string.version_format, detVersion), color = MaterialTheme.colorScheme.onSurface) })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (masterPinInput.length < 4) {
                    Toast.makeText(context, context.getString(R.string.pin_short_error), Toast.LENGTH_SHORT).show()
                } else {
                    val pinChars = masterPinInput.toCharArray()
                    if (viewModel.unlockVault(pinChars)) {
                        val generated = PasswordGenerator.generateDeterministicPassword(
                            masterPhrase = pinChars,
                            service = service,
                            version = detVersion,
                            length = 16
                        )
                        onPasswordGenerated(String(generated))
                        CryptoUtils.wipe(generated)
                        Toast.makeText(context, context.getString(R.string.det_pass_gen_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.invalid_master_pin), Toast.LENGTH_SHORT).show()
                    }
                    CryptoUtils.wipe(pinChars)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)) {
                Text(stringResource(R.string.generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.Gray)
            }
        }
    )
}
