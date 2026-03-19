package com.example.mempass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.mempass.R
import com.example.mempass.*
import com.example.mempass.ui.components.*
import com.example.mempass.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(navController: NavHostController, viewModel: VaultViewModel) {
    val securityTips by viewModel.securityTips.collectAsState(initial = emptyList())
    val healthData by viewModel.vaultHealth.collectAsState(initial = VaultHealth(0f, emptyList()))
    val themePref by viewModel.themePreference.collectAsState()
    
    var showHealthDetail by remember { mutableStateOf(false) }

    val isDark = when(themePref) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.dashboard_subtitle), fontSize = 11.sp, color = BrandEmerald, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        val newPref = if(isDark) 1 else 2
                        viewModel.setThemePreference(newPref)
                    }) {
                        Icon(if(isDark) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { viewModel.lockVault(); navController.navigate("unlock") }) {
                        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))

            Box(Modifier.padding(horizontal = 20.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ModernDashboardCard(Modifier.weight(1f), stringResource(R.string.passwords), Icons.Default.VpnKey, IndigoGradient) { navController.navigate("password_list") }
                        ModernDashboardCard(Modifier.weight(1f), stringResource(R.string.documents), Icons.Default.Description, EmeraldGradient) { navController.navigate("document_list") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ModernDashboardCard(Modifier.weight(1f), stringResource(R.string.notes), Icons.AutoMirrored.Filled.Notes, AmberGradient) { navController.navigate("note_list") }
                        ModernDashboardCard(Modifier.weight(1f), stringResource(R.string.settings), Icons.Default.Settings, Brush.verticalGradient(listOf(Color(0xFF94A3B8), Color(0xFF475569)))) { navController.navigate("settings") }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.vault_insights), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(16.dp))

            VaultHealthCard(healthData) { showHealthDetail = true }

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.security_tips), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (securityTips.isEmpty()) {
                    SecurityTipCard(SecurityTip(stringResource(R.string.vault_secure), TipType.SUCCESS), null)
                } else {
                    securityTips.forEach { tip ->
                        SecurityTipCard(tip) {
                            tip.route?.let { navController.navigate(it) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showHealthDetail) {
        VaultHealthDetailDialog(healthData) { showHealthDetail = false }
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
