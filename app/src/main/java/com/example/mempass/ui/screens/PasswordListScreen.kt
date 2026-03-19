package com.example.mempass.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.mempass.CryptoUtils
import com.example.mempass.PasswordEntry
import com.example.mempass.PasswordViewModel
import com.example.mempass.R
import com.example.mempass.TotpHelper
import com.example.mempass.ui.components.*
import com.example.mempass.ui.theme.BrandIndigo
import com.example.mempass.ui.theme.BrandRose
import com.example.mempass.utils.ClipboardUtils
import kotlinx.coroutines.delay
import java.util.Arrays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordListScreen(navController: NavHostController, viewModel: PasswordViewModel) {
    val passwords by viewModel.allPasswords.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var selectedEntry by remember { mutableStateOf<PasswordEntry?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.passwords), fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { navController.navigate("add_password") }, 
                containerColor = BrandIndigo, 
                contentColor = Color.White, 
                shape = RoundedCornerShape(20.dp)
            ) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.padding(20.dp)) { 
                ModernSearchBar(
                    query = searchQuery, 
                    onQueryChange = { searchQuery = it }, 
                    placeholder = stringResource(R.string.search_accounts)
                ) 
            }
            if (passwords.isEmpty()) {
                ModernEmptyState(Icons.Default.VpnKey, stringResource(R.string.vault_empty))
            } else {
                val filtered = passwords.filter { it.serviceName.contains(searchQuery, true) }
                    .sortedWith(compareByDescending<PasswordEntry> { it.isFavorite }.thenBy { it.serviceName.lowercase() })
                
                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedEntry = entry },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                ServiceIcon(entry.serviceName)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.serviceName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                    val userChars = viewModel.decryptToChars(entry.encryptedUsername)
                                    val user = String(userChars)
                                    CryptoUtils.wipe(userChars)
                                    Text(user, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                if (entry.isFavorite) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedEntry != null) {
        PasswordDetailDialog(
            entry = selectedEntry!!,
            viewModel = viewModel,
            onDismiss = { selectedEntry = null },
            onEdit = { 
                navController.navigate("add_password?editId=${selectedEntry!!.id}")
                selectedEntry = null
            }
        )
    }
}

@Composable
fun PasswordDetailDialog(entry: PasswordEntry, viewModel: PasswordViewModel, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var isPasswordVisible by remember { mutableStateOf(false) }
    var totpCode by remember { mutableStateOf("------") }
    var isTimeAccurate by remember { mutableStateOf(true) }

    LaunchedEffect(entry) {
        val secretChars = if(entry.encryptedTotpSecret != null) viewModel.decryptToChars(entry.encryptedTotpSecret!!) else CharArray(0)
        if (secretChars.isNotEmpty()) {
            while (true) {
                isTimeAccurate = TotpHelper.isTimeAutomatic(context)
                totpCode = TotpHelper.generateTotp(secretChars)
                delay(1000)
            }
        }
        CryptoUtils.wipe(secretChars)
    }

    DetailDialog(
        title = entry.serviceName, 
        onDismiss = onDismiss, 
        onDelete = { viewModel.deletePassword(entry); onDismiss() },
        onEdit = onEdit
    ) {
        val decryptedUsernameChars = viewModel.decryptToChars(entry.encryptedUsername)
        val decryptedUsername = String(decryptedUsernameChars)
        CryptoUtils.wipe(decryptedUsernameChars)
        
        DetailItem(stringResource(R.string.username_email), decryptedUsername, Icons.Default.Person) {
            ClipboardUtils.copyToClipboard(context, "Username", decryptedUsername)
            Toast.makeText(context, context.getString(R.string.copy_label_copied, "Username"), Toast.LENGTH_SHORT).show()
        }
        
        val decryptedPassChars = viewModel.decryptToChars(entry.encryptedPassword)
        val decryptedPass = String(decryptedPassChars)
        CryptoUtils.wipe(decryptedPassChars)
        
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.password_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }, modifier = Modifier.size(24.dp)) {
                    Icon(if(isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.size(14.dp), tint = BrandIndigo)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { 
                    ClipboardUtils.copyToClipboard(context, "Password", decryptedPass)
                    Toast.makeText(context, context.getString(R.string.copy_label_copied, "Password"), Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = BrandIndigo)
                }
            }
            Text(if(isPasswordVisible) decryptedPass else "••••••••", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }

        if (totpCode != "------") {
            Column(Modifier.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.totp_code_label), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { 
                        ClipboardUtils.copyToClipboard(context, "OTP", totpCode)
                        Toast.makeText(context, context.getString(R.string.copy_label_copied, "OTP"), Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = BrandIndigo)
                    }
                }
                Text(totpCode, fontSize = 24.sp, fontWeight = FontWeight.Black, color = BrandIndigo, letterSpacing = 2.sp)
                
                if (!isTimeAccurate) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(12.dp), tint = BrandRose)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.time_sync_warning),
                            fontSize = 10.sp,
                            color = BrandRose,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
        
        val decryptedNotesChars = viewModel.decryptToChars(entry.encryptedNotes)
        val notes = String(decryptedNotesChars)
        CryptoUtils.wipe(decryptedNotesChars)
        if(notes.isNotEmpty()) DetailItem(stringResource(R.string.private_notes), notes, Icons.AutoMirrored.Filled.Notes)
    }
}
