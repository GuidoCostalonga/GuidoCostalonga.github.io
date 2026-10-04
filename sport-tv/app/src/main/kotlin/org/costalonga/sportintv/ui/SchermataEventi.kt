package org.costalonga.sportintv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.costalonga.sportintv.dati.FiltroAccesso
import org.costalonga.sportintv.dati.Filtri
import org.costalonga.sportintv.dati.Preferiti
import org.costalonga.sportintv.dati.giorno
import org.costalonga.sportintv.raccolta.StatoFonte
import java.time.Duration
import java.time.Instant

private enum class Pannello { SPORT, COMPETIZIONE, CANALE, PIATTAFORMA, ACCESSO }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchermataEventi(modello: Modello, vista: Vista, apriEvento: (String) -> Unit) {
    val risultati by modello.risultati(vista).collectAsState()
    val istantanea by modello.istantanea.collectAsState()
    val filtri by modello.filtri(vista).collectAsState()
    val opzioni by modello.opzioni(vista).collectAsState()
    val adesso by modello.adesso.collectAsState()
    val stato by modello.statoAggiornamento.collectAsState()
    val inAggiornamento by modello.inAggiornamento.collectAsState()
    val seguiti by modello.seguiti.collectAsState()
    val salvati by modello.salvati.collectAsState()
    val promemoria by modello.promemoria.collectAsState()
    var pannello by remember { mutableStateOf<Pannello?>(null) }
    val idSalvati = remember(salvati) { salvati.map { it.eventoId }.toSet() }
    val imposta: (Filtri) -> Unit = { modello.impostaFiltri(vista, it) }

    Column(Modifier.fillMaxSize()) {
        // Intestazione ad altezza libera: con i caratteri ingranditi non taglia il testo.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (vista == Vista.CONFERMATI) "Sport in TV" else "Da confermare",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                val ricevuto = stato.ricevuto?.let { Instant.ofEpochMilli(it) }
                Text(
                    if (ricevuto != null) "Aggiornato ${Formato.momento(ricevuto, adesso)}" else "Mai aggiornato",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { modello.aggiorna() }, enabled = !inAggiornamento) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna adesso")
            }
        }

        PullToRefreshBox(isRefreshing = inAggiornamento, onRefresh = { modello.aggiorna() }, modifier = Modifier.fillMaxSize()) {
            val lista = rememberLazyListState()
            LazyColumn(state = lista, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "ricerca") {
                    CampoRicerca(filtri.testo) { imposta(filtri.copy(testo = it)) }
                }
                item(key = "giorni") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = filtri.giorno == null, onClick = { imposta(filtri.copy(giorno = null)) }, label = { Text("Tutti i giorni") })
                        }
                        val oggi = Formato.oggi(adesso)
                        val giorni = (0L..14L).map { oggi.plusDays(it) }
                        items(giorni, key = { it.toString() }) { g ->
                            FilterChip(
                                selected = filtri.giorno == g,
                                onClick = { imposta(filtri.copy(giorno = if (filtri.giorno == g) null else g)) },
                                label = { Text(Formato.etichettaGiorno(g, adesso)) },
                            )
                        }
                    }
                }
                item(key = "filtri") {
                    RigaFiltri(vista, filtri, imposta) { pannello = it }
                }
                item(key = "stato") {
                    AvvisiStato(modello, adesso)
                }

                val elenco = risultati
                when {
                    elenco == null -> Unit
                    istantanea?.eventi.isNullOrEmpty() -> item(key = "vuoto") {
                        val errore = stato.ultimoErrore
                        StatoVuoto(
                            titolo = "Dati non disponibili",
                            testo = if (inAggiornamento) "Sto scaricando i palinsesti…"
                            else "Non è ancora stato possibile scaricare i palinsesti. " + (errore ?: "Controlla la connessione e riprova."),
                            nessunDato = true,
                            azione = if (inAggiornamento) null else "Riprova" to { modello.aggiorna() },
                        )
                    }
                    elenco.isEmpty() -> item(key = "vuoto") {
                        val conFiltri = filtri.copy(giorno = null).attivi > 0
                        val oltreSettimana = filtri.giorno?.let { it.isAfter(Formato.oggi(adesso).plusDays(7)) } ?: false
                        StatoVuoto(
                            titolo = when {
                                conFiltri -> "Nessun evento con questi filtri"
                                vista == Vista.DA_CONFERMARE -> "Nessun evento da confermare"
                                else -> "Nessun evento in programma"
                            },
                            testo = when {
                                conFiltri -> "Prova a togliere qualche filtro o a cercare un altro nome."
                                oltreSettimana -> "Per questa data le fonti non hanno ancora pubblicato eventi. Rai e Mediaset pubblicano il palinsesto circa una settimana prima, DAZN con anticipo variabile."
                                vista == Vista.DA_CONFERMARE -> "Tutti gli eventi noti per questa data hanno una trasmissione confermata in Italia."
                                else -> "Le fonti consultate non riportano eventi sportivi per questa data."
                            },
                            nessunDato = false,
                            azione = if (conFiltri) "Togli i filtri" to { imposta(Filtri(giorno = filtri.giorno)) } else null,
                        )
                    }
                    else -> {
                        if (vista == Vista.DA_CONFERMARE) item(key = "spiega") {
                            Riquadro(
                                titolo = "Che cosa sono questi eventi",
                                testo = "Eventi presenti nei calendari ufficiali delle competizioni ma in nessun palinsesto italiano consultato. " +
                                    "Non è detto che siano trasmessi in Italia: controlla la pagina ufficiale prima di contarci.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        val perGiorno = elenco.groupBy { it.giorno() }
                        for ((g, eventi) in perGiorno) {
                            stickyHeader(key = "g-$g") { IntestazioneGiorno(Formato.intestazioneGiorno(g, adesso)) }
                            items(eventi, key = { it.id }) { e ->
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                    SchedaEvento(
                                        e = e,
                                        adesso = adesso,
                                        preferito = Preferiti.corrisponde(e, seguiti),
                                        salvato = e.id in idSalvati,
                                        conPromemoria = e.id in promemoria,
                                        onClick = { apriEvento(e.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pannello?.let { p ->
        PannelloFiltro(p, filtri, opzioni, imposta) { pannello = null }
    }
}

@Composable
private fun CampoRicerca(testo: String, cambia: (String) -> Unit) {
    OutlinedTextField(
        value = testo,
        onValueChange = cambia,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Squadra, atleta, gara", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        label = { Text("Cerca") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (testo.isNotEmpty()) IconButton(onClick = { cambia("") }) { Icon(Icons.Filled.Clear, contentDescription = "Cancella la ricerca") }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun RigaFiltri(vista: Vista, f: Filtri, imposta: (Filtri) -> Unit, apri: (Pannello) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ChipMenu("Sport", f.sport.size) { apri(Pannello.SPORT) } }
        item { ChipMenu("Competizione", f.competizioni.size) { apri(Pannello.COMPETIZIONE) } }
        if (vista == Vista.CONFERMATI) {
            item { ChipMenu("Canale", f.canali.size) { apri(Pannello.CANALE) } }
            item { ChipMenu("Piattaforma", f.piattaforme.size) { apri(Pannello.PIATTAFORMA) } }
            item {
                FilterChip(
                    selected = f.accesso != FiltroAccesso.TUTTI,
                    onClick = { apri(Pannello.ACCESSO) },
                    label = { Text(if (f.accesso == FiltroAccesso.TUTTI) "Gratis o a pagamento" else f.accesso.etichetta) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                )
            }
            item {
                FilterChip(selected = f.soloDirette, onClick = { imposta(f.copy(soloDirette = !f.soloDirette)) }, label = { Text("Solo dirette") })
            }
        }
        item {
            FilterChip(selected = f.soloPreferiti, onClick = { imposta(f.copy(soloPreferiti = !f.soloPreferiti)) }, label = { Text("Preferiti") })
        }
        item {
            FilterChip(selected = f.mostraConclusi, onClick = { imposta(f.copy(mostraConclusi = !f.mostraConclusi)) }, label = { Text("Anche già conclusi") })
        }
        if (f.copy(giorno = null, testo = "").attivi > 0) {
            item { TextButton(onClick = { imposta(Filtri(giorno = f.giorno, testo = f.testo)) }) { Text("Azzera filtri") } }
        }
    }
}

@Composable
private fun ChipMenu(etichetta: String, scelti: Int, onClick: () -> Unit) {
    FilterChip(
        selected = scelti > 0,
        onClick = onClick,
        label = { Text(if (scelti > 0) "$etichetta ($scelti)" else etichetta) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PannelloFiltro(p: Pannello, f: Filtri, o: OpzioniFiltri, imposta: (Filtri) -> Unit, chiudi: () -> Unit) {
    ModalBottomSheet(onDismissRequest = chiudi) {
        val (titolo, voci, scelti, cambia) = when (p) {
            Pannello.SPORT -> Quattro("Sport", o.sport.map { it to nomeSport(it) }, f.sport) { s: Set<String> -> imposta(f.copy(sport = s)) }
            Pannello.COMPETIZIONE -> Quattro("Competizione", o.competizioni.map { it to it }, f.competizioni) { s: Set<String> -> imposta(f.copy(competizioni = s)) }
            Pannello.CANALE -> Quattro("Canale", o.canali.map { it to it }, f.canali) { s: Set<String> -> imposta(f.copy(canali = s)) }
            Pannello.PIATTAFORMA -> Quattro("Piattaforma", o.piattaforme.map { it to it }, f.piattaforme) { s: Set<String> -> imposta(f.copy(piattaforme = s)) }
            Pannello.ACCESSO -> Quattro("Accesso", emptyList(), emptySet()) { _: Set<String> -> }
        }
        Column(Modifier.padding(bottom = 16.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(titolo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (p != Pannello.ACCESSO && scelti.isNotEmpty()) TextButton(onClick = { cambia(emptySet()) }) { Text("Tutti") }
            }
            Spacer(Modifier.height(8.dp))
            if (p == Pannello.ACCESSO) {
                for (a in FiltroAccesso.entries) {
                    Row(
                        Modifier.fillMaxWidth().clickable { imposta(f.copy(accesso = a)); chiudi() }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = f.accesso == a, onClick = null)
                        Spacer(Modifier.padding(4.dp))
                        Text(a.etichetta, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "Gratis: canali in chiaro e servizi gratuiti con registrazione. A pagamento: abbonamento o acquisto del singolo evento.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            } else if (voci.isEmpty()) {
                Text("Nessuna voce disponibile nei dati attuali.", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(voci, key = { it.first }) { (chiave, nome) ->
                        val selezionato = chiave in scelti
                        Row(
                            Modifier.fillMaxWidth()
                                .toggleable(selezionato, role = Role.Checkbox) { cambia(if (it) scelti + chiave else scelti - chiave) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selezionato, onCheckedChange = null)
                            Spacer(Modifier.padding(4.dp))
                            if (p == Pannello.SPORT) {
                                Icon(iconaSport(chiave), contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                            }
                            Text(nome, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

private data class Quattro(val a: String, val b: List<Pair<String, String>>, val c: Set<String>, val d: (Set<String>) -> Unit)

/** Avvisi su dati vecchi, fonti in difficoltà, aggiornamento fallito. */
@Composable
private fun AvvisiStato(modello: Modello, adesso: Instant) {
    val stato by modello.statoAggiornamento.collectAsState()
    val istantanea by modello.istantanea.collectAsState()
    val generato = stato.generato?.let { Instant.ofEpochMilli(it) } ?: return
    val vecchi = Duration.between(generato, adesso) > Duration.ofHours(12)
    val ultimoTentativoFallito = stato.ultimoErrore != null && (stato.ultimoTentativo ?: 0) > (stato.ricevuto ?: 0)
    val inDifficolta = istantanea?.fonti.orEmpty().filter { it.stato == StatoFonte.ERRORE || it.stato == StatoFonte.DATI_PRECEDENTI || it.stato == StatoFonte.PARZIALE }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (vecchi || ultimoTentativoFallito) {
            Riquadro(
                titolo = if (ultimoTentativoFallito) "Ultimo aggiornamento non riuscito" else "Dati non recenti",
                testo = "I dati mostrati sono stati raccolti ${Formato.fa(generato, adesso)} (${Formato.momento(generato, adesso)}) e potrebbero essere superati." +
                    (stato.ultimoErrore?.takeIf { ultimoTentativoFallito }?.let { " Motivo: $it." } ?: ""),
                icona = Icons.Filled.Warning,
                avviso = true,
                azione = "Riprova" to { modello.aggiorna() },
            )
        }
        if (inDifficolta.isNotEmpty()) {
            Riquadro(
                titolo = "Alcune fonti non hanno risposto del tutto",
                testo = inDifficolta.joinToString("\n") { f ->
                    "• ${f.nome}: " + when (f.stato) {
                        StatoFonte.DATI_PRECEDENTI -> "restano i dati del ${f.ultimoSuccesso?.let { Formato.momento(it, adesso) } ?: "precedente aggiornamento"}, forse superati"
                        StatoFonte.PARZIALE -> "dati parziali, possono mancare giorni o canali"
                        else -> "nessun dato disponibile"
                    }
                },
                icona = Icons.Filled.Warning,
                avviso = true,
            )
        }
    }
}
