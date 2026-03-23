package com.example.mempass.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.mempass.CryptoUtils
import com.example.mempass.NoteEntry
import com.example.mempass.NoteViewModel
import com.example.mempass.R
import com.example.mempass.ui.components.DetailDialog
import com.example.mempass.ui.components.ModernEmptyState
import com.example.mempass.ui.components.ModernSearchBar
import com.example.mempass.ui.theme.BrandIndigo
import com.example.mempass.ui.theme.BrandRose
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(navController: NavHostController, viewModel: NoteViewModel = hiltViewModel()) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val unlockedIds by viewModel.unlockedNoteIds.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<NoteEntry?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.secure_notes), fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                onClick = { navController.navigate("add_note") },
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
                    placeholder = stringResource(R.string.search_notes)
                )
            }

            if (notes.isEmpty()) {
                ModernEmptyState(Icons.AutoMirrored.Filled.Notes, stringResource(R.string.no_notes))
            } else {
                val filtered = remember(notes, searchQuery) {
                    notes.filter { 
                        it.title.contains(searchQuery, true) || 
                        it.category.contains(searchQuery, true) ||
                        it.tags.contains(searchQuery, true)
                    }.sortedWith(compareByDescending<NoteEntry> { it.isFavorite }.thenByDescending { it.createdAt })
                }

                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered, key = { it.id }) { note ->
                        NoteCard(note = note, onClick = { selectedNote = note })
                    }
                }
            }
        }
    }

    if (selectedNote != null) {
        val currentNote = notes.find { it.id == selectedNote!!.id } ?: selectedNote!!
        NoteDetailDialog(
            note = currentNote,
            viewModel = viewModel,
            isInitiallyUnlocked = unlockedIds.contains(currentNote.id),
            onDismiss = { selectedNote = null },
            onEdit = {
                navController.navigate("add_note?editId=${selectedNote!!.id}")
                selectedNote = null
            }
        )
    }
}

@Composable
fun NoteCard(note: NoteEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = BrandIndigo.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        if(note.isLocked) Icons.Default.Lock else if(note.isChecklist) Icons.Default.Checklist else Icons.AutoMirrored.Filled.Notes,
                        null,
                        Modifier.padding(8.dp).size(20.dp),
                        tint = BrandIndigo
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(note.title.ifEmpty { stringResource(R.string.untitled) }, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(note.category, fontSize = 12.sp, color = BrandIndigo, fontWeight = FontWeight.Medium)
                }
                if (note.isFavorite) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB800), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                if (note.selfDestructAt != null) {
                    Icon(Icons.Default.Timer, null, tint = BrandRose, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun NoteDetailDialog(note: NoteEntry, viewModel: NoteViewModel, isInitiallyUnlocked: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var content by remember { mutableStateOf<String?>(null) }
    var isUnlocked by remember { mutableStateOf(!note.isLocked || isInitiallyUnlocked) }
    val paths = note.snippetFilePaths.split("|").filter { it.isNotEmpty() }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            val chars = viewModel.decryptToChars(note.encryptedContent)
            content = String(chars)
            CryptoUtils.wipe(chars)
            if (note.isLocked) viewModel.markNoteAsUnlocked(note.id)
        }
    }

    DetailDialog(
        title = note.title.ifEmpty { stringResource(R.string.untitled) },
        onDismiss = onDismiss,
        onDelete = { viewModel.deleteNote(note); onDismiss() },
        onEdit = onEdit,
        onFavoriteToggle = { viewModel.toggleFavorite(note) },
        isFavorite = note.isFavorite
    ) {
        if (note.selfDestructAt != null) {
            val remaining = note.selfDestructAt!! - System.currentTimeMillis()
            if (remaining > 0) {
                val hours = (remaining / (1000 * 60 * 60))
                val minutes = (remaining / (1000 * 60)) % 60
                Surface(
                    color = BrandRose.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = BrandRose, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.self_destruct_timer, hours, minutes),
                            color = BrandRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!isUnlocked) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.locked_note_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.locked_note_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                
                var showPinDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { showPinDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.unlock))
                }

                if (showPinDialog) {
                    var pinInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showPinDialog = false },
                        title = { Text(stringResource(R.string.unlock_vault)) },
                        text = {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if(it.length <= 6) pinInput = it },
                                label = { Text(stringResource(R.string.master_pin_label)) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (viewModel.unlockVault(pinInput.toCharArray())) {
                                    isUnlocked = true
                                    showPinDialog = false
                                } else {
                                    Toast.makeText(context, context.getString(R.string.invalid_pin), Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(stringResource(R.string.unlock)) }
                        }
                    )
                }
            }
        } else {
            content?.let { text ->
                if (note.isChecklist) {
                    ChecklistDisplay(text)
                } else {
                    Text(
                        text,
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (paths.isNotEmpty()) {
                Text(
                    stringResource(R.string.attachments),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    items(paths) { path ->
                        Card(
                            modifier = Modifier.size(240.dp, 180.dp).clip(RoundedCornerShape(20.dp)).clickable { 
                                viewModel.getVaultKey()?.let { viewModel.sharingUtils.viewFile(path, it, "Note_File_${System.currentTimeMillis()}.${path.substringAfterLast(".")}") }
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                val thumbnailState = produceState<Bitmap?>(null, path) {
                                    value = viewModel.getVaultKey()?.let { viewModel.fileUtils.getThumbnail(path, 400, 300) }
                                }
                                if(thumbnailState.value != null) Image(thumbnailState.value!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Icon(if(path.lowercase(Locale.ROOT).endsWith(".pdf")) Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun ChecklistDisplay(content: String) {
    val lines = content.split("\n")
    Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            if (line.isNotBlank()) {
                val isChecked = line.startsWith("[x] ")
                val text = line.removePrefix("[x] ").removePrefix("[ ] ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        null,
                        tint = if (isChecked) BrandIndigo else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }
        }
    }
}
