package org.costalonga.fattidelgiorno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Avviso
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.costalonga.fattidelgiorno.dominio.TipoAvviso
import org.costalonga.fattidelgiorno.generazione.Lunghezza
import org.costalonga.fattidelgiorno.generazione.Piattaforma
import org.costalonga.fattidelgiorno.generazione.Tono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataPrincipale(vista: VistaModello, apriImpostazioni: () -> Unit) {
    val stato by vista.stato.collectAsStateWithLifecycle()
    val elenco = rememberLazyListState()
    var calendarioAperto by remember { mutableStateOf(false) }

    // Quando si sceglie un fatto, la pagina scorre fino alla sezione dei contenuti.
    LaunchedEffect(stato.fattoScelto?.id) {
        if (stato.fattoScelto != null) {
            val indice = 4 + stato.fattiFiltrati.size
            elenco.animateScrollToItem(indice)
        }
    }

    if (calendarioAperto) {
        val statoCalendario = rememberDatePickerState(initialSelectedDateMillis = Date.aMillisecondiUtc(stato.data))
        DatePickerDialog(
            onDismissRequest = { calendarioAperto = false },
            confirmButton = {
                TextButton(onClick = {
                    statoCalendario.selectedDateMillis?.let { vista.scegliData(Date.daMillisecondiUtc(it)) }
                    calendarioAperto = false
                }) { Text("Conferma") }
            },
            dismissButton = { TextButton(onClick = { calendarioAperto = false }) { Text("Annulla") } },
        ) {
            DatePicker(state = statoCalendario, title = { Text("Scegli una data", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fatti del Giorno", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = { vista.cerca(forza = true) }, enabled = !stato.caricamento) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aggiorna dalle fonti")
                    }
                    IconButton(onClick = apriImpostazioni) { Icon(Icons.Default.Settings, contentDescription = "Impostazioni") }
                },
            )
        },
    ) { margini ->
        LazyColumn(
            state = elenco,
            modifier = Modifier.fillMaxSize().padding(margini),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "data") {
                RigaData(stato, apriCalendario = { calendarioAperto = true }, oggi = vista::oggi)
            }
            item(key = "filtri") {
                Filtri(stato, vista)
            }
            item(key = "stato") {
                StatoRicerca(stato, riprova = { vista.cerca(forza = true) })
            }
            item(key = "intestazione-fatti") {
                val totale = stato.risultato?.fatti?.size ?: 0
                val mostrati = stato.fattiFiltrati.size
                if (totale > 0) {
                    Text(
                        if (mostrati == totale) "$totale proposte per il ${Date.giornoMese(stato.data.dayOfMonth, stato.data.monthValue)}"
                        else "$mostrati proposte mostrate su $totale (filtri attivi)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(stato.fattiFiltrati, key = { it.id }) { fatto ->
                SchedaFatto(fatto, scelto = stato.fattoScelto?.id == fatto.id, scegli = { vista.scegliFatto(fatto) })
            }
            item(key = "contenuti") {
                SezioneContenuti(stato, vista)
            }
            item(key = "piede") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun RigaData(stato: StatoSchermata, apriCalendario: () -> Unit, oggi: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Text("Data selezionata (ora di Roma)", style = MaterialTheme.typography.labelMedium)
            Text(Date.estesa(stato.data).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = apriCalendario) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cambia data")
                }
                if (stato.data != Date.oggi()) OutlinedButton(onClick = oggi) { Text("Torna a oggi") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Filtri(stato: StatoSchermata, vista: VistaModello) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Area geografica", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Area.entries.forEach { area ->
                FilterChip(selected = area in stato.areeAttive, onClick = { vista.alternaArea(area) }, label = { Text(area.etichetta) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Categorie", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            if (stato.categorieAttive.size < Categoria.entries.size) {
                TextButton(onClick = vista::tutteLeCategorie) { Text("Tutte") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Categoria.entries.forEach { c ->
                FilterChip(selected = c in stato.categorieAttive, onClick = { vista.alternaCategoria(c) }, label = { Text(c.etichetta) })
            }
        }
    }
}

@Composable
private fun StatoRicerca(stato: StatoSchermata, riprova: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stato.caricamento) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Interrogo le fonti per questa data…")
            }
        }
        stato.erroreGrave?.let { Riquadro(it, TipoAvviso.ERRORE) }
        val r = stato.risultato
        if (r != null && !stato.caricamento) {
            if (r.daMemoria) {
                Riquadro(
                    "Contenuti memorizzati sul telefono il ${Date.momentoLeggibile(r.consultatoIl)}: non sono stati appena aggiornati.",
                    TipoAvviso.OFFLINE,
                )
            } else {
                Text("Fonti consultate il ${Date.momentoLeggibile(r.consultatoIl)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            r.avvisi.forEach { Riquadro("${it.fonte}: ${it.messaggio}", it.tipo) }
            if (r.avvisi.any { it.tipo != TipoAvviso.VUOTO }) {
                TextButton(onClick = riprova) { Text("Riprova") }
            }
        }
    }
}

@Composable
private fun Riquadro(testo: String, tipo: TipoAvviso) {
    val (colore, icona) = when (tipo) {
        TipoAvviso.OFFLINE -> MaterialTheme.colorScheme.surfaceVariant to Icons.Default.Info
        TipoAvviso.LIMITE, TipoAvviso.TIMEOUT -> Color(0xFFFFF3CD) to Icons.Default.Warning
        TipoAvviso.ERRORE -> Color(0xFFF8D7DA) to Icons.Default.Error
        TipoAvviso.VUOTO -> MaterialTheme.colorScheme.surfaceVariant to Icons.Default.Info
    }
    Surface(color = colore, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icona, contentDescription = null, tint = Color(0xFF4A4740), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(testo, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2B2A27))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SchedaFatto(fatto: Fatto, scelto: Boolean, scegli: () -> Unit) {
    val contesto = LocalContext.current
    var noteAperte by remember(fatto.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = if (scelto) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Etichetta(fatto.area.etichetta, MaterialTheme.colorScheme.primary)
                Etichetta(fatto.categoria.etichetta, MaterialTheme.colorScheme.secondary)
            }
            Text(fatto.titolo, style = MaterialTheme.typography.titleLarge)
            Text(fatto.spiegazioneData().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(fatto.testo, style = MaterialTheme.typography.bodyLarge)
            Verifica(fatto.verifica)
            Text("Fonti", style = MaterialTheme.typography.labelMedium)
            fatto.fonti.forEach { fonte ->
                Row(
                    Modifier.fillMaxWidth().clickable { Condivisione.apri(contesto, fonte.url) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "Apri la fonte", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(fonte.nome, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            fonte.url + (fonte.consultataIl?.let { " · consultata il $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text("Origine: ${fatto.origine}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fatto.note.isNotEmpty()) {
                TextButton(onClick = { noteAperte = !noteAperte }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (noteAperte) "Nascondi note" else "Note (${fatto.note.size})")
                }
                if (noteAperte) fatto.note.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            Button(onClick = scegli, modifier = Modifier.fillMaxWidth()) {
                Text(if (scelto) "Scelto per il contenuto" else "Usa per un contenuto social")
            }
        }
    }
}

@Composable
private fun Etichetta(testo: String, colore: Color) {
    Surface(color = colore.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(testo, color = colore, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun Verifica(livello: LivelloVerifica) {
    val (colore, icona) = when (livello) {
        LivelloVerifica.ALTO -> Color(0xFF2E7D32) to Icons.Default.CheckCircle
        LivelloVerifica.MEDIO -> Color(0xFF1565C0) to Icons.Default.Info
        LivelloVerifica.BASSO -> Color(0xFFB26A00) to Icons.Default.Warning
        LivelloVerifica.INCOERENTE -> Color(0xFFC62828) to Icons.Default.Error
    }
    Surface(color = colore.copy(alpha = 0.10f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(icona, contentDescription = null, tint = colore, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(livello.etichetta, color = colore, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(livello.spiegazione, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SezioneContenuti(stato: StatoSchermata, vista: VistaModello) {
    val contesto = LocalContext.current
    val fatto = stato.fattoScelto
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Contenuto social", style = MaterialTheme.typography.headlineSmall)
            if (fatto == null) {
                Text("Scegli una scheda qui sopra con \"Usa per un contenuto social\": qui compariranno la bozza e il prompt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("Fatto scelto: ${fatto.titolo}", style = MaterialTheme.typography.titleMedium)
            val o = stato.opzioni
            var opzioniAvanzate by remember { mutableStateOf(false) }

            Text("Piattaforma", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Piattaforma.entries.forEach { p ->
                    FilterChip(selected = o.piattaforma == p, onClick = { vista.aggiornaOpzioni { it.copy(piattaforma = p) } }, label = { Text(p.etichetta) })
                }
            }
            Text("Formato", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                o.piattaforma.formati.forEach { f ->
                    FilterChip(selected = o.formato == f, onClick = { vista.aggiornaOpzioni { it.copy(formato = f) } }, label = { Text(f.etichetta) })
                }
            }
            Text(o.formato.descrizione, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tono", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tono.entries.forEach { t ->
                    FilterChip(selected = o.tono == t, onClick = { vista.aggiornaOpzioni { it.copy(tono = t) } }, label = { Text(t.etichetta) })
                }
            }
            TextButton(onClick = { opzioniAvanzate = !opzioniAvanzate }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(if (opzioniAvanzate) "Nascondi le altre opzioni" else "Altre opzioni: pubblico, lunghezza, invito, emoji, hashtag")
            }
            if (opzioniAvanzate) {
                OutlinedTextField(
                    value = o.pubblico,
                    onValueChange = { v -> vista.aggiornaOpzioni { it.copy(pubblico = v) } },
                    label = { Text("Pubblico (facoltativo)") },
                    placeholder = { Text("per esempio: cittadini di Roveredo in Piano") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Lunghezza", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lunghezza.entries.forEach { l ->
                        FilterChip(selected = o.lunghezza == l, onClick = { vista.aggiornaOpzioni { it.copy(lunghezza = l) } }, label = { Text(l.etichetta) })
                    }
                }
                OutlinedTextField(
                    value = o.invitoAzione,
                    onValueChange = { v -> vista.aggiornaOpzioni { it.copy(invitoAzione = v) } },
                    label = { Text("Invito all'azione (facoltativo)") },
                    placeholder = { Text("per esempio: Vieni in biblioteca sabato") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = o.emoji, onCheckedChange = { v -> vista.aggiornaOpzioni { it.copy(emoji = v) } })
                    Spacer(Modifier.width(8.dp)); Text("Emoji")
                    Spacer(Modifier.width(24.dp))
                    Switch(checked = o.hashtag, onCheckedChange = { v -> vista.aggiornaOpzioni { it.copy(hashtag = v) } })
                    Spacer(Modifier.width(8.dp)); Text("Hashtag")
                }
            }
            HorizontalDivider()

            BloccoTesto(
                titolo = "1. Bozza del contenuto (modificabile)",
                valore = stato.bozza,
                cambia = vista::modificaBozza,
                copia = { Condivisione.copia(contesto, "Bozza", stato.bozza) },
                condividi = { Condivisione.condividi(contesto, stato.bozza, "Condividi la bozza") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = vista::rigenera) { Text("Rigenera la bozza") }
                if (stato.geminiConfigurato) {
                    Button(onClick = vista::generaConGemini, enabled = !stato.generazioneAi) {
                        if (stato.generazioneAi) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Rifinisci con Gemini")
                    }
                }
            }
            stato.messaggioAi?.let { Riquadro(it, TipoAvviso.OFFLINE) }
            if (!stato.geminiConfigurato) {
                Text(
                    "La bozza è generata sul telefono, senza servizi esterni. Nelle impostazioni puoi aggiungere una chiave Gemini personale (facoltativa) per farla rifinire da un servizio AI.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            BloccoTesto(
                titolo = "2. Prompt pronto da copiare in un servizio AI",
                valore = stato.prompt,
                cambia = vista::modificaPrompt,
                copia = { Condivisione.copia(contesto, "Prompt", stato.prompt) },
                condividi = { Condivisione.condividi(contesto, stato.prompt, "Condividi il prompt") },
            )
        }
    }
}

@Composable
private fun BloccoTesto(titolo: String, valore: String, cambia: (String) -> Unit, copia: () -> Unit, condividi: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titolo, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = valore,
            onValueChange = cambia,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Text("${valore.length} caratteri", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = copia, label = { Text("Copia") }, leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) })
            AssistChip(onClick = condividi, label = { Text("Condividi") }, leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp)) })
        }
    }
}
