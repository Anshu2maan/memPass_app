package com.example.mempass.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.mempass.CryptoUtils
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
    
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val existingNote = remember(editId, notes) { if (editId != null) notes.find { it.id == editId } else null }

    var headline by remember(existingNote) { mutableStateOf(existingNote?.title ?: "") }
    var content by remember(existingNote) { 
        val chars = if(existingNote != null) viewModel.decryptToChars(existingNote.encryptedContent) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    var category by remember(existingNote) { mutableStateOf(existingNote?.category ?: "") }
    var isLocked by remember(existingNote) { mutableStateOf(existingNote?.isLocked ?: false) }
    var isFavorite by remember(existingNote) { mutableStateOf(existingNote?.isFavorite ?: false) }
    var selfDestructAt by remember(existingNote) { mutableStateOf(existingNote?.selfDestructAt) }
    var attachedFiles by remember(existingNote) { 
        val initialPaths = existingNote?.snippetFilePaths?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
        mutableStateOf(initialPaths)
    }
    var isChecklist by remember(existingNote) { mutableStateOf(existingNote?.isChecklist ?: false) }
    
    val newlyAddedFiles = remember { mutableStateListOf<String>() }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val hasChanges = remember(headline, content, category, isLocked, isFavorite, selfDestructAt, attachedFiles, isChecklist) {
        val initialContentChars = if(existingNote != null) viewModel.decryptToChars(existingNote.encryptedContent) else CharArray(0)
        val initialContent = String(initialContentChars)
        CryptoUtils.wipe(initialContentChars)
        
        headline != (existingNote?.title ?: "") ||
        content != initialContent ||
        category != (existingNote?.category ?: "") ||
        isLocked != (existingNote?.isLocked ?: false) ||
        isFavorite != (existingNote?.isFavorite ?: false) ||
        selfDestructAt != existingNote?.selfDestructAt ||
        isChecklist != (existingNote?.isChecklist ?: false) ||
        attachedFiles != (existingNote?.snippetFilePaths?.split("|")?.filter { it.isNotEmpty() } ?: emptyList<String>())
    }

    val onExit: () -> Unit = {
        if (hasChanges) showExitConfirmation = true else navController.popBackStack()
    }

    BackHandler(onBack = onExit)

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch {
            uris.forEach { uri ->
                val file = viewModel.uriToFile(uri)
                val path = viewModel.encryptAndSave(file, "note_file_${UUID.randomUUID()}")
                if (path.isNotEmpty()) {
                    attachedFiles = attachedFiles + path
                    newlyAddedFiles.add(path)
                }
            }
        }
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_desc)) },
            confirmButton = {
                TextButton(onClick = { 
                    newlyAddedFiles.forEach { viewModel.deleteOrphanedFile(it) }
                    showExitConfirmation = false
                    navController.popBackStack() 
                }) {
                    Text(stringResource(R.string.discard), color = BrandRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text(stringResource(R.string.keep_editing)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if(editId == null) stringResource(R.string.create_note) else stringResource(R.string.edit_note), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                                isChecklist = isChecklist,
                                snippetFilePaths = attachedFiles,
                                selfDestructAt = selfDestructAt,
                                isLocked = isLocked,
                                isFavorite = isFavorite,
                                id = editId ?: 0
                            ) {
                                isSaving = false
                                navController.popBackStack()
                            }
                        }) {
                            Text(if(editId == null) stringResource(R.string.save) else stringResource(R.string.update_details), color = BrandIndigo, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = headline,
                    onValueChange = { headline = it },
                    label = { Text(stringResource(R.string.headline)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandIndigo,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                Spacer(Modifier.height(16.dp))
                
                if (isChecklist) {
                    ChecklistEditor(
                        content = content,
                        onContentChange = { content = it }
                    )
                } else {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text(stringResource(R.string.secrets_placeholder)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandIndigo,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )
                }
                
                Spacer(Modifier.height(20.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 20.dp)
                ) {
                    item {
                        FilterChip(
                            selected = category.isNotEmpty(),
                            onClick = { showCategorySheet = true },
                            label = { Text(if(category.isEmpty()) stringResource(R.string.category) else category) },
                            leadingIcon = { Icon(Icons.Default.Category, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = isChecklist,
                            onClick = { isChecklist = !isChecklist },
                            label = { Text(stringResource(R.string.checklist)) },
                            leadingIcon = { Icon(Icons.Default.Checklist, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = isLocked,
                            onClick = { isLocked = !isLocked },
                            label = { Text(if(isLocked) stringResource(R.string.lock) else stringResource(R.string.unlock)) },
                            leadingIcon = { Icon(if(isLocked) Icons.Default.Lock else Icons.Default.LockOpen, null, Modifier.size(16.dp)) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selfDestructAt != null,
                            onClick = { showTimerDialog = true },
                            label = { Text(stringResource(R.string.timer)) },
                            leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(16.dp)) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { fileLauncher.launch("*/*") },
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
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(attachedFiles) { path ->
                            Box(Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                val thumbnailState = produceState<android.graphics.Bitmap?>(null, path) {
                                    value = viewModel.getVaultKey()?.let { viewModel.fileUtils.getThumbnail(path, 300, 300) }
                                }
                                thumbnailState.value?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                    ?: Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                
                                IconButton(onClick = { 
                                    attachedFiles = attachedFiles.filter { it != path }
                                    if (newlyAddedFiles.contains(path)) {
                                        viewModel.deleteOrphanedFile(path)
                                        newlyAddedFiles.remove(path)
                                    }
                                }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp).padding(4.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
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
            Text(stringResource(R.string.select_category), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp))
            LazyRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = hours.toLongOrNull() ?: 24
                        selfDestructAt = System.currentTimeMillis() + (h * 60 * 60 * 1000)
                        showTimerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.set_timer)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimerDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun ChecklistEditor(content: String, onContentChange: (String) -> Unit) {
    // We use a state that holds the actual list of strings to avoid excessive re-compositions and string manipulations
    val items = remember(content) { 
        if (content.isBlank()) mutableStateListOf("[ ] ") 
        else {
            val list = mutableStateListOf<String>()
            list.addAll(content.split("\n"))
            list
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            key(index) { // Use key for better Lazy-like performance in Column
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val isChecked = item.startsWith("[x] ")
                    val cleanText = item.removePrefix("[x] ").removePrefix("[ ] ")
                    
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            items[index] = if(checked) "[x] $cleanText" else "[ ] $cleanText"
                            onContentChange(items.joinToString("\n"))
                        },
                        colors = CheckboxDefaults.colors(checkedColor = BrandIndigo)
                    )
                    
                    TextField(
                        value = cleanText,
                        onValueChange = { newText ->
                            items[index] = if(isChecked) "[x] $newText" else "[ ] $newText"
                            onContentChange(items.joinToString("\n"))
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.checklist_item)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    IconButton(onClick = {
                        items.removeAt(index)
                        if(items.isEmpty()) items.add("[ ] ")
                        onContentChange(items.joinToString("\n"))
                    }) {
                        Icon(Icons.Default.Delete, null, tint = BrandRose.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        TextButton(
            onClick = {
                items.add("[ ] ")
                onContentChange(items.joinToString("\n"))
            },
            colors = ButtonDefaults.textButtonColors(contentColor = BrandIndigo)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_item), fontWeight = FontWeight.Medium)
        }
    }
}
