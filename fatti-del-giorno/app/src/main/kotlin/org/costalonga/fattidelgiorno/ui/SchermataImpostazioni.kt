package org.costalonga.fattidelgiorno.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.costalonga.fattidelgiorno.FattiApp
import org.costalonga.fattidelgiorno.sicurezza.ChiaveSegreta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataImpostazioni(app: FattiApp, chiudi: () -> Unit) {
    val contesto = LocalContext.current
    var wikipediaInglese by remember { mutableStateOf(app.impostazioni.wikipediaInglese) }
    var wikidataItalia by remember { mutableStateOf(app.impostazioni.wikidataItalia) }
    var modello by remember { mutableStateOf(app.impostazioni.modelloGemini) }
    var chiave by remember { mutableStateOf("") }
    var chiavePresente by remember { mutableStateOf(app.chiavi.presente(ChiaveSegreta.GEMINI)) }
    var mostraChiave by remember { mutableStateOf(false) }
    var messaggio by remember { mutableStateOf<String?>(null) }
    var dateInMemoria by remember { mutableStateOf(app.cache.quanteDate()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = chiudi) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { margini ->
        Column(
            Modifier.fillMaxSize().padding(margini).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Fonti", style = MaterialTheme.typography.titleLarge)
            Text("Wikipedia in italiano, Wikidata (persone nate o scomparse in Friuli Venezia Giulia) e l'archivio delle ricorrenze verificate sono sempre attivi. Nessuna fonte richiede una chiave.", style = MaterialTheme.typography.bodyMedium)
            RigaInterruttore("Wikipedia in inglese (selezione del giorno, area mondo)", wikipediaInglese) {
                wikipediaInglese = it; app.impostazioni.wikipediaInglese = it
            }
            RigaInterruttore("Wikidata: persone nate in Italia (oltre a quelle del FVG)", wikidataItalia) {
                wikidataItalia = it; app.impostazioni.wikidataItalia = it
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Date memorizzate sul telefono: $dateInMemoria", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { app.cache.svuota(); dateInMemoria = 0 }) { Text("Svuota") }
            }
            HorizontalDivider()

            Text("Servizio AI facoltativo: Gemini", style = MaterialTheme.typography.titleLarge)
            Text(
                "L'app funziona per intero senza questa chiave: la bozza e il prompt vengono creati sul telefono. " +
                    "Se vuoi far rifinire la bozza da Gemini, crea una chiave gratuita su Google AI Studio (aistudio.google.com) e incollala qui.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Come viene usata e conservata: la chiave è cifrata con una chiave del Keystore di Android che non lascia il telefono, " +
                    "è esclusa dal backup, non compare nei registri e viene inviata solo a Google, nell'intestazione della richiesta, " +
                    "quando premi \"Rifinisci con Gemini\". Il prompt inviato contiene il fatto, le fonti e la bozza.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Limiti e condizioni: il piano gratuito ha quote di richieste al minuto e al giorno stabilite da Google (se finiscono, l'app lo dice e la bozza locale resta disponibile). " +
                    "Nel piano gratuito Google dichiara di poter usare i contenuti inviati per migliorare i propri prodotti: non incollare dati personali o riservati.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(if (chiavePresente) "Stato: chiave salvata sul telefono" else "Stato: nessuna chiave salvata", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = chiave,
                onValueChange = { chiave = it },
                label = { Text(if (chiavePresente) "Nuova chiave (sostituisce quella salvata)" else "Chiave API Gemini") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (mostraChiave) VisualTransformation.None else PasswordVisualTransformation(),
            )
            RigaInterruttore("Mostra la chiave mentre la scrivo", mostraChiave) { mostraChiave = it }
            OutlinedTextField(
                value = modello,
                onValueChange = { modello = it },
                label = { Text("Modello (predefinito: gemini-2.5-flash)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    app.impostazioni.modelloGemini = modello
                    if (chiave.isNotBlank()) {
                        val esito = runCatching { app.chiavi.salva(ChiaveSegreta.GEMINI, chiave) }
                        messaggio = esito.fold(
                            onSuccess = { chiave = ""; chiavePresente = true; "Chiave salvata in forma cifrata" },
                            onFailure = { "Salvataggio non riuscito: ${it.message}" },
                        )
                    } else {
                        messaggio = "Impostazioni salvate"
                    }
                }) { Text("Salva") }
                if (chiavePresente) {
                    OutlinedButton(onClick = {
                        app.chiavi.cancella(ChiaveSegreta.GEMINI)
                        chiavePresente = false
                        messaggio = "Chiave eliminata dal telefono"
                    }) { Text("Elimina la chiave") }
                }
            }
            messaggio?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold) }
            HorizontalDivider()

            Text("Informazioni", style = MaterialTheme.typography.titleLarge)
            Text(
                "I testi di Wikipedia sono rilasciati con licenza CC BY-SA 4.0 e i dati di Wikidata con licenza CC0: per questo ogni scheda porta il collegamento alla voce di origine. " +
                    "L'app non pubblica nulla sui social e non chiede l'accesso a nessun profilo: copia e condividi i testi con le funzioni di Android.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(1.dp))
            OutlinedButton(onClick = { Condivisione.apri(contesto, "https://costalonga.org/fatti-del-giorno/") }) { Text("Istruzioni e codice sorgente") }
        }
    }
}

@Composable
private fun RigaInterruttore(testo: String, valore: Boolean, cambia: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(testo, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = valore, onCheckedChange = cambia)
    }
}
