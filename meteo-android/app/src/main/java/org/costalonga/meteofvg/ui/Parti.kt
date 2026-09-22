package org.costalonga.meteofvg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * La scheda bianca con la barra gialla a sinistra: il mattone con cui e'
 * costruita la pagina del sito, ripreso qui tale e quale.
 */
@Composable
fun Scheda(
    titolo: String,
    modifier: Modifier = Modifier,
    sottotitolo: String? = null,
    contenuto: @Composable () -> Unit,
) {
    // La barra gialla e' il fondo della scheda che resta scoperto a sinistra:
    // cosi' e' alta quanto la scheda senza dover misurare niente.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Giallo)
            .border(1.dp, Bordo, RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.padding(start = 6.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Superficie,
                        RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = titolo.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Blu,
                )
                if (sottotitolo != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = sottotitolo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Testo3,
                    )
                }
                Spacer(Modifier.height(12.dp))
                contenuto()
            }
        }
    }
}

/** Dato secondario in evidenza: valore grande, etichetta piccola sotto. */
@Composable
fun Dato(etichetta: String, valore: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Superficie2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = valore,
            style = MaterialTheme.typography.titleMedium,
            color = Testo,
        )
        Text(
            text = etichetta,
            style = MaterialTheme.typography.bodySmall,
            color = Testo3,
        )
    }
}

/** Barra di riempimento: la usa il punteggio di attendibilità. */
@Composable
fun BarraValore(quota: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(BluTenue),
    ) {
        Box(
            Modifier
                .fillMaxWidth(quota.coerceIn(0f, 1f))
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Blu),
        )
    }
}
