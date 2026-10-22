package org.costalonga.fattidelgiorno.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BluNotte = Color(0xFF1B2A49)
private val Rosso = Color(0xFFC8102E)
private val Crema = Color(0xFFFBF8F3)
private val Sabbia = Color(0xFFEFE8DC)

private val chiaro = lightColorScheme(
    primary = BluNotte,
    onPrimary = Color.White,
    secondary = Rosso,
    onSecondary = Color.White,
    background = Crema,
    surface = Color.White,
    surfaceVariant = Sabbia,
    onSurfaceVariant = Color(0xFF4A4740),
    tertiary = Color(0xFF2E7D32),
)

private val scuro = darkColorScheme(
    primary = Color(0xFFB7C4E6),
    onPrimary = BluNotte,
    secondary = Color(0xFFFF8A80),
    background = Color(0xFF14161C),
    surface = Color(0xFF1E2129),
    surfaceVariant = Color(0xFF2B2F3A),
    tertiary = Color(0xFF81C784),
)

private val tipografia = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

@Composable
fun TemaFattiDelGiorno(contenuto: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) scuro else chiaro,
        typography = tipografia,
        content = contenuto,
    )
}
