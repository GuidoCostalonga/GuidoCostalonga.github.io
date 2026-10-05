package org.costalonga.polso.ui.componenti

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.TipoPeriodo
import org.costalonga.polso.motore.analisi.Analisi
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Provenienza
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Etichetta con icona e testo: la distinzione non è affidata al solo colore. */
@Composable
fun Etichetta(testo: String, icona: ImageVector, colore: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(color = colore, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icona, null, Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text(testo, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable fun EtichettaFonte() = Etichetta("Misura della fonte", Icons.Default.Sensors, MaterialTheme.colorScheme.primaryContainer)
@Composable fun EtichettaCalcolo() = Etichetta("Valore calcolato", Icons.Default.Calculate, MaterialTheme.colorScheme.secondaryContainer)
@Composable fun EtichettaIa() = Etichetta("Interpretazione dell'IA", Icons.Default.AutoAwesome, MaterialTheme.colorScheme.tertiaryContainer)

@Composable
fun Sezione(titolo: String, modifier: Modifier = Modifier) {
    Text(titolo, modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedaAnalisi(a: Analisi, e: Esito, onApri: (() -> Unit)? = null, compatta: Boolean = false) {
    var dettagli by rememberSaveable(a.def.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).then(if (onApri != null) Modifier.clickable { onApri() } else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(a.def.titolo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            when (e) {
                is Esito.NonDisponibile -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, Modifier.size(18.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("Non disponibile: ${e.motivo}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.mancano.isNotEmpty()) Text("Serve: ${e.mancano.joinToString()}", style = MaterialTheme.typography.bodySmall)
                }
                is Esito.Disponibile -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        if (e.voci.any { it.provenienza == Provenienza.FONTE }) EtichettaFonte()
                        EtichettaCalcolo()
                        Etichetta("Dati in ${e.copertura.giorniConDati} su ${e.copertura.giorniNelPeriodo}", Icons.Default.DateRange, MaterialTheme.colorScheme.surfaceVariant)
                        if (e.copertura.sospetti > 0) Etichetta("${e.copertura.sospetti} valori sospetti esclusi", Icons.Default.WarningAmber, MaterialTheme.colorScheme.surfaceVariant)
                    }
                    val voci = if (compatta) e.voci.take(4) else e.voci
                    voci.forEach { v ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(v.etichetta, Modifier.weight(0.45f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v.testo + if (v.provenienza == Provenienza.FONTE && e.voci.any { it.provenienza != Provenienza.FONTE }) " ·fonte" else "", Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (compatta && e.voci.size > 4) Text("… altre ${e.voci.size - 4} voci nel dettaglio", style = MaterialTheme.typography.bodySmall)
                    (if (compatta) e.grafici.take(1) else e.grafici).forEach { Spacer(Modifier.height(8.dp)); GraficoAnalisi(it) }
                    e.copertura.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    if (!compatta) e.note.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (!compatta) {
                TextButton(onClick = { dettagli = !dettagli }) { Text(if (dettagli) "Nascondi come è calcolato" else "Come è calcolato") }
                if (dettagli) SchedaTecnica(a)
            }
        }
    }
}

@Composable
fun SchedaTecnica(a: Analisi) {
    Column {
        HorizontalDivider()
        listOf(
            "Dati richiesti" to a.def.datiRichiesti, "Metodo" to a.def.metodo, "Unità" to a.def.unita, "Minimo di osservazioni" to a.def.minimo,
            "Dati mancanti" to a.def.mancanti, "Limiti" to a.def.limiti, "Prova automatica" to a.def.prova,
        ).forEach { (t, v) ->
            Text(t, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            Text(v, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelettorePeriodo(p: Periodo, oggi: LocalDate, onTipo: (TipoPeriodo) -> Unit, onSposta: (Int) -> Unit, onPersonalizza: (LocalDate, LocalDate) -> Unit) {
    var scegli by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TipoPeriodo.entries.forEach { t ->
                FilterChip(selected = p.tipo == t, onClick = { if (t == TipoPeriodo.PERSONALIZZATO) scegli = true else onTipo(t) }, label = { Text(t.nome) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSposta(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Periodo precedente") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(p.descrizione().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                if (p.parziale(oggi)) Text("Periodo in corso: dati parziali", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = { onSposta(1) }, enabled = p.a < oggi) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Periodo successivo") }
        }
    }
    if (scegli) {
        val stato = rememberDateRangePickerState(
            initialSelectedStartDateMillis = p.da.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            initialSelectedEndDateMillis = p.a.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { scegli = false },
            confirmButton = {
                TextButton(onClick = {
                    val da = stato.selectedStartDateMillis; val a = stato.selectedEndDateMillis ?: stato.selectedStartDateMillis
                    if (da != null && a != null) onPersonalizza(Instant.ofEpochMilli(da).atOffset(ZoneOffset.UTC).toLocalDate(), Instant.ofEpochMilli(a).atOffset(ZoneOffset.UTC).toLocalDate())
                    scegli = false
                }) { Text("Conferma") }
            },
            dismissButton = { TextButton(onClick = { scegli = false }) { Text("Annulla") } },
        ) { DateRangePicker(stato, Modifier.height(480.dp), title = { Text("Scegli l'intervallo", Modifier.padding(16.dp)) }) }
    }
}

@Composable
fun Riquadro(titolo: String, testo: String, icona: ImageVector = Icons.Default.Info, colore: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(color = colore, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.padding(12.dp)) {
            Icon(icona, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(titolo, style = MaterialTheme.typography.titleSmall)
                Text(testo, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun data(t: Long?, zona: java.time.ZoneId): String = t?.let {
    val z = Instant.ofEpochMilli(it).atZone(zona)
    "%02d/%02d/%d %02d:%02d".format(z.dayOfMonth, z.monthValue, z.year, z.hour, z.minute)
} ?: "—"

