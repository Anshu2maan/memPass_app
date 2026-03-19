package com.example.mempass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsSection(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        PremiumCard {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        supportingContent = description?.let { { Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) } },
        leadingContent = { Icon(icon, null, tint = iconColor) },
        trailingContent = trailingContent,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    )
}
