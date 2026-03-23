package com.example.mempass.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text(
                            "Secure Notes", 
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "${notes.size} memories captured",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate("add_note") 
                },
                containerColor = BrandIndigo,
                contentColor = Color.White,
                shape = RoundedCornerShape(22.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(28.dp)) }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ModernSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Search notes, tags, or categories..."
                )
            }

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ModernEmptyState(Icons.AutoMirrored.Filled.Notes, "No notes yet. Start writing your legacy.")
                }
            } else {
                val filtered = remember(notes, searchQuery) {
                    notes.filter { 
                        it.title.contains(searchQuery, true) || 
                        it.category.contains(searchQuery, true) ||
                        it.tags.contains(searchQuery, true)
                    }.sortedWith(compareByDescending<NoteEntry> { it.isFavorite }.thenByDescending { it.createdAt })
                }

                val pinnedNotes = filtered.filter { it.isFavorite }
                val otherNotes = filtered.filter { !it.isFavorite }

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalItemSpacing = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pinnedNotes.isNotEmpty() && searchQuery.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            SectionHeader("Pinned", Icons.Default.PushPin)
                        }
                        item(span = StaggeredGridItemSpan.FullLine) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(pinnedNotes, key = { it.id }) { note ->
                                    PinnedNoteCard(
                                        note = note,
                                        isUnlocked = unlockedIds.contains(note.id),
                                        viewModel = viewModel,
                                        onClick = { 
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedNote = note 
                                        }
                                    )
                                }
                            }
                        }
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader("All Notes", Icons.AutoMirrored.Filled.Notes)
                        }
                    }

                    items(if (searchQuery.isNotEmpty()) filtered else otherNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note, 
                            isUnlocked = unlockedIds.contains(note.id),
                            viewModel = viewModel,
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedNote = note 
                            }
                        )
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
                navController.navigate("add_note?id=${selectedNote!!.id}")
                selectedNote = null
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(8.dp))
        Text(
            title.uppercase(), 
            style = MaterialTheme.typography.labelLarge, 
            fontWeight = FontWeight.Black, 
            color = MaterialTheme.colorScheme.outline,
            letterSpacing = 1.5.sp
        )
    }
}

@Composable
fun PinnedNoteCard(note: NoteEntry, isUnlocked: Boolean, viewModel: NoteViewModel, onClick: () -> Unit) {
    val cardColor = remember(note.colorHex) {
        try { Color(android.graphics.Color.parseColor(note.colorHex)) } catch (e: Exception) { Color.Transparent }
    }

    Card(
        modifier = Modifier
            .width(180.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cardColor != Color.Transparent) cardColor.copy(alpha = 0.25f) 
                             else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.5.dp, if(cardColor != Color.Transparent) cardColor.copy(alpha = 0.4f) else BrandIndigo.copy(alpha = 0.1f))
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            Column {
                Text(
                    note.title.ifEmpty { "Untitled" },
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    note.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = if(cardColor != Color.Transparent) cardColor.copy(alpha = 0.8f) else BrandIndigo,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                Icons.Default.PushPin, 
                null, 
                Modifier.align(Alignment.BottomEnd).size(14.dp), 
                tint = if(cardColor != Color.Transparent) cardColor.copy(alpha = 0.6f) else BrandIndigo.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun NoteCard(note: NoteEntry, isUnlocked: Boolean, viewModel: NoteViewModel, onClick: () -> Unit) {
    val dateStr = remember(note.createdAt) { 
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(note.createdAt))
    }
    val cardColor = remember(note.colorHex) {
        try { Color(android.graphics.Color.parseColor(note.colorHex)) } catch (e: Exception) { Color.Transparent }
    }
    var previewText by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(isUnlocked, note.id) {
        if (isUnlocked && !note.isLocked) {
            val chars = viewModel.decryptToChars(note.encryptedContent)
            previewText = String(chars).take(100)
            CryptoUtils.wipe(chars)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (cardColor != Color.Transparent) cardColor.copy(alpha = 0.15f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (cardColor != Color.Transparent) cardColor.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    color = if(cardColor != Color.Transparent) cardColor.copy(alpha = 0.2f) else BrandIndigo.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        note.category.uppercase(), 
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 9.sp, 
                        fontWeight = FontWeight.Black, 
                        letterSpacing = 1.sp,
                        color = if(cardColor != Color.Transparent) cardColor.copy(alpha = 0.9f) else BrandIndigo
                    )
                }
                if (note.isLocked) Icon(Icons.Default.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
            }

            Spacer(Modifier.height(12.dp))
            
            Text(
                text = note.title.ifEmpty { "Untitled Note" }, 
                fontWeight = FontWeight.Bold, 
                fontSize = 17.sp, 
                lineHeight = 22.sp
            )

            if (!note.isLocked || isUnlocked) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = previewText ?: "No content",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    fontFamily = when(note.fontFamily) {
                        "Serif" -> FontFamily.Serif
                        "Monospace" -> FontFamily.Monospace
                        else -> FontFamily.Default
                    }
                )
            }

            Spacer(Modifier.height(20.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                if (note.snippetFilePaths.isNotEmpty()) {
                    Icon(Icons.Default.AttachFile, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
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
    val haptic = LocalHapticFeedback.current
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
        title = note.title.ifEmpty { "Secure Note" },
        onDismiss = onDismiss,
        onDelete = { 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.deleteNote(note)
            onDismiss() 
        },
        onEdit = onEdit,
        onFavoriteToggle = { 
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.toggleFavorite(note) 
        },
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
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp)
                ) {
                    Icon(Icons.Default.EnhancedEncryption, null, Modifier.padding(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(24.dp))
                Text("Vulnerability Shield Active", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This note is wrapped in AES-256 encryption. Authentication required to view.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(32.dp))
                
                var showPinDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { showPinDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(56.dp).fillMaxWidth(0.8f)
                ) {
                    Icon(Icons.Default.Fingerprint, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Unlock Secure Entry", fontWeight = FontWeight.ExtraBold)
                }

                if (showPinDialog) {
                    var pinInput by remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { showPinDialog = false },
                        title = { Text("Vault Access") },
                        text = {
                            OutlinedTextField(
                                value = pinInput,
                                onValueChange = { if(it.length <= 6) pinInput = it },
                                label = { Text("Enter Master PIN") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                if (viewModel.unlockVault(pinInput.toCharArray())) {
                                    isUnlocked = true
                                    showPinDialog = false
                                } else {
                                    Toast.makeText(context, "Access Denied", Toast.LENGTH_SHORT).show()
                                }
                            }, shape = RoundedCornerShape(12.dp)) { Text("Authenticate") }
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
                        fontSize = note.fontSize.sp,
                        lineHeight = (note.fontSize * 1.6).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = when(note.fontFamily) {
                            "Serif" -> FontFamily.Serif
                            "Monospace" -> FontFamily.Monospace
                            else -> FontFamily.Default
                        }
                    )
                }
            }

            if (paths.isNotEmpty()) {
                Text(
                    "VAULT ASSETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = BrandIndigo,
                    modifier = Modifier.padding(top = 32.dp, bottom = 12.dp),
                    letterSpacing = 1.5.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(paths) { path ->
                        Card(
                            modifier = Modifier.size(width = 300.dp, height = 220.dp).clip(RoundedCornerShape(24.dp)).clickable { 
                                viewModel.getVaultKey()?.let { viewModel.sharingUtils.viewFile(path, it, "Asset_${System.currentTimeMillis()}") }
                            },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                val thumbnailState = produceState<Bitmap?>(null, path) {
                                    value = viewModel.getVaultKey()?.let { viewModel.fileUtils.getThumbnail(path, 800, 600) }
                                }
                                if(thumbnailState.value != null) Image(thumbnailState.value!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(12.dp))
                                    Text(path.substringAfterLast("/"), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistDisplay(content: String) {
    val lines = content.split("\n")
    Column(modifier = Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        lines.forEach { line ->
            if (line.isNotBlank()) {
                val isChecked = line.startsWith("[x] ")
                val text = line.removePrefix("[x] ").removePrefix("[ ] ")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        tint = if (isChecked) BrandIndigo else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = text,
                        fontSize = 17.sp,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
