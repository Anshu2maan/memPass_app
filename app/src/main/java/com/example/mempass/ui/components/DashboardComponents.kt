package com.example.mempass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mempass.*
import com.example.mempass.R
import com.example.mempass.ui.theme.*

@Composable
fun SecurityTipCard(tip: SecurityTip, onClick: (() -> Unit)?) {
    val (icon, color) = when(tip.type) {
        TipType.WARNING -> Icons.Default.Warning to BrandRose
        TipType.INFO -> Icons.Default.Info to BrandIndigo
        TipType.SUCCESS -> Icons.Default.CheckCircle to BrandEmerald
    }

    Card(
        modifier = Modifier.width(260.dp).height(110.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f))
    ) {
        Row(Modifier.padding(16.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tip.message, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, color = color)
                if (tip.route != null) {
                    Text(stringResource(R.string.quick_access) + " →", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun ModernDashboardCard(modifier: Modifier, title: String, icon: ImageVector, gradient: Brush, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(160.dp).shadow(12.dp, RoundedCornerShape(32.dp), ambientColor = BrandIndigo.copy(alpha = 0.3f), spotColor = BrandIndigo.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(100.dp).align(Alignment.TopEnd).offset(x = 30.dp, y = (-30).dp).background(gradient, CircleShape).alpha(0.1f))
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.size(52.dp).background(gradient, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun VaultHealthCard(healthData: VaultHealth, onClick: () -> Unit) {
    val healthScore = healthData.overallScore
    val healthColor = when {
        healthScore < 0.3f -> BrandRose
        healthScore < 0.6f -> BrandAmber
        healthScore < 0.8f -> Color(0xFF84CC16)
        else -> BrandEmerald
    }

    Card(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(healthColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.HealthAndSafety, null, tint = healthColor)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    val status = when {
                        healthScore < 0.3f -> stringResource(R.string.health_critical)
                        healthScore < 0.6f -> stringResource(R.string.health_fair)
                        healthScore < 0.8f -> stringResource(R.string.health_good)
                        else -> stringResource(R.string.health_excellent)
                    }
                    Text("${stringResource(R.string.vault_health)}: $status", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.health_score_desc, (healthScore * 100).toInt()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { healthScore },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = healthColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun VaultHealthDetailDialog(healthData: VaultHealth, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.security_breakdown), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                healthData.factors.forEach { factor ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val fColor = if(factor.score > 0.8f) BrandEmerald else if(factor.score > 0.5f) BrandAmber else BrandRose
                            Box(Modifier.size(8.dp).background(fColor, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(factor.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Text(factor.status, fontSize = 12.sp, color = fColor, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(factor.suggestion, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { factor.score },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = if(factor.score > 0.8f) BrandEmerald else if(factor.score > 0.5f) BrandAmber else BrandRose,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.got_it), color = MaterialTheme.colorScheme.primary) }
        }
    )
}
