package com.example.mempass.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    val haptic = LocalHapticFeedback.current
    
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val existingNote = remember(editId, notes) { if (editId != null) notes.find { it.id == editId } else null }

    var headline by remember(existingNote) { mutableStateOf(existingNote?.title ?: "") }
    var content by remember(existingNote) { 
        val chars = if(existingNote != null) viewModel.decryptToChars(existingNote.encryptedContent) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    var category by remember(existingNote) { mutableStateOf(existingNote?.category ?: "General") }
    var colorHex by remember(existingNote) { mutableStateOf(existingNote?.colorHex ?: "#FFFFFF") }
    var fontFamily by remember(existingNote) { mutableStateOf(existingNote?.fontFamily ?: "Default") }
    var fontSize by remember(existingNote) { mutableStateOf(existingNote?.fontSize ?: 16.0f) }
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
    var showColorSheet by remember { mutableStateOf(false) }
    var showFontSheet by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val bgColor = remember(colorHex) {
        try { Color(android.graphics.Color.parseColor(colorHex)).copy(alpha = 0.12f) } catch (e: Exception) { Color.Transparent }
    }

    val selectedFont = when(fontFamily) {
        "Serif" -> FontFamily.Serif
        "Monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

    val hasChanges = remember(headline, content, category, colorHex, isLocked, isFavorite, selfDestructAt, attachedFiles, isChecklist, fontFamily, fontSize) {
        val initialContentChars = if(existingNote != null) viewModel.decryptToChars(existingNote.encryptedContent) else CharArray(0)
        val initialContent = String(initialContentChars)
        CryptoUtils.wipe(initialContentChars)
        
        headline != (existingNote?.title ?: "") ||
        content != initialContent ||
        category != (existingNote?.category ?: "General") ||
        colorHex != (existingNote?.colorHex ?: "#FFFFFF") ||
        fontFamily != (existingNote?.fontFamily ?: "Default") ||
        fontSize != (existingNote?.fontSize ?: 16.0f) ||
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
                val path = viewModel.encryptAndSave(file, "vault_asset_${UUID.randomUUID()}")
                if (path.isNotEmpty()) {
                    attachedFiles = attachedFiles + path
                    newlyAddedFiles.add(path)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = { 
                    IconButton(onClick = onExit) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) 
                    } 
                },
                actions = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isFavorite = !isFavorite 
                    }) {
                        Icon(
                            if (isFavorite) Icons.Default.PushPin else Icons.Default.PushPin,
                            null,
                            tint = if (isFavorite) BrandIndigo else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp, color = BrandIndigo)
                    } else {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSaving = true
                                viewModel.saveNote(
                                    title = headline,
                                    content = content.toCharArray(),
                                    category = category,
                                    colorHex = colorHex,
                                    isChecklist = isChecklist,
                                    snippetFilePaths = attachedFiles,
                                    selfDestructAt = selfDestructAt,
                                    isLocked = isLocked,
                                    isFavorite = isFavorite,
                                    fontFamily = fontFamily,
                                    fontSize = fontSize,
                                    id = editId ?: 0
                                ) {
                                    isSaving = false
                                    navController.popBackStack()
                                }
                            },
                            enabled = headline.isNotBlank() || content.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text("Secure Save", fontWeight = FontWeight.Black)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(vertical = 16.dp)) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item { ToolIcon(Icons.Default.Palette, "Theme", onClick = { showColorSheet = true }) }
                        item { ToolIcon(Icons.Default.TextFields, "Typography", onClick = { showFontSheet = true }) }
                        item { ToolIcon(Icons.Default.Category, "Label", onClick = { showCategorySheet = true }) }
                        item { 
                            ToolIcon(
                                if(isChecklist) Icons.Default.Checklist else Icons.Default.List, 
                                "List", 
                                active = isChecklist, 
                                onClick = { isChecklist = !isChecklist }
                            ) 
                        }
                        item { 
                            ToolIcon(
                                if(isLocked) Icons.Default.Https else Icons.Default.LockOpen, 
                                "Vault", 
                                active = isLocked, 
                                onClick = { isLocked = !isLocked }
                            ) 
                        }
                        item { ToolIcon(Icons.Default.Timer, "Destruct", active = selfDestructAt != null, onClick = { showTimerDialog = true }) }
                        item { ToolIcon(Icons.Default.AddPhotoAlternate, "Attach", onClick = { fileLauncher.launch("image/*") }) }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(bgColor)
        ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                TextField(
                    value = headline,
                    onValueChange = { headline = it },
                    placeholder = { 
                        Text(
                            "Entry Title", 
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                Spacer(Modifier.height(24.dp))
                
                if (isChecklist) {
                    ChecklistEditor(content = content, onContentChange = { content = it })
                } else {
                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        placeholder = { Text("Capture your thoughts...", style = MaterialTheme.typography.bodyLarge) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 500.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = (fontSize * 1.6).sp, 
                            fontSize = fontSize.sp,
                            fontFamily = selectedFont,
                            letterSpacing = 0.3.sp
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }

                if (attachedFiles.isNotEmpty()) {
                    Spacer(Modifier.height(40.dp))
                    Text("SECURE ASSETS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = BrandIndigo, letterSpacing = 2.sp)
                    Spacer(Modifier.height(20.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(attachedFiles) { path ->
                            AssetCard(
                                path = path,
                                viewModel = viewModel,
                                onRemove = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    attachedFiles = attachedFiles.filter { it != path }
                                }
                            )
                        }
                    }
                }
                
                if (selfDestructAt != null) {
                    SelfDestructBanner(
                        time = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(selfDestructAt!!)),
                        onCancel = { selfDestructAt = null }
                    )
                }
                
                Spacer(Modifier.height(100.dp))
            }
        }
    }

    // Ultra-Premium Sheets
    if (showFontSheet) {
        TypographySheet(
            currentFont = fontFamily,
            currentSize = fontSize,
            onFontSelect = { fontFamily = it },
            onSizeChange = { fontSize = it },
            onDismiss = { showFontSheet = false }
        )
    }

    if (showColorSheet) {
        PremiumColorSheet(
            selectedHex = colorHex,
            onSelect = { colorHex = it; showColorSheet = false },
            onDismiss = { showColorSheet = false }
        )
    }

    if (showCategorySheet) {
        PremiumCategorySheet(
            selected = category,
            onSelect = { category = it; showCategorySheet = false },
            onDismiss = { showCategorySheet = false }
        )
    }

    if (showTimerDialog) {
        SelfDestructTimerDialog(
            onSet = { hours ->
                selfDestructAt = System.currentTimeMillis() + (hours * 3600000)
                showTimerDialog = false
            },
            onDismiss = { showTimerDialog = false }
        )
    }
    
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Discard Entry?", fontWeight = FontWeight.Black) },
            text = { Text("You have unsaved changes in this secure entry. Discard them?") },
            confirmButton = {
                Button(
                    onClick = { 
                        newlyAddedFiles.forEach { viewModel.deleteOrphanedFile(it) }
                        navController.popBackStack() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRose),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Discard", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Keep Editing") }
            }
        )
    }
}

@Composable
fun ToolIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { 
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick() 
    }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if(active) BrandIndigo else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if(active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if(active) BrandIndigo else MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypographySheet(currentFont: String, currentSize: Float, onFontSelect: (String) -> Unit, onSizeChange: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp)) {
            Text("Typography Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(32.dp))
            
            Text("Font Family", style = MaterialTheme.typography.labelLarge, color = BrandIndigo, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Default", "Serif", "Monospace").forEach { font ->
                    FilterChip(
                        selected = currentFont == font,
                        onClick = { onFontSelect(font) },
                        label = { Text(font) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(32.dp))
            Text("Font Size", style = MaterialTheme.typography.labelLarge, color = BrandIndigo, fontWeight = FontWeight.Black)
            Slider(
                value = currentSize,
                onValueChange = onSizeChange,
                valueRange = 12f..24f,
                steps = 6,
                colors = SliderDefaults.colors(thumbColor = BrandIndigo, activeTrackColor = BrandIndigo)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Small", style = MaterialTheme.typography.labelSmall)
                Text("Large", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun AssetCard(path: String, viewModel: NoteViewModel, onRemove: () -> Unit) {
    Box(
        Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val thumbnailState = produceState<android.graphics.Bitmap?>(null, path) {
            value = viewModel.getVaultKey()?.let { viewModel.fileUtils.getThumbnail(path, 400, 400, it) }
        }
        thumbnailState.value?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.Description, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.DeleteOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SelfDestructBanner(time: String, onCancel: () -> Unit) {
    Surface(
        color = BrandRose.copy(alpha = 0.1f), 
        shape = RoundedCornerShape(20.dp), 
        modifier = Modifier.padding(top = 40.dp).fillMaxWidth(),
        border = BorderStroke(1.dp, BrandRose.copy(alpha = 0.2f))
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoDelete, null, tint = BrandRose)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Destruction Sequence Armed", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = BrandRose)
                Text("Will be wiped on $time", style = MaterialTheme.typography.bodySmall, color = BrandRose.copy(alpha = 0.8f))
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, null, tint = BrandRose, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumColorSheet(selectedHex: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        val colors = listOf("#FFFFFF", "#FEE2E2", "#FFEDD5", "#FEF9C3", "#DCFCE7", "#E0F2FE", "#E0E7FF", "#F5F3FF", "#FAE8FF")
        Column(Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp)) {
            Text("Canvas Color", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(colors) { hex ->
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(
                                width = if (selectedHex == hex) 4.dp else 1.dp,
                                color = if (selectedHex == hex) BrandIndigo else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .clickable { onSelect(hex) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumCategorySheet(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        val categories = listOf("General", "Personal", "Work", "Finance", "Secrets", "Ideas", "Checklist")
        Column(Modifier.padding(bottom = 48.dp)) {
            Text("Select Label", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(16.dp))
            categories.forEach { cat ->
                ListItem(
                    headlineContent = { Text(cat, fontWeight = if(selected == cat) FontWeight.Bold else FontWeight.Medium) },
                    leadingContent = { RadioButton(selected == cat, onClick = { onSelect(cat) }) },
                    modifier = Modifier.clickable { onSelect(cat) }
                )
            }
        }
    }
}

@Composable
fun SelfDestructTimerDialog(onSet: (Long) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableStateOf("24") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Self-Destruct", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Note will be permanently deleted after the set duration.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() } },
                    label = { Text("Hours from now") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSet(hours.toLongOrNull() ?: 24) }, shape = RoundedCornerShape(12.dp)) { Text("Set Timer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChecklistEditor(content: String, onContentChange: (String) -> Unit) {
    val items = remember(content) { 
        if (content.isBlank()) mutableStateListOf("[ ] ") 
        else {
            val list = mutableStateListOf<String>()
            list.addAll(content.split("\n"))
            list
        }
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, item ->
            key(index) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val isChecked = item.startsWith("[x] ")
                    val cleanText = item.removePrefix("[x] ").removePrefix("[ ] ")
                    
                    IconButton(onClick = {
                        items[index] = if(isChecked) "[ ] $cleanText" else "[x] $cleanText"
                        onContentChange(items.joinToString("\n"))
                    }) {
                        Icon(
                            if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            null,
                            tint = if (isChecked) BrandIndigo else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    
                    TextField(
                        value = cleanText,
                        onValueChange = { newText ->
                            items[index] = if(isChecked) "[x] $newText" else "[ ] $newText"
                            onContentChange(items.joinToString("\n"))
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("List item...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = if (isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    IconButton(onClick = {
                        items.removeAt(index)
                        if(items.isEmpty()) items.add("[ ] ")
                        onContentChange(items.joinToString("\n"))
                    }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        
        TextButton(
            onClick = {
                items.add("[ ] ")
                onContentChange(items.joinToString("\n"))
            },
            colors = ButtonDefaults.textButtonColors(contentColor = BrandIndigo),
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add item", fontWeight = FontWeight.Black)
        }
    }
}
