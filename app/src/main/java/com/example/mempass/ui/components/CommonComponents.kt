package com.example.mempass.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mempass.R
import com.example.mempass.ui.theme.*
import com.example.mempass.utils.ClipboardUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = BrandIndigo) },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun IconButtonWithLabel(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (active) BrandIndigo else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(if (active) BrandIndigo.copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ModernSearchBar(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = BrandIndigo) },
        trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, null) } },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

fun copyToClipboardWithTimer(
    context: Context,
    clipboard: ClipboardManager,
    text: String,
    label: String,
    scope: CoroutineScope
) {
    ClipboardUtils.copyToClipboard(context, label, text)
    Toast.makeText(context, context.getString(R.string.copy_label_copied, label), Toast.LENGTH_SHORT).show()
}

@Composable
fun InfoCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BrandIndigo.copy(alpha = 0.1f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, null, tint = BrandIndigo, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
    }
}

@Composable
fun ModernEmptyState(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(80.dp), tint = BrandIndigo.copy(alpha = 0.2f))
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DetailDialog(
    title: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onFavoriteToggle: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    
                    if (onFavoriteToggle != null) {
                        IconButton(onClick = onFavoriteToggle) {
                            Icon(
                                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (onEdit != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = BrandIndigo) }
                    }
                    if (onDelete != null) {
                        var showConfirm by remember { mutableStateOf(false) }
                        IconButton(onClick = { showConfirm = true }) { Icon(Icons.Default.Delete, null, tint = BrandRose) }
                        if (showConfirm) {
                            AlertDialog(
                                onDismissRequest = { showConfirm = false },
                                title = { Text(stringResource(R.string.delete_entry_title)) },
                                text = { Text(stringResource(R.string.delete_entry_desc)) },
                                confirmButton = {
                                    TextButton(onClick = { onDelete(); showConfirm = false }, colors = ButtonDefaults.textButtonColors(contentColor = BrandRose)) {
                                        Text(stringResource(R.string.delete_pass), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
                                }
                            )
                        }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String, icon: ImageVector, onCopy: (() -> Unit)? = null) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = BrandIndigo)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.weight(1f))
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp), tint = BrandIndigo)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onUsePassword: (String) -> Unit
) {
    var len by remember { mutableFloatStateOf(16f) }
    var genPass by remember { mutableStateOf(com.example.mempass.PasswordGenerator.generateRandomPassword(16)) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.random_password_title), fontWeight = FontWeight.Black) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(String(genPass), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.length_format, len.toInt()))
                Slider(value = len, onValueChange = { len = it; genPass = com.example.mempass.PasswordGenerator.generateRandomPassword(it.toInt()) }, valueRange = 8f..32f)
            }
        },
        confirmButton = { Button(onClick = { onUsePassword(String(genPass)) }) { Text(stringResource(R.string.use_password)) } },
        dismissButton = { TextButton(onClick = { genPass = com.example.mempass.PasswordGenerator.generateRandomPassword(len.toInt()) }) { Text(stringResource(R.string.regenerate)) } }
    )
}
