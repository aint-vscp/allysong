package com.allysong.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// AllySongTheme.kt – commonMain
// ============================================================================
// Dark-first theme optimized for emergency scenarios.
// High-contrast colors ensure readability in low-light disaster conditions.
// ============================================================================

// ── Emergency color palette ─────────────────────────────────────────────────
val EmergencyRed     = Color(0xFFFF1744)
val AlertOrange      = Color(0xFFFF9100)
val SafeGreen        = Color(0xFF00E676)
val MeshBlue         = Color(0xFF448AFF)
val SurfaceDark      = Color(0xFF121212)
val SurfaceVariant   = Color(0xFF1E1E1E)
val OnSurfaceLight   = Color(0xFFE0E0E0)

// ── Extended palette for UI components ──────────────────────────────────────
val SurfaceCard       = Color(0xFF1A1A2E)
val SurfaceCardBorder = Color(0xFF2A2A4E)
val TextMuted         = Color(0xFF8888AA)
val TextSecondary     = Color(0xFFAAAACC)
val GradientStart     = Color(0xFF0D1B2A)
val GradientEnd       = Color(0xFF1B2838)

private val AllySongColorScheme = darkColorScheme(
    primary = MeshBlue,
    secondary = AlertOrange,
    tertiary = SafeGreen,
    error = EmergencyRed,
    background = SurfaceDark,
    surface = SurfaceVariant,
    surfaceVariant = SurfaceCard,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onError = Color.White,
    outline = SurfaceCardBorder
)

private val AllySongTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AllySongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AllySongColorScheme,
        typography = AllySongTypography,
        content = content
    )
}
