package com.example.mempass.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.example.mempass.DriveBackupWorker
import com.example.mempass.R
import com.example.mempass.VaultViewModel
import com.example.mempass.ImportResult
import com.example.mempass.common.Constants
import com.example.mempass.ui.components.*
import com.example.mempass.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController, viewModel: VaultViewModel) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val sharedPrefs = context.getSharedPreferences(Constants.PREFS_SECURITY, Context.MODE_PRIVATE)
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    
    var biometricEnabled by remember { mutableStateOf(viewModel.biometricHelper.isBiometricEnabled()) }
    var autoBackupEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(Constants.KEY_AUTO_BACKUP_ENABLED, false)) }
    var screenSecurityEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(Constants.KEY_SCREEN_SECURITY_ENABLED, true)) }
    var lastBackupTime by remember { mutableLongStateOf(sharedPrefs.getLong(Constants.KEY_LAST_BACKUP_TIME, 0L)) }
    
    var showBiometricConfirmDialog by remember { mutableStateOf(false) }
    var showRecoveryKeyDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showDriveBackupDialog by remember { mutableStateOf(false) }
    var showDriveRestoreDialog by remember { mutableStateOf(false) }
    var showGenDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showExportHistoryDialog by remember { mutableStateOf(false) }
    var showImportSuccessDialog by remember { mutableStateOf<ImportResult?>(null) }
    
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }
    var isOverwriteMode by remember { mutableStateOf(false) }
    val isProcessing by viewModel.isBackupProcessing.collectAsState()

    fun getGoogleSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            showImportDialog = true
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) showDriveBackupDialog = true
        } catch (e: ApiException) {
            Toast.makeText(context, context.getString(R.string.signin_failed), Toast.LENGTH_SHORT).show()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) showDriveRestoreDialog = true
        } catch (e: ApiException) {
            Toast.makeText(context, context.getString(R.string.signin_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Black) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box {
            Column(
                Modifier
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SyncStatusCard(
                    isSyncing = isProcessing,
                    lastBackupTime = lastBackupTime,
                    onSyncNow = { backupLauncher.launch(getGoogleSignInIntent()) }
                )

                // --- SECURITY SECTION ---
                SettingsSection(title = stringResource(R.string.security_privacy)) {
                    SettingsItem(
                        title = stringResource(R.string.change_master_pin),
                        description = stringResource(R.string.change_master_pin_desc),
                        icon = Icons.Default.VpnKey,
                        iconColor = BrandIndigo,
                        onClick = { showChangePinDialog = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.biometric_unlock),
                        description = stringResource(R.string.biometric_unlock_desc),
                        icon = Icons.Default.Fingerprint,
                        iconColor = BrandIndigo,
                        trailingContent = {
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) showBiometricConfirmDialog = true
                                    else {
                                        viewModel.biometricHelper.setBiometricEnabled(false)
                                        biometricEnabled = false
                                    }
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.screen_privacy),
                        description = stringResource(R.string.screen_privacy_desc),
                        icon = Icons.Default.Screenshot,
                        iconColor = BrandIndigo,
                        trailingContent = {
                            Switch(
                                checked = screenSecurityEnabled,
                                onCheckedChange = { enabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    screenSecurityEnabled = enabled
                                    sharedPrefs.edit().putBoolean(Constants.KEY_SCREEN_SECURITY_ENABLED, enabled).apply()
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.recovery_key),
                        description = stringResource(R.string.recovery_key_settings_desc),
                        icon = Icons.Default.Password,
                        iconColor = BrandIndigo,
                        onClick = { showRecoveryKeyDialog = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.intruder_alerts),
                        description = stringResource(R.string.intruder_alerts_desc),
                        icon = Icons.Default.PhotoCamera,
                        iconColor = BrandRose,
                        onClick = { navController.navigate("intruder_logs") }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.lock_vault),
                        icon = Icons.Default.Lock,
                        iconColor = BrandRose,
                        onClick = { viewModel.lockVault(); navController.navigate("unlock") { popUpTo("main") { inclusive = true } } }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.password_generator),
                        description = stringResource(R.string.password_generator_desc),
                        icon = Icons.Default.AutoFixHigh,
                        iconColor = BrandIndigo,
                        onClick = { showGenDialog = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.autofill_settings),
                        description = stringResource(R.string.autofill_settings_desc),
                        icon = Icons.Default.SettingsSuggest,
                        iconColor = BrandIndigo,
                        onClick = { SettingsUtils.openAutofillSettings(context) }
                    )
                }

                // --- BACKUP & SYNC SECTION ---
                SettingsSection(title = stringResource(R.string.backup_sync)) {
                    SettingsItem(
                        title = stringResource(R.string.incremental_auto_backup),
                        description = stringResource(R.string.incremental_auto_backup_desc),
                        icon = Icons.Default.CloudSync,
                        iconColor = BrandIndigo,
                        trailingContent = {
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    autoBackupEnabled = enabled
                                    sharedPrefs.edit().putBoolean(Constants.KEY_AUTO_BACKUP_ENABLED, enabled).apply()
                                    if (enabled) DriveBackupWorker.schedule(context)
                                    else DriveBackupWorker.cancel(context)
                                }
                            )
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.restore_drive),
                        description = stringResource(R.string.restore_drive), 
                        icon = Icons.Default.SettingsBackupRestore,
                        iconColor = BrandAmber,
                        onClick = { restoreLauncher.launch(getGoogleSignInIntent()) }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.export_local),
                        description = stringResource(R.string.export_local_desc),
                        icon = Icons.Default.SdStorage,
                        iconColor = BrandIndigo,
                        onClick = { showExportDialog = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.import_local),
                        description = stringResource(R.string.import_local),
                        icon = Icons.Default.FileOpen,
                        iconColor = BrandEmerald,
                        onClick = { importLauncher.launch("*/*") }
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.export_history),
                        description = stringResource(R.string.export_history_title),
                        icon = Icons.Default.History,
                        iconColor = BrandIndigo,
                        onClick = { showExportHistoryDialog = true }
                    )
                }

                // --- DANGER ZONE ---
                SettingsSection(title = stringResource(R.string.danger_zone), titleColor = BrandRose) {
                    SettingsItem(
                        title = stringResource(R.string.factory_reset),
                        description = stringResource(R.string.factory_reset_desc),
                        icon = Icons.Default.DeleteForever,
                        iconColor = BrandRose,
                        onClick = { showResetDialog = true }
                    )
                }
                
                Spacer(Modifier.height(40.dp))
            }
            
            if (isProcessing) {
                LoadingOverlay(title = stringResource(R.string.syncing_cloud), description = stringResource(R.string.syncing_desc))
            }
        }
    }

    // --- Modularized Dialog Triggers ---
    if (showChangePinDialog) {
        ChangePinDialog(viewModel = viewModel, onDismiss = { showChangePinDialog = false })
    }

    if (showRecoveryKeyDialog) {
        RecoveryKeySettingsDialog(viewModel = viewModel, onDismiss = { showRecoveryKeyDialog = false })
    }

    if (showExportHistoryDialog) {
        ExportHistoryDialog(viewModel = viewModel, onDismiss = { showExportHistoryDialog = false })
    }

    if (showBiometricConfirmDialog) {
        BiometricConfirmDialog(
            viewModel = viewModel,
            onDismiss = { showBiometricConfirmDialog = false },
            onSuccess = { pinChars ->
                viewModel.biometricHelper.encryptPinWithBiometric(
                    activity, pinChars,
                    onSuccess = { 
                        biometricEnabled = true
                        showBiometricConfirmDialog = false 
                        Arrays.fill(pinChars, ' ')
                    },
                    onError = { err -> 
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        showBiometricConfirmDialog = false 
                        Arrays.fill(pinChars, ' ')
                    }
                )
            }
        )
    }

    if (showExportDialog) {
        ExportBackupDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { passChars ->
                viewModel.exportVaultManual(context, passChars) { _, msg -> 
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show() 
                    Arrays.fill(passChars, ' ')
                }
                showExportDialog = false
            }
        )
    }

    if (showDriveBackupDialog) {
        ExportBackupDialog(
            onDismiss = { showDriveBackupDialog = false },
            onConfirm = { passChars ->
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    viewModel.performDriveSync(context, account, passChars) { _, msg -> 
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show() 
                        Arrays.fill(passChars, ' ')
                    }
                } else {
                    Arrays.fill(passChars, ' ')
                }
                showDriveBackupDialog = false
            }
        )
    }

    if (showDriveRestoreDialog) {
        ImportBackupDialog(
            isOverwriteMode = isOverwriteMode,
            onOverwriteToggle = { isOverwriteMode = it },
            onDismiss = { showDriveRestoreDialog = false },
            onConfirm = { passChars ->
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    viewModel.restoreFromDrive(context, account, passChars, overwrite = isOverwriteMode) { _, msg -> 
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show() 
                        Arrays.fill(passChars, ' ')
                    }
                } else {
                    Arrays.fill(passChars, ' ')
                }
                showDriveRestoreDialog = false
            }
        )
    }

    if (showImportDialog) {
        ImportBackupDialog(
            isOverwriteMode = isOverwriteMode,
            onOverwriteToggle = { isOverwriteMode = it },
            onDismiss = { showImportDialog = false },
            onConfirm = { passChars ->
                selectedImportUri?.let { uri ->
                    viewModel.importVaultManual(context, uri, passChars, overwrite = isOverwriteMode,
                        onComplete = { _, pCount, dCount, nCount ->
                            showImportSuccessDialog = ImportResult(pCount, dCount, nCount)
                            showImportDialog = false
                            Arrays.fill(passChars, ' ')
                        },
                        onError = { err -> 
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show() 
                            Arrays.fill(passChars, ' ')
                        }
                    )
                } ?: Arrays.fill(passChars, ' ')
            }
        )
    }

    showImportSuccessDialog?.let { result ->
        ImportSuccessDialog(result) { showImportSuccessDialog = null }
    }

    if (showGenDialog) {
        PasswordGeneratorSettingsDialog(clipboardManager) { showGenDialog = false }
    }

    if (showResetDialog) {
        FactoryResetDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = { viewModel.destroyVault { activity.finish() } }
        )
    }
}

@Composable
fun SyncStatusCard(
    isSyncing: Boolean,
    lastBackupTime: Long,
    onSyncNow: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    PremiumCard {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).background(BrandIndigo.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CloudSync, 
                    null, 
                    tint = BrandIndigo,
                    modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.google_drive_sync), fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(
                    if (lastBackupTime == 0L) stringResource(R.string.never_backed_up) 
                    else stringResource(R.string.last_backup_format, SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(lastBackupTime))),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
            ) {
                Text(stringResource(R.string.sync_now), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, titleColor: Color = MaterialTheme.colorScheme.primary, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(title, color = titleColor, fontWeight = FontWeight.Black, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        PremiumCard {
            Column { content() }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    description: String? = null,
    icon: ImageVector,
    iconColor: Color,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (description != null) {
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (onClick != null) {
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
}

@Composable
fun BulletPoint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun LoadingOverlay(title: String, description: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        PremiumCard(Modifier.padding(40.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = BrandIndigo)
                Spacer(Modifier.height(24.dp))
                Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(description, textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}
