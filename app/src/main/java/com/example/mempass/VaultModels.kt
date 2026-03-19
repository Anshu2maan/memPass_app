package com.example.mempass

enum class TipType { WARNING, INFO, SUCCESS }

data class SecurityTip(
    val message: String,
    val type: TipType,
    val route: String? = null
)

data class HealthFactor(
    val name: String,
    val score: Float, // 0.0 to 1.0
    val status: String,
    val suggestion: String
)

data class VaultHealth(
    val overallScore: Float,
    val factors: List<HealthFactor>
)
