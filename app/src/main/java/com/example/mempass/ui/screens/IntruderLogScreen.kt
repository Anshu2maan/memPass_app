package com.example.mempass.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.mempass.ImageUtils
import com.example.mempass.R
import com.example.mempass.VaultViewModel
import com.example.mempass.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntruderLogScreen(navController: NavHostController, viewModel: VaultViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(viewModel.cameraHelper.getIntruderLogs()) }
    var selectedPhoto by remember { mutableStateOf<File?>(null) }
    val intruderKey = remember { viewModel.cameraHelper.getIntruderKey() }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Scaffold(
        containerColor = AppSurfaceLight,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.intruder_logs), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.app_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurfaceLight)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!hasCameraPermission) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = BrandRose.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.camera_access_disabled), fontWeight = FontWeight.Bold, color = BrandRose)
                            Text(stringResource(R.string.camera_access_disabled_desc), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRose)
                        ) {
                            Text(stringResource(R.string.enable), color = Color.White)
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Security, null, Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.no_intruder_attempts), color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(logs) { file ->
                        val time = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedPhoto = file },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.LightGray), contentAlignment = Alignment.Center) {
                                    val thumbnailState = produceState<Bitmap?>(null, file) {
                                        value = ImageUtils.decodeSampledBitmapFromEncryptedFile(file, intruderKey, 128, 128)
                                    }
                                    if (thumbnailState.value != null) {
                                        Image(thumbnailState.value!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.failed_attempt), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BrandRose)
                                    Text(time, fontSize = 12.sp, color = Color.Gray)
                                }
                                IconButton(onClick = {
                                    viewModel.cameraHelper.deleteIntruderLog(file)
                                    logs = viewModel.cameraHelper.getIntruderLogs()
                                }) {
                                    Icon(Icons.Default.Delete, null, tint = BrandRose.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedPhoto != null) {
        AlertDialog(
            onDismissRequest = { selectedPhoto = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Box(Modifier.fillMaxWidth().height(500.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                    val bitmapState = produceState<Bitmap?>(null, selectedPhoto) {
                        value = ImageUtils.decodeSampledBitmapFromEncryptedFile(selectedPhoto!!, intruderKey, 1080, 1920)
                    }
                    if (bitmapState.value != null) {
                        Image(bitmapState.value!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                    IconButton(
                        onClick = { selectedPhoto = null },
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}
