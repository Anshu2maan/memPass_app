package com.example.mempass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mempass.ui.theme.BrandIndigo

@Composable
fun ServiceIcon(name: String) {
    val firstChar = name.firstOrNull()?.uppercase() ?: "?"
    val bgColor = when(name.lowercase()) {
        "google" -> Color(0xFFEA4335)
        "facebook" -> Color(0xFF1877F2)
        "instagram" -> Color(0xFFE4405F)
        "twitter", "x" -> Color(0xFF000000)
        "github" -> Color(0xFF24292E)
        "linkedin" -> Color(0xFF0A66C2)
        "microsoft" -> Color(0xFF00A4EF)
        "amazon" -> Color(0xFFFF9900)
        else -> BrandIndigo.copy(alpha = 0.1f)
    }
    val textColor = if (bgColor == BrandIndigo.copy(alpha = 0.1f)) BrandIndigo else Color.White

    Box(Modifier.size(44.dp).background(bgColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(firstChar, fontWeight = FontWeight.Black, color = textColor, fontSize = 20.sp)
    }
}
