package com.example.mempass.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
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
                val filtered = notes.filter { it.title.contains(searchQuery, true) || it.category.contains(searchQuery, true) }
                    .sortedByDescending { it.createdAt }

                LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filtered) { note ->
                        NoteCard(note = note, onClick = { selectedNote = note })
                    }
                }
            }
        }
    }

    if (selectedNote != null) {
        NoteDetailDialog(
            note = selectedNote!!,
            viewModel = viewModel,
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
                        if(note.isLocked) Icons.Default.Lock else Icons.AutoMirrored.Filled.Notes,
                        null,
                        Modifier.padding(8.dp).size(20.dp),
                        tint = BrandIndigo
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(note.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(note.category, fontSize = 12.sp, color = BrandIndigo, fontWeight = FontWeight.Medium)
                }
                if (note.selfDestructAt != null) {
                    Icon(Icons.Default.Timer, null, tint = BrandRose, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun NoteDetailDialog(note: NoteEntry, viewModel: NoteViewModel, onDismiss: () -> Unit, onEdit: () -> Unit) {
    var content by remember { mutableStateOf<String?>(null) }
    var isUnlocked by remember { mutableStateOf(!note.isLocked) }
    val paths = note.snippetFilePaths.split("|").filter { it.isNotEmpty() }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            content = String(viewModel.decryptToChars(note.encryptedContent))
        }
    }

    DetailDialog(
        title = note.title,
        onDismiss = onDismiss,
        onDelete = { viewModel.deleteNote(note); onDismiss() },
        onEdit = onEdit
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
                Button(
                    onClick = { /* ViewModel already handles unlock via PIN if integrated */ isUnlocked = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.unlock))
                }
            }
        } else {
            content?.let {
                Text(
                    it,
                    modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
