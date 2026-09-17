package org.costalonga.meteofvg.ui

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import org.costalonga.meteofvg.data.Famiglia

/** Quanto resta in vista ogni fotogramma: lo stesso passo del widget. */
private const val PASSO_MS = 150L

/**
 * L'icona della condizione, ferma: per gli elenchi delle ore e dei giorni,
 * dove far muovere decine di disegni non servirebbe a nulla.
 */
@Composable
fun IconaMeteo(famiglia: Famiglia, dimensione: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(famiglia.fotogrammi[0]),
        contentDescription = null,
        modifier = modifier.size(dimensione),
    )
}

/**
 * L'icona animata del riquadro "Adesso": gli stessi sei fotogrammi che scorrono
 * nel widget. Se sul telefono le animazioni sono disattivate, resta ferma.
 */
@Composable
fun IconaAnimata(famiglia: Famiglia, dimensione: Dp, modifier: Modifier = Modifier) {
    val contesto = LocalContext.current
    val animazioni = remember {
        runCatching {
            Settings.Global.getFloat(
                contesto.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }

    if (!animazioni) {
        IconaMeteo(famiglia, dimensione, modifier)
        return
    }

    val fotogrammi = famiglia.fotogrammi
    var indice by remember(famiglia) { mutableIntStateOf(0) }
    LaunchedEffect(famiglia) {
        while (true) {
            delay(PASSO_MS)
            indice = (indice + 1) % fotogrammi.size
        }
    }
    Image(
        painter = painterResource(fotogrammi[indice]),
        contentDescription = null,
        modifier = modifier.size(dimensione),
    )
}
