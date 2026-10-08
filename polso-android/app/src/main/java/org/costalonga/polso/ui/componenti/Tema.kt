package org.costalonga.polso.ui.componenti

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colori scelti per un contrasto almeno AA su sfondo chiaro e scuro.
private val Chiaro = lightColorScheme(
    primary = Color(0xFF0E5C63), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDEE), onPrimaryContainer = Color(0xFF00363B),
    secondary = Color(0xFF8A4B12), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2), onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF5B4B9A), tertiaryContainer = Color(0xFFE6DEFF),
    background = Color(0xFFF6FAF9), onBackground = Color(0xFF171D1D),
    surface = Color(0xFFF6FAF9), onSurface = Color(0xFF171D1D),
    surfaceVariant = Color(0xFFDAE5E4), onSurfaceVariant = Color(0xFF3F4948),
    surfaceContainer = Color(0xFFEAF0EF), surfaceContainerHigh = Color(0xFFE4EAE9),
    error = Color(0xFFB3261E), outline = Color(0xFF6F7979),
)

private val Scuro = darkColorScheme(
    primary = Color(0xFF7FD4DB), onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F55), onPrimaryContainer = Color(0xFFCDEDEE),
    secondary = Color(0xFFFFB77C), onSecondary = Color(0xFF4B2800),
    secondaryContainer = Color(0xFF6B3A06), onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFCABEFF), tertiaryContainer = Color(0xFF433481),
    background = Color(0xFF101615), onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF101615), onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceContainer = Color(0xFF1B2221), surfaceContainerHigh = Color(0xFF252C2B),
    error = Color(0xFFF2B8B5), outline = Color(0xFF899392),
)

@Composable
fun TemaPolso(tema: String, contenuto: @Composable () -> Unit) {
    val scuro = when (tema) { "chiaro" -> false; "scuro" -> true; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (scuro) Scuro else Chiaro, content = contenuto)
}
