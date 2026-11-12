package org.costalonga.meteofvg.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.costalonga.meteofvg.allerte.Guardia
import org.costalonga.meteofvg.allerte.Notifiche
import org.costalonga.meteofvg.data.Comune
import org.costalonga.meteofvg.data.Preferenze

/**
 * L'interruttore degli avvisi: spento finche' non lo si accende.
 *
 * Acceso, il telefono guarda il riquadro della Regione circa ogni ora e
 * squilla solo quando compare un'allerta nuova per il Comune scelto.
 */
@Composable
fun RiquadroAvvisi(comune: Comune, modifier: Modifier = Modifier) {
    val contesto = LocalContext.current

    var attivi by remember { mutableStateOf(Preferenze.avvisiAttivi(contesto)) }
    var negato by remember { mutableStateOf(false) }

    fun accendi() {
        Preferenze.salvaAvvisiAttivi(contesto, true)
        Notifiche.preparaCanale(contesto)
        Guardia.attiva(contesto)
        attivi = true
        negato = false
    }

    fun spegni() {
        Preferenze.salvaAvvisiAttivi(contesto, false)
        Guardia.disattiva(contesto)
        attivi = false
    }

    // Da Android 13 le notifiche vanno chieste. Se chi usa l'app dice di no,
    // l'interruttore resta spento: accenderlo senza permesso sarebbe una
    // promessa che il telefono non puo' mantenere.
    val richiesta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concesso ->
        if (concesso) accendi() else negato = true
    }

    Scheda(
        titolo = "Avvisi sul telefono",
        sottotitolo = "Allerte della Protezione Civile regionale",
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Avvisami per ${comune.nome}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Testo,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (attivi) "Controllo attivo, circa ogni ora" else "Controllo spento",
                    style = MaterialTheme.typography.bodySmall,
                    color = Testo3,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = attivi,
                onCheckedChange = { acceso ->
                    if (!acceso) {
                        spegni()
                    } else if (Notifiche.permesso(contesto)) {
                        accendi()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        richiesta.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        negato = true
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Superficie,
                    checkedTrackColor = Blu,
                ),
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Il telefono legge il riquadro ufficiale della Protezione Civile " +
                "della Regione Friuli Venezia Giulia e avvisa soltanto quando compare " +
                "un'allerta nuova, o quando quella in corso cambia. Se il riquadro non " +
                "è raggiungibile l'app tace: il silenzio non significa che non ci siano " +
                "allerte.",
            style = MaterialTheme.typography.bodyMedium,
            color = Testo2,
        )

        if (negato) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Android non sta concedendo le notifiche a questa app. " +
                    "Si attivano da Impostazioni, Applicazioni, Meteo FVG, Notifiche.",
                style = MaterialTheme.typography.bodyMedium,
                color = Pericolo,
            )
        }
    }
}
