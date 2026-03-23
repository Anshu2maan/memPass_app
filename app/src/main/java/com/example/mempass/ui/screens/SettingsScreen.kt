package com.example.mempass.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mempass.R
import com.example.mempass.VaultViewModel
import com.example.mempass.ui.components.ImportBackupDialog
import com.example.mempass.ui.components.ExportBackupDialog
import com.example.mempass.ui.components.SettingsItem
import com.example.mempass.ui.components.SettingsSection
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.util.Arrays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: VaultViewModel,
    onNavigateToLogs: () -> Unit = {},
    onNavigateToRecovery: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showDriveRestoreDialog by remember { mutableStateOf(false) }
    var isOverwriteMode by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            // Manual import still needs a dialog for password
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.security)) {
                    SettingsItem(
                        title = stringResource(R.string.theme),
                        icon = Icons.Default.Palette,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToTheme
                    )
                    SettingsItem(
                        title = stringResource(R.string.recovery_key),
                        icon = Icons.Default.VpnKey,
                        iconColor = MaterialTheme.colorScheme.primary,
                        onClick = onNavigateToRecovery
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.backup_sync)) {
                    SettingsItem(
                        title = stringResource(R.string.export_vault),
                        icon = Icons.Default.Backup,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        onClick = { showExportDialog = true }
                    )
                    SettingsItem(
                        title = stringResource(R.string.import_local),
                        icon = Icons.Default.Restore,
                        iconColor = MaterialTheme.colorScheme.secondary,
                        onClick = { importLauncher.launch(arrayOf("*/*")) }
                    )
                    SettingsItem(
                        title = stringResource(R.string.google_drive_sync),
                        icon = Icons.Default.CloudUpload,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        onClick = {
                            val account = GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) {
                                viewModel.performDriveSync(context, account) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Please sign in to Google Drive", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SettingsItem(
                        title = stringResource(R.string.restore_drive),
                        icon = Icons.Default.CloudDownload,
                        iconColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { showDriveRestoreDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.history)) {
                    SettingsItem(
                        title = stringResource(R.string.view_logs),
                        icon = Icons.Default.History,
                        iconColor = MaterialTheme.colorScheme.outline,
                        onClick = onNavigateToLogs
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportBackupDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { passChars ->
                viewModel.exportVaultManual(context, passChars) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    Arrays.fill(passChars, ' ')
                }
                showExportDialog = false
            }
        )
    }

    if (showDriveRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreDialog = false },
            title = { Text("Restore from Drive") },
            text = { 
                Column {
                    Text("This will download the latest backup from Google Drive and merge it with your local data.")
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = isOverwriteMode, onCheckedChange = { isOverwriteMode = it })
                        Text("Overwrite current data")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        viewModel.restoreFromDrive(context, account, overwrite = isOverwriteMode) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    showDriveRestoreDialog = false
                }) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
