package org.costalonga.sportintv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.costalonga.sportintv.dati.Preferiti
import org.costalonga.sportintv.dati.TipoSeguito
import org.costalonga.sportintv.dati.concluso

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchermataPreferiti(modello: Modello, apriEvento: (String) -> Unit) {
    val seguiti by modello.seguiti.collectAsState()
    val salvati by modello.salvati.collectAsState()
    val istantanea by modello.istantanea.collectAsState()
    val adesso by modello.adesso.collectAsState()
    val promemoria by modello.promemoria.collectAsState()
    var nome by rememberSaveable { mutableStateOf("") }
    var tipo by rememberSaveable { mutableStateOf(TipoSeguito.SQUADRA) }

    val eventi = istantanea?.eventi.orEmpty()
    val idSalvati = salvati.map { it.eventoId }.toSet()
    val prossimi = eventi.filter { !it.concluso(adesso) && (it.id in idSalvati || Preferiti.corrisponde(it, seguiti)) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Preferiti", fontWeight = FontWeight.Bold) })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Squadre, atleti e competizioni che segui", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in TipoSeguito.entries) FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.etichetta) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = nome,
                            onValueChange = { nome = it },
                            label = { Text("Nome da seguire") },
                            placeholder = { Text(if (tipo == TipoSeguito.COMPETIZIONE) "es. Serie A" else if (tipo == TipoSeguito.ATLETA) "es. Sinner" else "es. Juventus") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { modello.segui(tipo, nome); nome = "" }),
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { modello.segui(tipo, nome); nome = "" }, enabled = nome.isNotBlank()) { Text("Segui") }
                    }
                    if (seguiti.isEmpty()) {
                        Text(
                            "Non segui ancora nulla. Puoi aggiungere un nome qui sopra oppure toccare «Segui» nella pagina di un evento.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (s in seguiti) {
                                InputChip(
                                    selected = false,
                                    onClick = { modello.smetti(s) },
                                    label = { Text("${s.nome} · ${s.tipo.etichetta.lowercase()}") },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Smetti di seguire ${s.nome}") },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("Prossimi eventi dei preferiti e salvati", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            if (prossimi.isEmpty()) {
                item {
                    Text(
                        if (seguiti.isEmpty() && salvati.isEmpty()) "Qui compariranno gli eventi delle squadre, degli atleti e delle competizioni che segui, e quelli che salvi."
                        else "Nessun evento in programma nei prossimi 14 giorni per i tuoi preferiti, secondo i dati scaricati.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(prossimi, key = { it.id }) { e ->
                Column(Modifier.fillMaxWidth()) {
                    Text(Formato.intestazioneGiorno(e.inizio.atZone(org.costalonga.sportintv.raccolta.ROMA).toLocalDate(), adesso), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                    SchedaEvento(e, adesso, Preferiti.corrisponde(e, seguiti), e.id in idSalvati, e.id in promemoria) { apriEvento(e.id) }
                }
            }
            val salvatiAssenti = salvati.filter { s -> eventi.none { it.id == s.eventoId } }
            if (salvatiAssenti.isNotEmpty()) {
                item {
                    Text("Salvati non più presenti nei dati", style = MaterialTheme.typography.titleSmall)
                }
                items(salvatiAssenti, key = { "assente-" + it.eventoId }) { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${s.titolo} · ${Formato.momento(java.time.Instant.ofEpochMilli(s.inizio), adesso)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.TextButton(onClick = { modello.togliSalvato(s.eventoId) }) { Text("Togli") }
                    }
                }
            }
        }
    }
}
