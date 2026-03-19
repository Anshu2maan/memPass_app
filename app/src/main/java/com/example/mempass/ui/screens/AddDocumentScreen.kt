package com.example.mempass.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.mempass.CryptoUtils
import com.example.mempass.DocumentEntry
import com.example.mempass.DocumentViewModel
import com.example.mempass.R
import com.example.mempass.ui.components.QualityOption
import com.example.mempass.ui.components.QualityPickerDialog
import com.example.mempass.ui.theme.BrandRose
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class DocCategory(
    val nameRes: Int,
    val icon: ImageVector,
    val subCategories: List<Int>
)

val DOCUMENT_CATEGORIES = listOf(
    DocCategory(R.string.cat_identity, Icons.Default.Badge, listOf(R.string.sub_passport, R.string.sub_id_card, R.string.sub_drivers_license, R.string.sub_voter_id, R.string.sub_pan_card, R.string.sub_other)),
    DocCategory(R.string.cat_financial, Icons.Default.AccountBalance, listOf(R.string.sub_bank_card, R.string.sub_contract, R.string.sub_tax_record, R.string.sub_insurance, R.string.sub_property_deed, R.string.sub_other)),
    DocCategory(R.string.cat_professional, Icons.Default.Work, listOf(R.string.sub_resume, R.string.sub_certificate, R.string.sub_employment_letter, R.string.sub_pay_slip, R.string.sub_other)),
    DocCategory(R.string.cat_medical, Icons.Default.MedicalServices, listOf(R.string.sub_health_record, R.string.sub_prescription, R.string.sub_test_report, R.string.sub_vaccination_cert, R.string.sub_insurance_card, R.string.sub_other)),
    DocCategory(R.string.cat_vehicle, Icons.Default.DirectionsCar, listOf(R.string.sub_rc, R.string.sub_insurance, R.string.sub_puc, R.string.sub_service_record, R.string.sub_other)),
    DocCategory(R.string.cat_educational, Icons.Default.School, listOf(R.string.sub_marksheet, R.string.sub_admit_card, R.string.sub_transfer_cert, R.string.sub_character_cert, R.string.sub_other)),
    DocCategory(R.string.cat_miscellaneous, Icons.Default.Category, listOf(R.string.sub_warranty, R.string.sub_receipt, R.string.sub_membership_card, R.string.sub_emergency_info, R.string.sub_other))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentScreen(navController: NavHostController, viewModel: DocumentViewModel, documentId: Int = 0) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documents by viewModel.allDocuments.collectAsState(initial = emptyList())
    val existingDoc = remember(documentId, documents) { if (documentId != 0) documents.find { it.id == documentId } else null }

    var title by remember(existingDoc) { mutableStateOf(existingDoc?.title ?: "") }
    var type by remember(existingDoc) { mutableStateOf(existingDoc?.documentType ?: context.getString(R.string.sub_other)) }
    var notes by remember(existingDoc) { 
        val chars = if(existingDoc != null) viewModel.decryptToChars(existingDoc.encryptedNotes) else CharArray(0)
        val s = mutableStateOf(String(chars))
        CryptoUtils.wipe(chars)
        s
    }
    
    var filePaths by remember(existingDoc) { 
        mutableStateOf(DocumentViewModel.splitPaths(existingDoc?.filePaths))
    }
    var expiryDate by remember(existingDoc) { mutableStateOf(existingDoc?.expiryDate) }
    var isSaving by remember { mutableStateOf(false) }
    var isAttaching by remember { mutableStateOf(false) }

    // Strings for Toasts and other non-composable contexts
    val filesFailedToEncryptMsg = stringResource(R.string.files_failed_to_encrypt)
    val documentDefaultName = stringResource(R.string.document_default_name)

    // States for individual file sharing
    var showIndividualQualityDialog by remember { mutableStateOf(false) }
    var sharingFilePath by remember { mutableStateOf<String?>(null) }
    var sharingFileName by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedMainCat by remember { mutableStateOf<DocCategory?>(null) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = expiryDate ?: System.currentTimeMillis()
    )

    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            isAttaching = true
            scope.launch {
                var successCount = 0
                uris.forEach { uri ->
                    val path = viewModel.saveUriToInternalEncrypted(uri)
                    if (path != null) {
                        filePaths = filePaths + path
                        successCount++
                    }
                }
                isAttaching = false
                if (successCount < uris.size) {
                    Toast.makeText(context, filesFailedToEncryptMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    expiryDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCategorySheet) {
        ModalBottomSheet(
            onDismissRequest = { 
                showCategorySheet = false
                selectedMainCat = null
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 40.dp).fillMaxWidth()) {
                Text(
                    text = if (selectedMainCat == null) stringResource(R.string.select_category) else stringResource(R.string.select_sub_category),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(20.dp))

                if (selectedMainCat == null) {
                    DOCUMENT_CATEGORIES.forEach { cat ->
                        Surface(
                            onClick = { selectedMainCat = cat },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(cat.icon, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Text(stringResource(cat.nameRes), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { selectedMainCat = null }) {
                            Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.back_to_categories))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    selectedMainCat!!.subCategories.forEach { subRes ->
                        val subName = stringResource(subRes)
                        Surface(
                            onClick = { 
                                type = subName
                                showCategorySheet = false
                                selectedMainCat = null
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (type == subName) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
                            border = if (type == subName) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(subName, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                if (type == subName) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Individual Quality Dialog for attachments
    if (showIndividualQualityDialog && sharingFilePath != null) {
        val originalSize = remember(sharingFilePath) { File(sharingFilePath!!).length() / 1024 }
        QualityPickerDialog(
            originalSizeKb = originalSize,
            onDismiss = { showIndividualQualityDialog = false },
            onQualitySelected = { quality ->
                viewModel.shareDocument(sharingFilePath!!, sharingFileName ?: documentDefaultName, quality)
                showIndividualQualityDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (documentId == 0) stringResource(R.string.add_document) else stringResource(R.string.edit_document)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, null)
                    }
                },
                actions = {
                    if (documentId != 0 && existingDoc != null) {
                        IconButton(onClick = {
                            viewModel.deleteDocument(existingDoc)
                            navController.popBackStack()
                        }) { Icon(Icons.Default.Delete, null, tint = BrandRose) }
                    }
                    Button(
                        onClick = {
                            isSaving = true
                            val fieldsChars = "{}".toCharArray()
                            val notesChars = notes.toCharArray()
                            viewModel.saveDocument(title, type, fieldsChars, notesChars, filePaths, expiryDate, documentId) {
                                isSaving = false
                                CryptoUtils.wipe(fieldsChars, notesChars)
                                navController.popBackStack()
                            }
                        },
                        enabled = title.isNotEmpty() && !isSaving && !isAttaching,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.saving), fontSize = 12.sp)
                        } else {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text(stringResource(R.string.doc_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(24.dp))
            
            Text(stringResource(R.string.category), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Surface(
                onClick = { showCategorySheet = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Category, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(text = type, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.UnfoldMore, null, tint = Color.Gray)
                }
            }
            
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.expiry_date_label), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
            Surface(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (expiryDate != null) {
                            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(expiryDate!!))
                        } else {
                            stringResource(R.string.set_expiry_date_optional)
                        },
                        color = if (expiryDate != null) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                    Spacer(Modifier.weight(1f))
                    if (expiryDate != null) {
                        IconButton(onClick = { expiryDate = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.attachments), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (isAttaching) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.attaching), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            TextButton(onClick = { pickerLauncher.launch("*/*") }) {
                                Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.attach_files))
                            }
                        }
                    }
                    
                    if (filePaths.isEmpty() && !isAttaching) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_documents_desc), color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(filePaths) { path ->
                                val key = viewModel.getVaultKey()
                                FileListItem(
                                    path = path, 
                                    onDelete = { filePaths = filePaths - path }, 
                                    onShare = {
                                        sharingFilePath = path
                                        sharingFileName = path.substringAfterLast("/")
                                        showIndividualQualityDialog = true
                                    }, 
                                    onView = {
                                        if (key != null) viewModel.sharingUtils.viewFile(path, key, path.substringAfterLast("/"))
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun FileListItem(path: String, onDelete: () -> Unit, onShare: () -> Unit, onView: () -> Unit) {
    val fileName = path.substringAfterLast("/")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp).clickable { onView() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.InsertDriveFile, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(fileName, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
        IconButton(onClick = onShare, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, tint = BrandRose, modifier = Modifier.size(16.dp))
        }
    }
}
