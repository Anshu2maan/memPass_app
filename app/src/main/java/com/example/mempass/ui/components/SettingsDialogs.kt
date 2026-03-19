package com.example.mempass.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mempass.ImportResult
import com.example.mempass.PasswordGenerator
import com.example.mempass.R
import com.example.mempass.VaultViewModel
import com.example.mempass.ui.theme.BrandIndigo
import com.example.mempass.ui.theme.BrandRose
import com.example.mempass.utils.ClipboardUtils
import java.util.Arrays

@Composable
fun RecoveryKeySettingsDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: PIN Auth, 2: Actions
    var pinInput by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf<CharArray?>(null) }
    var isKeyVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VpnKey, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if(step == 1) stringResource(R.string.confirm_master_pin) else stringResource(R.string.recovery_key_options),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if(step == 1) stringResource(R.string.manage_recovery_key_desc) else stringResource(R.string.recovery_key_private_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(24.dp))

                if (step == 1) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if(it.length <= 6) pinInput = it },
                        label = { Text(stringResource(R.string.master_pin_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val pinChars = pinInput.toCharArray()
                            if (viewModel.unlockVault(pinChars)) {
                                step = 2
                            } else {
                                Toast.makeText(context, context.getString(R.string.invalid_pin), Toast.LENGTH_SHORT).show()
                            }
                            Arrays.fill(pinChars, ' ')
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.verify))
                    }
                } else {
                    if (recoveryKey == null) {
                        Button(
                            onClick = { recoveryKey = viewModel.getCurrentRecoveryKey() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.view_current_key))
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { recoveryKey = viewModel.regenerateRecoveryKey() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.generate_new_key))
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isKeyVisible) String(recoveryKey!!) else stringResource(R.string.masked_recovery_key),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val recoveryKeyLabel = stringResource(R.string.recovery_key)
                            OutlinedButton(
                                onClick = {
                                    ClipboardUtils.copyToClipboard(context, recoveryKeyLabel, String(recoveryKey!!))
                                    Toast.makeText(context, context.getString(R.string.copy_label_copied, recoveryKeyLabel), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.copy)) }
                            Button(
                                onClick = {
                                    Arrays.fill(recoveryKey!!, ' ')
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text(stringResource(R.string.done)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChangePinDialog(
    viewModel: VaultViewModel,
    onDismiss: () -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    
    var isRotating by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var rotationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    
    val context = LocalContext.current

    if (rotationResult != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (rotationResult!!.first) stringResource(R.string.success) else stringResource(R.string.error)) },
            text = { Text(rotationResult!!.second) },
            confirmButton = {
                Button(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        )
    } else if (isRotating) {
        Dialog(onDismissRequest = {}) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandIndigo)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.rotating_master_key), fontWeight = FontWeight.Bold)
                    Text(progressMessage, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.change_master_pin), fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.change_pin_rotate_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { if (it.length <= 6) oldPin = it },
                        label = { Text(stringResource(R.string.current_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6) newPin = it },
                        label = { Text(stringResource(R.string.new_pin_6_digits)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6) confirmPin = it },
                        label = { Text(stringResource(R.string.confirm_new_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin != confirmPin) {
                            Toast.makeText(context, context.getString(R.string.pins_dont_match), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPin.length < 6) {
                            Toast.makeText(context, context.getString(R.string.pin_must_6_digits), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        isRotating = true
                        val oldChars = oldPin.toCharArray()
                        val newChars = newPin.toCharArray()
                        
                        viewModel.rotateMasterKey(
                            oldPin = oldChars,
                            newPin = newChars,
                            onProgress = { progressMessage = it },
                            onComplete = { success, msg ->
                                rotationResult = Pair(success, msg)
                                isRotating = false
                                Arrays.fill(oldChars, ' ')
                                Arrays.fill(newChars, ' ')
                            }
                        )
                    }
                ) { Text(stringResource(R.string.update_rotate)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun BiometricConfirmDialog(viewModel: VaultViewModel, onDismiss: () -> Unit, onSuccess: (CharArray) -> Unit) {
    val context = LocalContext.current
    var confirmPin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.confirm_pin_for_bio), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6) confirmPin = it },
                    label = { Text(stringResource(R.string.master_pin_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val pinChars = confirmPin.toCharArray()
                if (viewModel.unlockVault(pinChars)) {
                    onSuccess(pinChars)
                } else {
                    Arrays.fill(pinChars, ' ')
                    Toast.makeText(context, context.getString(R.string.invalid_pin), Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) } }
    )
}

@Composable
fun ExportBackupDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    val context = LocalContext.current
    var backupPassword by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.backup_protect_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(stringResource(R.string.backup_protect_desc), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (backupPassword.length >= 4) {
                    val passChars = backupPassword.toCharArray()
                    onConfirm(passChars)
                } else {
                    Toast.makeText(context, context.getString(R.string.password_too_short), Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.export_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) } }
    )
}

@Composable
fun ImportBackupDialog(isOverwriteMode: Boolean, onOverwriteToggle: (Boolean) -> Unit, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var backupPassword by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.import_backup_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(stringResource(R.string.import_backup_desc), color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isOverwriteMode, onCheckedChange = onOverwriteToggle)
                    Text(stringResource(R.string.overwrite_vault_data), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val passChars = backupPassword.toCharArray()
                onConfirm(passChars)
            }) { Text(stringResource(R.string.decrypt_import)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) } }
    )
}

@Composable
fun ImportSuccessDialog(result: ImportResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.import_success_title), color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(stringResource(R.string.import_success_desc), color = MaterialTheme.colorScheme.onSurface)
                BulletPoint("${stringResource(R.string.passwords)}: ${result.p}")
                BulletPoint("${stringResource(R.string.documents)}: ${result.d}")
                BulletPoint("${stringResource(R.string.notes)}: ${result.n}")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.awesome)) } }
    )
}

@Composable
fun PasswordGeneratorSettingsDialog(clipboardManager: ClipboardManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var len by remember { mutableFloatStateOf(16f) }
    var genPass by remember { mutableStateOf(PasswordGenerator.generateRandomPassword(16)) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.strong_gen_title), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(String(genPass), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.pass_length, len.toInt()), color = MaterialTheme.colorScheme.onSurface)
                Slider(value = len, onValueChange = { len = it; genPass = PasswordGenerator.generateRandomPassword(it.toInt()) }, valueRange = 8f..32f)
            }
        },
        confirmButton = { 
            val passwordLabel = stringResource(R.string.password_label)
            Button(onClick = { 
                ClipboardUtils.copyToClipboard(context, passwordLabel, String(genPass))
                Toast.makeText(context, context.getString(R.string.copy_label_copied, passwordLabel), Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(stringResource(R.string.copy_close)) } 
        },
        dismissButton = { TextButton(onClick = { genPass = PasswordGenerator.generateRandomPassword(len.toInt()) }) { Text(stringResource(R.string.regenerate), color = MaterialTheme.colorScheme.primary) } }
    )
}

@Composable
fun FactoryResetDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.confirm_reset_title), color = BrandRose) },
        text = { Text(stringResource(R.string.confirm_reset_desc), color = MaterialTheme.colorScheme.onSurface) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = BrandRose)) { Text(stringResource(R.string.destroy_everything)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.Gray) } }
    )
}

@Composable
fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
