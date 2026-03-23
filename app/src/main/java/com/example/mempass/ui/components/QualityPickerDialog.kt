package com.example.mempass.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mempass.R

sealed class QualityOption(val labelRes: Int, val sizeKb: Long?) {
    object Original : QualityOption(R.string.original_quality, null)
    class Custom(val size: Long) : QualityOption(R.string.compress_to_size, size)
}

@Composable
fun QualityPickerDialog(
    titleRes: Int = R.string.share_options,
    originalSizeKb: Long? = null,
    onDismiss: () -> Unit,
    onQualitySelected: (QualityOption) -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customSize by remember { mutableStateOf("") }
    val context = LocalContext.current

    val qualityLevel = remember(customSize, originalSizeKb) {
        val target = customSize.toLongOrNull() ?: return@remember null
        if (originalSizeKb == null) {
            when {
                target < 20 -> context.getString(R.string.q_extreme) to Color(0xFFF44336)
                target < 50 -> context.getString(R.string.q_low) to Color(0xFFFFA000)
                target < 150 -> context.getString(R.string.q_medium) to Color(0xFF8BC34A)
                else -> context.getString(R.string.q_high) to Color(0xFF4CAF50)
            }
        } else {
            val ratio = target.toFloat() / originalSizeKb
            when {
                ratio < 0.05f -> context.getString(R.string.q_extreme_desc) to Color(0xFFF44336)
                ratio < 0.15f -> context.getString(R.string.q_low_desc) to Color(0xFFFFA000)
                ratio < 0.40f -> context.getString(R.string.q_balanced_desc) to Color(0xFF8BC34A)
                else -> context.getString(R.string.q_high_desc) to Color(0xFF4CAF50)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    stringResource(if (showCustomInput) R.string.custom_compression else titleRes),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
                if (originalSizeKb != null) {
                    Text(
                        stringResource(R.string.original_size_info, originalSizeKb),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                if (!showCustomInput) {
                    QualityOptionItem(
                        title = stringResource(R.string.original_quality),
                        subtitle = stringResource(R.string.original_quality_desc),
                        icon = Icons.Default.CloudDone,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onQualitySelected(QualityOption.Original) }
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    QualityOptionItem(
                        title = stringResource(R.string.compress_to_size),
                        subtitle = stringResource(R.string.compress_to_size_desc),
                        icon = Icons.Default.HighQuality,
                        color = Color(0xFF8BC34A),
                        onClick = { showCustomInput = true }
                    )
                } else {
                    OutlinedTextField(
                        value = customSize,
                        onValueChange = { if (it.all { char -> char.isDigit() }) customSize = it },
                        label = { Text(stringResource(R.string.target_size_label)) },
                        placeholder = { Text(stringResource(R.string.target_size_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        prefix = { Text(stringResource(R.string.max_prefix), color = Color.Gray, fontSize = 14.sp) },
                        suffix = { Text(stringResource(R.string.kb_suffix), fontWeight = FontWeight.Bold) }
                    )

                    AnimatedVisibility(
                        visible = qualityLevel != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        qualityLevel?.let { (text, color) ->
                            Column(Modifier.padding(top = 16.dp, start = 4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.est_quality), fontSize = 12.sp, color = Color.Gray)
                                    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
                                }
                                if (text.contains(stringResource(R.string.q_extreme)) || text.contains("Extreme")) {
                                    Text(
                                        stringResource(R.string.extreme_quality_warning),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showCustomInput) {
                Button(
                    onClick = {
                        val size = customSize.toLongOrNull() ?: 100L
                        onQualitySelected(QualityOption.Custom(size))
                    },
                    enabled = customSize.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.compress_share))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (showCustomInput) showCustomInput = false else onDismiss() }) {
                Text(stringResource(if (showCustomInput) R.string.back else R.string.cancel))
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun QualityOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}
