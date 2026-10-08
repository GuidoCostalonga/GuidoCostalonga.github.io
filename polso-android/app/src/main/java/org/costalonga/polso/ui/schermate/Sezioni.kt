package org.costalonga.polso.ui.schermate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.costalonga.polso.motore.analisi.Catalogo
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Sezione
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.SceltaMotore
import org.costalonga.polso.ui.componenti.EtichettaIa
import org.costalonga.polso.ui.componenti.Riquadro
import org.costalonga.polso.ui.componenti.SchedaAnalisi
import org.costalonga.polso.ui.componenti.SelettorePeriodo
import org.costalonga.polso.ui.componenti.Sezione as TitoloSezione

private val INTRO = mapOf(
    Sezione.ATTIVITA to "Passi, distanza e calorie come li registra la fonte, con statistiche, confronti, obiettivi e regolarità.",
    Sezione.CUORE to "Frequenza cardiaca diurna e notturna, zone, risposta agli allenamenti. La HRV compare solo se la fonte la fornisce.",
    Sezione.SONNO to "Durata, orari, fasi e regolarità. Il sonno appartiene al giorno del risveglio.",
    Sezione.ALLENAMENTI to "Sessioni registrate sull'orologio: volume, sport, passo, progressi e carico (solo con dati cardiaci sufficienti).",
    Sezione.ALTRI to "SpO₂, stress, respirazione, temperatura, peso e pressione: solo quando disponibili. I punteggi proprietari non sono grandezze cliniche.",
    Sezione.RELAZIONI to "Associazioni esplorative fra grandezze, con numerosità, incertezza e correzione per confronti multipli. Non indicano cause.",
    Sezione.INDICI to "Indici descrittivi con formula trasparente e componenti consultabili, e qualità dei dati.",
)

@Composable
fun SezioneSchermata(vm: AppViewModel, sezione: Sezione, apri: (String) -> Unit) {
    val p by vm.periodo.collectAsState()
    val esiti by vm.esiti.collectAsState()
    val sezioni = if (sezione == Sezione.INDICI) listOf(Sezione.INDICI, Sezione.QUALITA) else listOf(sezione)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { SelettorePeriodo(p, vm.oggi, vm::impostaTipo, vm::sposta, vm::personalizza) }
        item { Text(INTRO[sezione].orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        sezioni.forEach { s ->
            if (sezioni.size > 1) item { TitoloSezione(s.nome) }
            val lista = Catalogo.perSezione(s).sortedBy { if (esiti[it.def.id] is Esito.Disponibile) 0 else 1 }
            items(lista, key = { it.def.id }) { a ->
                SchedaAnalisi(a, esiti[a.def.id] ?: Esito.NonDisponibile("Calcolo in corso…"), { apri(a.def.id) }, compatta = true)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun DettaglioAnalisi(vm: AppViewModel, id: String) {
    val a = Catalogo.perId(id) ?: return
    val esiti by vm.esiti.collectAsState()
    val p by vm.periodo.collectAsState()
    val spiegazione by vm.spiegazione.collectAsState()
    val lavorando by vm.lavorandoIa.collectAsState()
    var motore by remember { mutableStateOf(SceltaMotore.CALCOLO) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SelettorePeriodo(p, vm.oggi, vm::impostaTipo, vm::sposta, vm::personalizza)
        SchedaAnalisi(a, esiti[id] ?: Esito.NonDisponibile("Calcolo in corso…"))
        if (esiti[id] is Esito.Disponibile) {
            TitoloSezione("Spiegazione")
            Row { vm.motoriDisponibili().forEach { m -> FilterChip(selected = motore == m, onClick = { motore = m }, label = { Text(m.nome) }, modifier = Modifier.padding(end = 6.dp)) } }
            OutlinedButton(onClick = { vm.spiega(id, motore) }, enabled = !lavorando) { Text("Spiega questo grafico") }
            if (lavorando) CircularProgressIndicator(Modifier.padding(8.dp))
            spiegazione?.takeIf { it.fatti.analisiUsate.contains(id) || it.fatti.mancanti.isNotEmpty() }?.let { r ->
                if (r.daIa) EtichettaIa()
                Riquadro(if (r.daIa) "Scritto da ${r.motore}" else "Testo calcolato (senza IA)", r.testo + (r.nota?.let { "\n\n$it" } ?: ""))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CatalogoSchermata(vm: AppViewModel, apri: (String) -> Unit) {
    val esiti by vm.esiti.collectAsState()
    val p by vm.periodo.collectAsState()
    val disp = esiti.values.count { it is Esito.Disponibile }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            SelettorePeriodo(p, vm.oggi, vm::impostaTipo, vm::sposta, vm::personalizza)
            Text("$disp analisi disponibili su ${Catalogo.tutte.size} per questo periodo. Le altre indicano cosa manca.", style = MaterialTheme.typography.bodyMedium)
        }
        Sezione.entries.forEach { s ->
            item { TitoloSezione(s.nome) }
            items(Catalogo.perSezione(s), key = { it.def.id }) { a ->
                val e = esiti[a.def.id]
                Row(Modifier.fillMaxWidth().clickable { apri(a.def.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (e is Esito.Disponibile) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline, if (e is Esito.Disponibile) "Disponibile" else "Non disponibile",
                        Modifier.size(20.dp), tint = if (e is Esito.Disponibile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(a.def.titolo, style = MaterialTheme.typography.bodyLarge)
                        Text(if (e is Esito.NonDisponibile) "Non disponibile: ${e.motivo}" else "Disponibile · richiede: ${a.def.datiRichiesti}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
