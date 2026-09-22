package org.costalonga.meteofvg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.costalonga.meteofvg.data.COMUNI
import org.costalonga.meteofvg.data.COMUNI_PER_PROVINCIA
import org.costalonga.meteofvg.data.Comune
import java.text.Normalizer

/** Confronto che non si fa fermare da accenti e maiuscole. */
private fun pulito(testo: String): String =
    Normalizer.normalize(testo, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase()

/**
 * L'elenco dei 215 Comuni: raggruppati per provincia, oppure filtrati mentre
 * si scrive. Nessuna ricerca nel nulla: chi non trova un Comune non lo ha in
 * regione.
 */
@Composable
fun SchermataComuni(
    scelto: Comune,
    onScelto: (Comune) -> Unit,
    onChiudi: () -> Unit,
) {
    var cerca by remember { mutableStateOf("") }
    val ricerca = pulito(cerca.trim())
    val trovati = remember(ricerca) {
        if (ricerca.isEmpty()) emptyList() else COMUNI.filter { pulito(it.nome).contains(ricerca) }
    }

    Surface(color = Fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = Blu) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "SCEGLI IL COMUNE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Giallo,
                            )
                            Text(
                                text = "215 Comuni del Friuli Venezia Giulia",
                                style = MaterialTheme.typography.titleMedium,
                                color = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                        TextButton(onClick = onChiudi) {
                            Text("Chiudi", color = Giallo, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cerca,
                        onValueChange = { cerca = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        placeholder = { Text("Cerca un Comune") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (ricerca.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    COMUNI_PER_PROVINCIA.forEach { (provincia, comuni) ->
                        item(key = "provincia-$provincia") {
                            IntestazioneProvincia(provincia, comuni.size)
                        }
                        items(comuni, key = { it.istat }) { comune ->
                            RigaComune(comune, comune.nome == scelto.nome, onScelto)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            } else if (trovati.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "Nessun Comune del Friuli Venezia Giulia corrisponde a «$cerca».",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Testo2,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(trovati, key = { it.istat }) { comune ->
                        RigaComune(comune, comune.nome == scelto.nome, onScelto, conProvincia = true)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun IntestazioneProvincia(provincia: String, quanti: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Superficie2)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "PROVINCIA DI ${provincia.uppercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = Blu,
        )
        Text(
            text = "$quanti",
            style = MaterialTheme.typography.labelSmall,
            color = Testo3,
        )
    }
}

@Composable
private fun RigaComune(
    comune: Comune,
    attivo: Boolean,
    onScelto: (Comune) -> Unit,
    conProvincia: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onScelto(comune) }
                .background(if (attivo) BluTenue else Superficie)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 22.dp)
                    .background(
                        if (attivo) Giallo else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = comune.nome,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (attivo) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (attivo) Blu else Testo,
                    fontSize = 16.sp,
                )
                if (conProvincia) {
                    Text(
                        text = "Provincia di ${comune.provincia}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Testo3,
                    )
                }
            }
        }
        HorizontalDivider(color = Bordo)
    }
}
