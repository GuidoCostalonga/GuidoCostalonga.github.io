package org.costalonga.meteofvg.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.costalonga.meteofvg.R

// I colori del sistema visivo di costalonga.org, gli stessi del file colori.xml.
val Blu = Color(0xFF0B3C63)
val BluFondo = Color(0xFF07293F)
val BluChiaro = Color(0xFF1A6098)
val BluTenue = Color(0xFFE7EEF4)
val Giallo = Color(0xFFE6C168)
val GialloScuro = Color(0xFFC39F47)
val Fondo = Color(0xFFF7F6F2)
val Superficie = Color(0xFFFFFFFF)
val Superficie2 = Color(0xFFF1EFE9)
val Testo = Color(0xFF1F1C17)
val Testo2 = Color(0xFF57534A)
val Testo3 = Color(0xFF6E6960)
val Bordo = Color(0xFFE4E0D7)
val Ok = Color(0xFF1B6B4A)
val Attenzione = Color(0xFF8A5A12)
val Pericolo = Color(0xFF9C2B22)

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private val schema = lightColorScheme(
    primary = Blu,
    onPrimary = Color.White,
    primaryContainer = BluTenue,
    onPrimaryContainer = Blu,
    secondary = GialloScuro,
    onSecondary = Color.White,
    background = Fondo,
    onBackground = Testo,
    surface = Superficie,
    onSurface = Testo,
    surfaceVariant = Superficie2,
    onSurfaceVariant = Testo2,
    outline = Bordo,
    error = Pericolo,
)

private fun tipografia(): Typography {
    val base = Typography()
    fun TextStyle.m(peso: FontWeight) = copy(fontFamily = Manrope, fontWeight = peso)
    return Typography(
        displayLarge = base.displayLarge.m(FontWeight.ExtraBold),
        displayMedium = base.displayMedium.m(FontWeight.ExtraBold),
        displaySmall = base.displaySmall.m(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.m(FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.m(FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.m(FontWeight.Bold),
        titleLarge = base.titleLarge.m(FontWeight.ExtraBold),
        titleMedium = base.titleMedium.m(FontWeight.Bold),
        titleSmall = base.titleSmall.m(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.m(FontWeight.Normal),
        bodyMedium = base.bodyMedium.m(FontWeight.Normal),
        bodySmall = base.bodySmall.m(FontWeight.Normal),
        labelLarge = base.labelLarge.m(FontWeight.SemiBold),
        labelMedium = base.labelMedium.m(FontWeight.SemiBold),
        labelSmall = base.labelSmall.m(FontWeight.Medium).copy(letterSpacing = 0.8.sp),
    )
}

@Composable
fun TemaMeteoFVG(contenuto: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = schema,
        typography = tipografia(),
        content = contenuto,
    )
}
