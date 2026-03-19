package com.example.mempass.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.example.mempass.R
import com.example.mempass.VaultViewModel
import com.example.mempass.ui.theme.*
import com.example.mempass.utils.ClipboardUtils
import java.util.Arrays

@Composable
fun SetupScreen(navController: NavHostController, viewModel: VaultViewModel) {
    var pin by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf<CharArray?>(null) }
    var isKeyVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(context, context.getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    if (recoveryKey != null) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.save_recovery_key), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    Text(stringResource(R.string.recovery_key_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(16.dp),
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
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pinChars = pin.toCharArray()
                    viewModel.setTempPin(pinChars)
                    Arrays.fill(pinChars, ' ')
                    Arrays.fill(recoveryKey!!, ' ')
                    navController.navigate("biometric_setup")
                    recoveryKey = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.saved_it)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    ClipboardUtils.copyToClipboard(context, "Recovery Key", String(recoveryKey!!))
                    Toast.makeText(context, context.getString(R.string.copy_label_copied, "Recovery Key"), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.copy_key), color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    PinBaseScreen(
        title = stringResource(R.string.create_vault),
        subtitle = stringResource(R.string.create_vault_subtitle),
        pin = pin,
        onPinChange = { pin = it },
        onComplete = {
            val pinChars = pin.toCharArray()
            recoveryKey = viewModel.setupVault(pinChars)
            Arrays.fill(pinChars, ' ')
        },
        navController = navController,
        viewModel = viewModel
    )
}

@Composable
fun UnlockScreen(navController: NavHostController, viewModel: VaultViewModel) {
    val activity = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var showRecoveryFlow by remember { mutableStateOf(false) }
    var showBiometricOffer by remember { mutableStateOf(false) }
    var authenticatedPinChars by remember { mutableStateOf<CharArray?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.cameraHelper.captureIntruderPhoto(activity) { /* Saved */ }
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.biometricHelper.isBiometricEnabled()) {
            viewModel.biometricHelper.decryptPinWithBiometric(
                activity,
                onSuccess = { pinChars ->
                    if (viewModel.unlockVault(pinChars)) {
                        Arrays.fill(pinChars, ' ')
                        navController.navigate("main") {
                            popUpTo("unlock") { inclusive = true }
                        }
                    } else {
                        Arrays.fill(pinChars, ' ')
                    }
                },
                onError = { /* Fallback to PIN */ }
            )
        }
    }

    if (showBiometricOffer) {
        AlertDialog(
            onDismissRequest = { 
                authenticatedPinChars?.let { Arrays.fill(it, ' ') }
                authenticatedPinChars = null
                navController.navigate("main") { popUpTo("unlock") { inclusive = true } } 
            },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.enable_biometric), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.biometric_desc), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
            confirmButton = {
                Button(onClick = {
                    authenticatedPinChars?.let { 
                        viewModel.setTempPin(it)
                        Arrays.fill(it, ' ')
                    }
                    authenticatedPinChars = null
                    navController.navigate("biometric_setup") { popUpTo("unlock") { inclusive = true } }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(stringResource(R.string.enable_now)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    authenticatedPinChars?.let { Arrays.fill(it, ' ') }
                    authenticatedPinChars = null
                    navController.navigate("main") { popUpTo("unlock") { inclusive = true } }
                }) { Text(stringResource(R.string.skip_for_now), color = Color.Gray) }
            }
        )
    }

    if (showRecoveryFlow) {
        RecoveryFlowDialog(
            onDismiss = { showRecoveryFlow = false },
            onSuccess = {
                showRecoveryFlow = false
                navController.navigate("main") { popUpTo("unlock") { inclusive = true } }
            },
            viewModel = viewModel
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            PinBaseScreen(
                title = stringResource(R.string.welcome_back),
                subtitle = stringResource(R.string.unlock_subtitle),
                pin = pin,
                onPinChange = { pin = it; error = false },
                isError = error,
                onComplete = {
                    val currentPinChars = pin.toCharArray()
                    if (viewModel.unlockVault(currentPinChars)) {
                        authenticatedPinChars = currentPinChars // Transferred ownership
                        if (!viewModel.biometricHelper.isBiometricSetup() && viewModel.biometricHelper.isBiometricEnrolled()) {
                            showBiometricOffer = true
                        } else {
                            Arrays.fill(currentPinChars, ' ')
                            navController.navigate("main") {
                                popUpTo("unlock") { inclusive = true }
                            }
                        }
                    } else {
                        Arrays.fill(currentPinChars, ' ')
                        error = true
                        pin = ""
                        attempts++
                        if (attempts >= 3) {
                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.cameraHelper.captureIntruderPhoto(activity) { /* Saved */ }
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                },
                navController = navController,
                viewModel = viewModel
            )
        }
        Text(
            stringResource(R.string.forgot_pin),
            Modifier.fillMaxWidth().clickable { showRecoveryFlow = true }.padding(bottom = 24.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun RecoveryFlowDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: VaultViewModel
) {
    var step by remember { mutableIntStateOf(1) } // 1: Key, 2: New PIN
    var recoveryKeyInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler { if (step > 1) step-- else onDismiss() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (step == 1) Icons.Default.VpnKey else Icons.Default.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(if (step == 1) R.string.enter_recovery_key else R.string.set_new_pin),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (step == 1) R.string.recovery_key_desc else R.string.create_vault_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(Modifier.height(24.dp))

                if (step == 1) {
                    OutlinedTextField(
                        value = recoveryKeyInput,
                        onValueChange = { recoveryKeyInput = it.uppercase() },
                        label = { Text(stringResource(R.string.recovery_key_label)) },
                        placeholder = { Text(stringResource(R.string.recovery_key_placeholder)) },
                        isError = error != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(6) { index ->
                            val isFilled = index < newPin.length
                            Box(
                                Modifier.size(40.dp).border(1.dp, if(isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isFilled) Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            }
                        }
                    }
                    // Hidden number pad for PIN
                    Box(Modifier.height(200.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items((1..9).toList() + listOf(0)) { num ->
                                TextButton(onClick = { if(newPin.length < 6) newPin += num.toString() }) {
                                    Text(num.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            item { IconButton(onClick = { if(newPin.isNotEmpty()) newPin = newPin.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, null) } }
                        }
                    }
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = {
                            if (step == 1) {
                                if (recoveryKeyInput.length < 16) {
                                    error = context.getString(R.string.invalid_key_length)
                                } else {
                                    step = 2
                                    error = null
                                }
                            } else {
                                if (newPin.length == 6) {
                                    val rkChars = recoveryKeyInput.toCharArray()
                                    val pinChars = newPin.toCharArray()
                                    if (viewModel.resetPinWithRecoveryKey(rkChars, pinChars)) {
                                        Arrays.fill(rkChars, ' ')
                                        Arrays.fill(pinChars, ' ')
                                        onSuccess()
                                    } else {
                                        Arrays.fill(rkChars, ' ')
                                        Arrays.fill(pinChars, ' ')
                                        error = context.getString(R.string.invalid_recovery_key)
                                        step = 1
                                    }
                                }
                            }
                        },
                        Modifier.weight(1f),
                        enabled = if(step == 1) recoveryKeyInput.isNotEmpty() else newPin.length == 6,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(if(step == 1) R.string.next else R.string.reset_unlock))
                    }
                }
            }
        }
    }
}

@Composable
fun PinBaseScreen(
    title: String,
    subtitle: String,
    pin: String,
    onPinChange: (String) -> Unit,
    isError: Boolean = false,
    onComplete: () -> Unit,
    navController: NavHostController,
    viewModel: VaultViewModel
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as FragmentActivity

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.5f))
        Icon(Icons.Default.Security, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(48.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { index ->
                val isFilled = index < pin.length
                Box(
                    Modifier.size(48.dp).border(2.dp, if(isError) BrandRose else if(isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).background(if(isFilled) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isFilled) {
                        Box(Modifier.size(12.dp).background(if(isError) BrandRose else MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
            }
        }

        if (isError) {
            Text(stringResource(R.string.invalid_pin), color = BrandRose, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }

        Spacer(Modifier.weight(1f))

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "FINGERPRINT", "0", "DEL")
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(keys) { key ->
                if (key.isNotEmpty()) {
                    Box(
                        Modifier.height(64.dp).clip(RoundedCornerShape(16.dp)).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (key == "DEL") {
                                if (pin.isNotEmpty()) onPinChange(pin.dropLast(1))
                            } else if (key == "FINGERPRINT") {
                                if (viewModel.biometricHelper.isBiometricEnabled()) {
                                    viewModel.biometricHelper.decryptPinWithBiometric(
                                        activity,
                                        onSuccess = { pinChars ->
                                            if (viewModel.unlockVault(pinChars)) {
                                                Arrays.fill(pinChars, ' ')
                                                navController.navigate("main") {
                                                    popUpTo("unlock") { inclusive = true }
                                                }
                                            } else {
                                                Arrays.fill(pinChars, ' ')
                                            }
                                        },
                                        onError = { Toast.makeText(activity, it, Toast.LENGTH_SHORT).show() }
                                    )
                                } else {
                                    val msg = if (viewModel.biometricHelper.isBiometricEnrolled()) {
                                        activity.getString(R.string.login_with_pin_to_enable)
                                    } else {
                                        activity.getString(R.string.biometric_not_enabled)
                                    }
                                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else if (pin.length < 6) {
                                val newPin = pin + key
                                onPinChange(newPin)
                                if (newPin.length == 6) onComplete()
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "DEL" -> Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = MaterialTheme.colorScheme.onBackground)
                            "FINGERPRINT" -> Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                            else -> Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
    }
}
