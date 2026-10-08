package org.costalonga.polso.ui.schermate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.costalonga.polso.motore.TipoPeriodo
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.SceltaMotore
import org.costalonga.polso.ui.componenti.EtichettaCalcolo
import org.costalonga.polso.ui.componenti.EtichettaIa
import org.costalonga.polso.ui.componenti.data

private val ESEMPI = listOf(
    "Come ho dormito questa settimana?", "Quanti passi ho fatto il mese scorso?", "Qual è il mio record di passi?",
    "Com'è andata la frequenza cardiaca negli ultimi 30 giorni?", "Gli allenamenti stanno migliorando?", "C'è una relazione fra attività e sonno?",
)

@Composable
fun AssistenteSchermata(vm: AppViewModel) {
    var scheda by rememberSaveable { mutableIntStateOf(0) }
    var motore by remember { mutableStateOf(SceltaMotore.CALCOLO) }
    val lavorando by vm.lavorandoIa.collectAsState()
    val motori = vm.motoriDisponibili()
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = scheda) {
            Tab(scheda == 0, { scheda = 0 }, text = { Text("Riepiloghi") })
            Tab(scheda == 1, { scheda = 1 }, text = { Text("Domande") })
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Chi scrive la risposta:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                motori.forEach { m -> FilterChip(selected = motore == m, onClick = { motore = m }, label = { Text(m.nome) }) }
            }
            if (motori.size == 1) Text("Nessun modello di IA attivo: le risposte sono calcolate dall'app. Puoi scaricare un modello locale gratuito in Impostazioni.", style = MaterialTheme.typography.bodySmall)
            Text("I numeri vengono sempre dal calcolo dell'app. L'IA li riformula soltanto: se inventa un valore o usa linguaggio medico, la risposta viene scartata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lavorando) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        if (scheda == 0) Riepiloghi(vm, motore, lavorando) else Chat(vm, motore, lavorando)
    }
}

@Composable
private fun Riepiloghi(vm: AppViewModel, motore: SceltaMotore, lavorando: Boolean) {
    val lista by vm.riepiloghi.collectAsState()
    val zona = vm.impostazioni.collectAsState().value.preferenze().zona
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                OutlinedButton(onClick = { vm.generaRiepilogo(TipoPeriodo.GIORNO, motore) }, enabled = !lavorando) { Text("Riepilogo di ieri") }
                OutlinedButton(onClick = { vm.generaRiepilogo(TipoPeriodo.SETTIMANA, motore) }, enabled = !lavorando) { Text("Settimana scorsa") }
            }
            Text("I riepiloghi restano consultabili qui; nessuna notifica viene inviata per i riepiloghi.", style = MaterialTheme.typography.bodySmall)
        }
        items(lista, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Riepilogo ${r.tipo} · dal ${r.da} al ${r.a}", style = MaterialTheme.typography.titleSmall)
                    if (r.verificato) EtichettaIa() else EtichettaCalcolo()
                    Text("Scritto da: ${if (r.motore == "calcolo") "calcolo dell'app (senza IA)" else r.motore} · ${data(r.creatoIl, zona)}", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(r.testo, style = MaterialTheme.typography.bodyMedium)
                    var fatti by remember { mutableStateOf(false) }
                    TextButton(onClick = { fatti = !fatti }) { Text(if (fatti) "Nascondi i dati usati" else "Su quali dati si basa") }
                    if (fatti) Text(r.fatti.ifBlank { "Non disponibile per i riepiloghi ripristinati da backup." }, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { vm.eliminaRiepilogo(r.id) }) { Text("Elimina") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Chat(vm: AppViewModel, motore: SceltaMotore, lavorando: Boolean) {
    val chat by vm.chat.collectAsState()
    var testo by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (chat.isEmpty()) item {
                Text("Esempi di domande:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ESEMPI.forEach { e -> AssistChip(onClick = { vm.chiedi(e, motore) }, label = { Text(e) }) }
            }
            items(chat) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Text(m.domanda, Modifier.padding(10.dp))
                }
                m.risposta?.let { r ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(10.dp)) {
                            if (r.daIa) EtichettaIa() else EtichettaCalcolo()
                            Text(r.testo, style = MaterialTheme.typography.bodyMedium)
                            r.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                            Text("Periodo: ${r.fatti.periodo.descrizione()} · analisi usate: ${r.fatti.analisiUsate.size} · motore: ${if (r.daIa) r.motore else "calcolo"}", style = MaterialTheme.typography.labelSmall)
                            if (r.fatti.mancanti.isNotEmpty()) Text("Limiti: ${r.fatti.mancanti.take(3).joinToString("; ")}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp)) {
            OutlinedTextField(testo, { testo = it }, Modifier.weight(1f), placeholder = { Text("Chiedi sui tuoi dati…") }, maxLines = 3)
            IconButton(onClick = { if (testo.isNotBlank()) { vm.chiedi(testo.trim(), motore); testo = "" } }, enabled = !lavorando) { Icon(Icons.AutoMirrored.Filled.Send, "Invia") }
        }
    }
}
