package org.costalonga.polso.ui.schermate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Sport
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.analisi.Catalogo
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.analisi.Evidenze
import org.costalonga.polso.motore.analisi.Grafico
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.Pagina
import org.costalonga.polso.ui.componenti.EtichettaCalcolo
import org.costalonga.polso.ui.componenti.EtichettaFonte
import org.costalonga.polso.ui.componenti.GraficoAnalisi
import org.costalonga.polso.ui.componenti.Riquadro
import org.costalonga.polso.ui.componenti.SchedaAnalisi
import org.costalonga.polso.ui.componenti.SelettorePeriodo
import org.costalonga.polso.ui.componenti.data

val NOMI_SCHEDE = mapOf(
    "oggi" to "Riepilogo di oggi", "passi" to "Passi", "sonno" to "Sonno", "cuore" to "Cuore", "allenamenti" to "Allenamenti",
    "emerge" to "Cosa emerge dai tuoi dati", "calendario" to "Calendario dell'attività", "qualita" to "Qualità dei dati e fonte",
)

@Composable
fun Panoramica(vm: AppViewModel, apri: (String) -> Unit, vai: (Pagina) -> Unit) {
    val imp by vm.impostazioni.collectAsState()
    val p by vm.periodo.collectAsState()
    val esiti by vm.esiti.collectAsState()
    val dati by vm.dati.collectAsState()
    val ev by vm.evidenze.collectAsState()
    val registro by vm.registro.collectAsState()
    var riordina by remember { mutableStateOf(false) }
    val ordine = imp.ordineSchede.filter { it in NOMI_SCHEDE } + NOMI_SCHEDE.keys.filter { it !in imp.ordineSchede }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item { SelettorePeriodo(p, vm.oggi, vm::impostaTipo, vm::sposta, vm::personalizza) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { riordina = !riordina }) { Text(if (riordina) "Fine riordino" else "Riordina schede") }
            }
        }
        items(ordine, key = { it }) { scheda ->
            Column {
                if (riordina) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(NOMI_SCHEDE[scheda]!!, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { vm.modifica { i -> i.copy(ordineSchede = ordine.toMutableList().apply { val k = indexOf(scheda); if (k > 0) { removeAt(k); add(k - 1, scheda) } }) } }) { Icon(Icons.Default.ArrowUpward, "Sposta su") }
                    IconButton(onClick = { vm.modifica { i -> i.copy(ordineSchede = ordine.toMutableList().apply { val k = indexOf(scheda); if (k < size - 1) { removeAt(k); add(k + 1, scheda) } }) } }) { Icon(Icons.Default.ArrowDownward, "Sposta giù") }
                } else when (scheda) {
                    "oggi" -> dati?.let { Oggi(it, vm) }
                    "passi" -> esiti["att.passi"]?.let { SchedaAnalisi(Catalogo.perId("att.passi")!!, it, { apri("att.passi") }, compatta = true) }
                    "sonno" -> esiti["sonno.durata"]?.let { SchedaAnalisi(Catalogo.perId("sonno.durata")!!, it, { apri("sonno.durata") }, compatta = true) }
                    "cuore" -> esiti["fc.giornaliera"]?.let { SchedaAnalisi(Catalogo.perId("fc.giornaliera")!!, it, { apri("fc.giornaliera") }, compatta = true) }
                    "allenamenti" -> esiti["all.riepilogo"]?.let { SchedaAnalisi(Catalogo.perId("all.riepilogo")!!, it, { apri("all.riepilogo") }, compatta = true) }
                    "calendario" -> (esiti["att.obiettivo"] as? Esito.Disponibile)?.grafici?.filterIsInstance<Grafico.Calendario>()?.firstOrNull()?.let {
                        Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(14.dp)) { GraficoAnalisi(it) }
                        }
                    }
                    "emerge" -> Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lightbulb, null); Spacer(Modifier.padding(4.dp)); Text("Cosa emerge dai tuoi dati", style = MaterialTheme.typography.titleSmall) }
                            EtichettaCalcolo()
                            if (ev.isEmpty()) Text("Nulla di rilevante secondo i criteri fissati per questo periodo.", style = MaterialTheme.typography.bodyMedium)
                            ev.forEach { e ->
                                Text(e.titolo, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                                Text(e.testo, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { apri(e.analisiId) }) { Text("Vedi l'analisi") }
                            }
                            Text(Evidenze.CRITERI, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    "qualita" -> {
                        val ultima = registro.firstOrNull { it.esito == "riuscita" }
                        Riquadro("Fonte e aggiornamento",
                            (if (imp.modalitaDemo) "Dati dimostrativi sintetici. " else "") +
                                "Ultima sincronizzazione riuscita: ${data(ultima?.conclusaIl, imp.preferenze().zona)} (${ultima?.descrizione ?: "nessuna"}). " +
                                (registro.firstOrNull()?.takeIf { it.esito != "riuscita" }?.let { "Ultimo tentativo: ${it.esito} — ${it.errori.lineSequence().firstOrNull().orEmpty()}" } ?: ""))
                        esiti["qual.copertura"]?.let { SchedaAnalisi(Catalogo.perId("qual.copertura")!!, it, { apri("qual.copertura") }, compatta = true) }
                        TextButton(onClick = { vai(Pagina.CATALOGO) }) { Text("Elenco delle analisi e dei requisiti mancanti") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Oggi(d: Dati, vm: AppViewModel) {
    val oggi = vm.oggi
    val passi = d.serie(Metrica.PASSI)[oggi]
    val fc = d.serie(Metrica.FREQUENZA_CARDIACA)[oggi]
    val notte = d.notti()[oggi]
    val allenamenti = d.allenamenti.filter { Tempo.giorno(it.inizio, d.zona, it.scartoSec) == oggi }
    val ultimo = d.ultimoGiorno()
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text("Oggi, ${Tempo.etichetta(oggi)}", style = MaterialTheme.typography.titleMedium)
            Text("Giorno in corso: i totali crescono durante la giornata.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { EtichettaFonte(); EtichettaCalcolo() }
            Spacer(Modifier.height(6.dp))
            Riga("Passi", passi?.valore?.let { "${Formato.numero(it)} su ${Formato.numero(d.pref.obiettivoPassi.toDouble())}" } ?: "nessun dato")
            Riga("Distanza", d.serie(Metrica.DISTANZA)[oggi]?.valore?.let { Formato.distanza(it) } ?: "nessun dato")
            Riga("Sonno di stanotte", notte?.let { "${Formato.durata(it.minutiSonno)} (${Formato.orario(Tempo.minutoDelGiorno(it.inizio, d.zona).toDouble())}–${Formato.orario(Tempo.minutoDelGiorno(it.fine, d.zona).toDouble())})" } ?: "nessuna sessione")
            Riga("Frequenza cardiaca", fc?.valore?.let { "media ${Formato.numero(it)} bpm (${Formato.numero(fc.minimo)}–${Formato.numero(fc.massimo)}), ${fc.n} campioni" } ?: "nessun dato")
            d.serie(Metrica.FC_RIPOSO)[oggi]?.valore?.let { Riga("Frequenza a riposo (fonte)", "${Formato.numero(it)} bpm") }
            Riga("Allenamenti", if (allenamenti.isEmpty()) "nessuno" else allenamenti.joinToString { "${Sport.nome(it.sport)} ${Formato.durata(it.durataMs / 60_000.0)}" })
            if (ultimo != null && ultimo < oggi) Text("Ultimo dato disponibile: ${Tempo.etichetta(ultimo)}. Sincronizza per aggiornare.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun Riga(a: String, b: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(a, Modifier.weight(0.45f), style = MaterialTheme.typography.bodyMedium)
        Text(b, Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
