package com.example.mempass.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.navigation.NavHostController
import com.example.mempass.DocumentEntry
import com.example.mempass.DocumentViewModel
import com.example.mempass.R
import com.example.mempass.ui.components.QualityOption
import com.example.mempass.ui.components.QualityPickerDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(navController: NavHostController, viewModel: DocumentViewModel) {
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    val context = LocalContext.current
    
    // Multi-select State
    val selectedDocs = remember { mutableStateListOf<DocumentEntry>() }
    val isSelectionMode = selectedDocs.isNotEmpty()

    // Existing Dialog States
    var showQualityDialog by remember { mutableStateOf(false) }
    var showFileSelectionSheet by remember { mutableStateOf(false) }
    var selectedDocForShare by remember { mutableStateOf<DocumentEntry?>(null) }
    var selectedFilePathForShare by remember { mutableStateOf<String?>(null) }
    var selectedFileNameForShare by remember { mutableStateOf<String?>(null) }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val filteredDocuments = documents.filter {
        it.title.contains(searchQuery, ignoreCase = true) || 
        it.documentType.contains(searchQuery, ignoreCase = true)
    }

    // Batch Action Dialogs
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(stringResource(R.string.delete_entry_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDocuments(selectedDocs.toList())
                    selectedDocs.clear()
                    showBatchDeleteConfirm = false
                }) {
                    Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showQualityDialog && selectedFilePathForShare != null) {
        val originalSize = remember(selectedFilePathForShare) {
            File(selectedFilePathForShare!!).length() / 1024
        }
        
        QualityPickerDialog(
            titleRes = R.string.share_options,
            originalSizeKb = originalSize,
            onDismiss = { 
                showQualityDialog = false
                selectedFilePathForShare = null
            },
            onQualitySelected = { quality ->
                viewModel.shareDocument(selectedFilePathForShare!!, selectedFileNameForShare ?: context.getString(R.string.document_default_name), quality)
                showQualityDialog = false
                selectedFilePathForShare = null
            }
        )
    }

    if (showFileSelectionSheet && selectedDocForShare != null) {
        val paths = DocumentViewModel.splitPaths(selectedDocForShare?.filePaths)
        ModalBottomSheet(
            onDismissRequest = { showFileSelectionSheet = false }
        ) {
            Column(Modifier.padding(24.dp).padding(bottom = 24.dp).fillMaxWidth()) {
                Text(stringResource(R.string.select_file_to_share), fontWeight = FontWeight.Black, fontSize = 20.sp)
                Spacer(Modifier.height(16.dp))
                paths.forEach { path ->
                    val fileName = path.substringAfterLast("/")
                    ListItem(
                        headlineContent = { Text(fileName) },
                        leadingContent = { Icon(Icons.Default.InsertDriveFile, null) },
                        modifier = Modifier.clickable {
                            selectedFilePathForShare = path
                            selectedFileNameForShare = fileName
                            showFileSelectionSheet = false
                            showQualityDialog = true
                        }
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedDocs.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedDocs.clear() }) {
                            Icon(Icons.Default.Close, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val allFavorite = selectedDocs.all { it.isFavorite }
                            viewModel.toggleFavoriteForDocuments(selectedDocs.toList(), !allFavorite)
                            selectedDocs.clear()
                        }) {
                            Icon(Icons.Default.Star, null)
                        }
                        IconButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            } else if (!isSearching) {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.documents), fontWeight = FontWeight.Black) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, null)
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_documents_placeholder), fontSize = 16.sp) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null, Modifier.size(20.dp))
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate("add_document") },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.add_document)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        if (documents.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Description, null, Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.no_documents), color = Color.Gray)
                }
            }
        } else if (filteredDocuments.isEmpty() && searchQuery.isNotEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_matching_documents), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredDocuments, key = { it.id }) { doc ->
                    val isSelected = selectedDocs.contains(doc)
                    DocumentItem(
                        doc = doc, 
                        viewModel = viewModel,
                        isSelected = isSelected,
                        selectionMode = isSelectionMode,
                        onClick = { 
                            if (isSelectionMode) {
                                if (isSelected) selectedDocs.remove(doc) else selectedDocs.add(doc)
                            } else {
                                navController.navigate("add_document?id=${doc.id}") 
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                selectedDocs.add(doc)
                            }
                        },
                        onShare = {
                            selectedDocForShare = doc
                            val paths = DocumentViewModel.splitPaths(doc.filePaths)
                            if (paths.size > 1) {
                                showFileSelectionSheet = true
                            } else if (paths.isNotEmpty()) {
                                selectedFilePathForShare = paths[0]
                                selectedFileNameForShare = doc.title
                                showQualityDialog = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocumentItem(
    doc: DocumentEntry, 
    viewModel: DocumentViewModel, 
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit
) {
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val isUnlocked by viewModel.isUnlocked.collectAsState()

    LaunchedEffect(doc.thumbnailPath, isUnlocked) {
        if (doc.thumbnailPath != null && isUnlocked) {
            thumbnail = viewModel.getDecryptedThumbnail(doc.thumbnailPath!!)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(thumbnail!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(
                        when(doc.documentType) {
                            stringResource(R.string.sub_id_card) -> Icons.Default.Badge
                            stringResource(R.string.sub_passport) -> Icons.Default.Public
                            stringResource(R.string.sub_health_record), stringResource(R.string.sub_prescription) -> Icons.Default.MedicalServices
                            else -> Icons.Default.Description
                        },
                        null, tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (isSelected) {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(doc.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(doc.documentType, fontSize = 12.sp, color = Color.Gray)
                
                val fileCount = DocumentViewModel.splitPaths(doc.filePaths).size
                if (fileCount > 1) {
                    Text(stringResource(R.string.file_count, fileCount), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
            
            if (!selectionMode) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
