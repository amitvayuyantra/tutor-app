package com.tutordiary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF8A5A12),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2B8),
    onSecondaryContainer = Color(0xFF2C1700),
    background = Color(0xFFF8F6F1),
    onBackground = Color(0xFF1C1C19),
    surface = Color(0xFFF8F6F1),
    onSurface = Color(0xFF1C1C19),
    surfaceVariant = Color(0xFFE6E3DA),
    onSurfaceVariant = Color(0xFF47463F),
    surfaceContainerLow = Color(0xFFF2EFE8),
    surfaceContainer = Color(0xFFECE9E1),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF78776E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD4BF),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFCDEBE1),
    secondary = Color(0xFFF2BD72),
    onSecondary = Color(0xFF482A00),
    secondaryContainer = Color(0xFF673E00),
    onSecondaryContainer = Color(0xFFFFE2B8),
    background = Color(0xFF141614),
    onBackground = Color(0xFFE4E3DD),
    surface = Color(0xFF141614),
    onSurface = Color(0xFFE4E3DD),
    surfaceVariant = Color(0xFF45483F),
    onSurfaceVariant = Color(0xFFC7C7BC),
    surfaceContainerLow = Color(0xFF1B1E1B),
    surfaceContainer = Color(0xFF212421),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF919189),
)

/** Colours for fee status, readable in both themes. */
@Immutable
data class StatusColors(val paid: Color, val partial: Color, val due: Color, val overdue: Color)

private val LightStatus = StatusColors(Color(0xFF1E6B3A), Color(0xFF8A5A12), Color(0xFF3D5A80), Color(0xFFB3261E))
private val DarkStatus = StatusColors(Color(0xFF8BD6A0), Color(0xFFF2BD72), Color(0xFFA9C7F0), Color(0xFFF2B8B5))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val base = Typography()
private val AppTypography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

val AmountStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)

@Composable
fun TutorDiaryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
