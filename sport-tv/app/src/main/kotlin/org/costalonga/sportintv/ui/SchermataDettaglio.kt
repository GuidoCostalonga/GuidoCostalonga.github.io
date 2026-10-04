package org.costalonga.sportintv.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.costalonga.sportintv.dati.Preferiti
import org.costalonga.sportintv.dati.Seguito
import org.costalonga.sportintv.dati.TipoSeguito
import org.costalonga.sportintv.dati.concluso
import org.costalonga.sportintv.dati.inCorso
import org.costalonga.sportintv.dati.inOnda
import org.costalonga.sportintv.promemoria.Pianificatore
import org.costalonga.sportintv.raccolta.Evento
import org.costalonga.sportintv.raccolta.Fonte
import org.costalonga.sportintv.raccolta.StatoEvento
import org.costalonga.sportintv.raccolta.Trasmissione
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchermataDettaglio(modello: Modello, id: String, indietro: () -> Unit) {
    val evento by remember(id) { modello.evento(id) }.collectAsState(initial = null)
    val istantanea by modello.istantanea.collectAsState()
    val adesso by modello.adesso.collectAsState()
    val seguiti by modello.seguiti.collectAsState()
    val salvati by modello.salvati.collectAsState()
    val promemoria by modello.promemoria.collectAsState()
    val pref by modello.preferenze.collectAsState()
    val context = LocalContext.current

    val richiestaPermesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concesso ->
        if (!concesso) modello.messaggio.value = "Senza il permesso per le notifiche il promemoria non può avvisarti. Puoi concederlo dalle impostazioni di Android."
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Dettagli") },
            navigationIcon = { IconButton(onClick = indietro) { Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro") } },
        )
        val e = evento
        if (e == null) {
            StatoVuoto("Evento non più presente", "L'evento non compare negli ultimi dati scaricati: potrebbe essere stato tolto dal palinsesto o essere già passato.", nessunDato = false, azione = "Torna all'elenco" to indietro)
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Intestazione
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CerchioSport(e.sport)
                Text(
                    listOfNotNull(nomeSport(e.sport), e.competizione).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(e.titolo, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val (sf, co) = when (e.stato) {
                    StatoEvento.CONFERMATO -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                    StatoEvento.DA_CONFERMARE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                }
                Etichetta(e.stato.etichetta(), sf, co)
                if (e.inCorso(adesso) || e.inOnda(adesso)) EtichettaInCorso(e.inCorso(adesso))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Formato.dataOra(e.inizio).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
                if (e.inizioCalendario != null) {
                    Text("Orario d'inizio secondo il calendario ufficiale della competizione.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e.fine?.let {
                    Text(
                        "Fine della trasmissione: ${Formato.ora(it)}" + if (e.fineStimata) " (stima)" else " (dato della fonte)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } ?: Text("Ora di fine non indicata dalle fonti.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Orari nel fuso italiano.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Azioni personali
            val salvato = salvati.any { it.eventoId == e.id }
            val conPromemoria = e.id in promemoria
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { modello.salvaEvento(e, !salvato) }) {
                    Icon(if (salvato) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (salvato) "Salvato" else "Salva evento")
                }
                if (!e.concluso(adesso) && e.inizio.isAfter(adesso) && e.stato != StatoEvento.ANNULLATO) {
                    FilledTonalButton(onClick = {
                        if (!conPromemoria && Build.VERSION.SDK_INT >= 33 && !Pianificatore.notificheConsentite(context)) {
                            richiestaPermesso.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        modello.promemoria(e, !conPromemoria)
                    }) {
                        Icon(if (conPromemoria) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (conPromemoria) "Promemoria attivo (${pref.anticipoMinuti} min prima)" else "Promemoria ${pref.anticipoMinuti} min prima")
                    }
                }
            }

            // Segui squadre, atleti, competizione
            BloccoSegui(e, seguiti, modello)

            if (e.incertezze.isNotEmpty()) {
                Riquadro(
                    titolo = "Informazioni discordanti",
                    testo = e.incertezze.joinToString("\n") + "\nVerifica sulla pagina ufficiale prima dell'inizio.",
                    icona = Icons.Filled.Warning,
                    avviso = true,
                )
            }
            e.nota?.let { Riquadro(titolo = "Nota", testo = it) }

            // Dove vederlo
            Text("Dove vederlo in Italia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (e.trasmissioni.isEmpty()) {
                Riquadro(
                    titolo = "Trasmissione in Italia da confermare",
                    testo = "L'evento è nel calendario della competizione, ma nessun palinsesto italiano consultato lo riporta ancora. " +
                        "Non è detto che sia trasmesso.",
                    avviso = true,
                )
                e.linkUfficiale?.let { url ->
                    OutlinedButton(onClick = { apriLink(context, url) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Pagina ufficiale della competizione")
                    }
                }
            } else {
                for (t in e.trasmissioni) SchedaTrasmissione(t, istantanea?.fonti.orEmpty(), adesso) { apriLink(context, t.link) }
            }

            // Fonti
            HorizontalDivider()
            Text("Fonti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val fonti = istantanea?.fonti.orEmpty().filter { it.id in e.fonti }
            for (f in fonti) RigaFonte(f, adesso) { apriLink(context, f.url) }
            Text(
                "L'app è una guida: non trasmette né incorpora i contenuti. Il pulsante «Guarda» apre la pagina ufficiale, nell'app del servizio se installata.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BloccoSegui(e: Evento, seguiti: List<Seguito>, modello: Modello) {
    val tipo = Preferiti.tipoPartecipante(e)
    val voci = e.partecipanti.map { tipo to it } + listOfNotNull(e.competizione?.let { TipoSeguito.COMPETIZIONE to it })
    if (voci.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Segui", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((t, nome) in voci) {
                val gia = seguiti.firstOrNull { it.tipo == t && it.nome.equals(nome, ignoreCase = true) }
                OutlinedButton(onClick = { if (gia != null) modello.smetti(gia) else modello.segui(t, nome) }) {
                    Icon(if (gia != null) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (gia != null) "$nome (${t.etichetta.lowercase()}, seguito)" else "$nome (${t.etichetta.lowercase()})")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchedaTrasmissione(t: Trasmissione, fonti: List<Fonte>, adesso: Instant, guarda: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.canale, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(t.piattaforma, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EtichettaAccesso(t.accesso)
                Etichetta(t.tipo.etichetta(), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Dalle ${Formato.ora(t.inizio)}" + (t.fine?.let { " alle ${Formato.ora(it)}" } ?: "") + " · " + Formato.dataOra(t.inizio).substringBefore(" alle"),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (t.titoloOriginale.isNotBlank()) {
                Text("Nel palinsesto: «${t.titoloOriginale}»", style = MaterialTheme.typography.bodySmall)
            }
            t.nota?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val fonte = fonti.firstOrNull { it.id == t.fonte }
            Text(
                "Fonte: ${fonte?.nome ?: t.fonte} · verificato ${Formato.momento(t.verificato, adesso)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = guarda) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Guarda su ${t.canale}")
            }
        }
    }
}

@Composable
fun RigaFonte(f: Fonte, adesso: Instant, apri: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(f.nome, style = MaterialTheme.typography.titleSmall)
        Text(
            "Ultima verifica ${Formato.momento(f.ultimaVerifica, adesso)}" +
                (f.ultimoSuccesso?.takeIf { it != f.ultimaVerifica }?.let { " · ultimo dato valido ${Formato.momento(it, adesso)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
        f.messaggio?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        TextButton(onClick = apri) { Text("Apri la fonte") }
    }
}
