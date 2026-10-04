package org.costalonga.sportintv.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.costalonga.sportintv.dati.Tema

// Palette propria: blu sportivo, verde campo, ambra. Contrasti verificati
// per testo normale (almeno 4,5:1) sia in chiaro sia in scuro.
private val Chiaro = lightColorScheme(
    primary = Color(0xFF0B4F9C), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF1B6B40), onSecondary = Color.White,
    secondaryContainer = Color(0xFFA6F2BF), onSecondaryContainer = Color(0xFF00210F),
    tertiary = Color(0xFF8A5100), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8F9FF), onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF), onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC), onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3FA),
    surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E8),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
)

private val Scuro = darkColorScheme(
    primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C), onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF8BD6A4), onSecondary = Color(0xFF00391D),
    secondaryContainer = Color(0xFF00522C), onSecondaryContainer = Color(0xFFA6F2BF),
    tertiary = Color(0xFFFFB870), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00), onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318), onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318), onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
)

/** Colori per le modalità di accesso, leggibili su entrambe le modalità. */
@Immutable
data class ColoriAccesso(val gratis: Color, val suGratis: Color, val pagamento: Color, val suPagamento: Color)

val LocalColoriAccesso = staticCompositionLocalOf {
    ColoriAccesso(Color(0xFFA6F2BF), Color(0xFF00210F), Color(0xFFFFDCBE), Color(0xFF2C1600))
}

@Composable
fun TemaSport(tema: Tema, dinamici: Boolean, contenuto: @Composable () -> Unit) {
    val scuro = when (tema) {
        Tema.SISTEMA -> isSystemInDarkTheme()
        Tema.CHIARO -> false
        Tema.SCURO -> true
    }
    val schema: ColorScheme = when {
        dinamici && Build.VERSION.SDK_INT >= 31 ->
            if (scuro) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        scuro -> Scuro
        else -> Chiaro
    }
    val accesso = if (scuro) {
        ColoriAccesso(Color(0xFF00522C), Color(0xFFA6F2BF), Color(0xFF693C00), Color(0xFFFFDCBE))
    } else {
        ColoriAccesso(Color(0xFFA6F2BF), Color(0xFF00210F), Color(0xFFFFDCBE), Color(0xFF2C1600))
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalColoriAccesso provides accesso) {
        MaterialTheme(colorScheme = schema, content = contenuto)
    }
}
