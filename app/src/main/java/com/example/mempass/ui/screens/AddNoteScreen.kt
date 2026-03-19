package com.example.mempass.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.mempass.NoteEntry
import com.example.mempass.NoteViewModel
import com.example.mempass.R
import com.example.mempass.ui.theme.BrandIndigo
import com.example.mempass.ui.theme.BrandRose
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNoteScreen(navController: NavHostController, viewModel: NoteViewModel = hiltViewModel(), editId: Int? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var headline by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var isLocked by remember { mutableStateOf(false) }
    var selfDestructAt by remember { mutableStateOf<Long?>(null) }
    var attachedFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            uris.forEach { uri ->
                val file = viewModel.uriToFile(uri)
                val path = viewModel.encryptAndSave(file, "note_file_${UUID.randomUUID()}")
                if (path.isNotEmpty()) attachedFiles = attachedFiles + path
            }
        }
    }

    LaunchedEffect(editId) {
        if (editId != null) {
            viewModel.allNotes.collect { notes ->
                notes.find { it.id == editId }?.let { note ->
                    headline = note.title
                    content = String(viewModel.decryptToChars(note.encryptedContent))
                    category = note.category
                    isLocked = note.isLocked
                    selfDestructAt = note.selfDestructAt
                    attachedFiles = note.snippetFilePaths.split("|").filter { it.isNotEmpty() }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if(editId == null) stringResource(R.string.create_note) else stringResource(R.string.edit_note), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp, color = BrandIndigo)
                    } else {
                        TextButton(onClick = {
                            isSaving = true
                            viewModel.saveNote(
                                title = headline,
                                content = content.toCharArray(),
                                category = category,
                                colorHex = "#FFFFFF",
                                snippetFilePaths = attachedFiles,
                                selfDestructAt = selfDestructAt,
                                isLocked = isLocked,
                                id = editId ?: 0
                            )
                            navController.popBackStack()
                        }) {
                            Text(stringResource(R.string.save), color = BrandIndigo, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = headline,
                onValueChange = { headline = it },
                label = { Text(stringResource(R.string.headline)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text(stringResource(R.string.secrets_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = category.isNotEmpty(),
                    onClick = { showCategorySheet = true },
                    label = { Text(if(category.isEmpty()) stringResource(R.string.category) else category) },
                    leadingIcon = { Icon(Icons.Default.Category, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = isLocked,
                    onClick = { isLocked = !isLocked },
                    label = { Text(if(isLocked) stringResource(R.string.lock) else stringResource(R.string.unlock)) },
                    leadingIcon = { Icon(if(isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = selfDestructAt != null,
                    onClick = { showTimerDialog = true },
                    label = { Text(stringResource(R.string.timer)) },
                    leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) }
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { 
                    fileLauncher.launch("*/*")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo.copy(alpha = 0.1f), contentColor = BrandIndigo)
            ) {
                Icon(Icons.Default.AttachFile, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.attach_files))
            }

            if (selfDestructAt != null) {
                Spacer(Modifier.height(16.dp))
                val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(selfDestructAt!!))
                Surface(color = BrandRose.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoDelete, null, tint = BrandRose, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.destruction_date, dateStr), fontSize = 13.sp, color = BrandRose, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { selfDestructAt = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, tint = BrandRose, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (attachedFiles.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(attachedFiles) { path ->
                        Box(Modifier.size(90.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            val thumbnailState = produceState<android.graphics.Bitmap?>(null, path) {
                                value = viewModel.getVaultKey()?.let { viewModel.fileUtils.getThumbnail(path, 200, 200) }
                            }
                            thumbnailState.value?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                ?: Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            
                            IconButton(onClick = { 
                                attachedFiles = attachedFiles.filter { it != path } 
                            }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp).padding(4.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            val categories = listOf(
                stringResource(R.string.cat_personal),
                stringResource(R.string.cat_work),
                stringResource(R.string.cat_finance),
                stringResource(R.string.cat_secrets),
                stringResource(R.string.cat_idea),
                stringResource(R.string.cat_checklist)
            )
            LazyRow(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat; showCategorySheet = false },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showTimerDialog) {
        var hours by remember { mutableStateOf("24") }
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            title = { Text(stringResource(R.string.self_destruct_timer_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.self_destruct_timer_desc), fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.duration_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = hours.toLongOrNull() ?: 24
                    selfDestructAt = System.currentTimeMillis() + (h * 60 * 60 * 1000)
                    showTimerDialog = false
                }) { Text(stringResource(R.string.set_custom_timer)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
