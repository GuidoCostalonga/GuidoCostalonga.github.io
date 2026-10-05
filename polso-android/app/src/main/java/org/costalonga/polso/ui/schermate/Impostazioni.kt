package org.costalonga.polso.ui.schermate

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.costalonga.polso.dati.Impostazioni
import org.costalonga.polso.dati.ProfiloIa
import org.costalonga.polso.ia.StatoScaricamento
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.MetodoZone
import org.costalonga.polso.motore.Regole
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.componenti.Riquadro
import org.costalonga.polso.ui.componenti.data
import org.costalonga.polso.ui.componenti.Sezione as Titolo

@Composable
private fun Interruttore(testo: String, valore: Boolean, descrizione: String? = null, cambia: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(testo, style = MaterialTheme.typography.bodyLarge)
            descrizione?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(valore, cambia)
    }
}

@Composable
private fun CampoNumero(etichetta: String, valore: Int?, vuotoConsentito: Boolean = true, salva: (Int?) -> Unit) {
    var t by remember(valore) { mutableStateOf(valore?.toString() ?: "") }
    OutlinedTextField(t, { nuovo -> t = nuovo.filter { it.isDigit() }.take(6); val n = t.toIntOrNull(); if (n != null || (vuotoConsentito && t.isEmpty())) salva(n) },
        label = { Text(etichetta) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImpostazioniSchermata(vm: AppViewModel) {
    val i by vm.impostazioni.collectAsState()
    val notifiche by vm.notifiche.collectAsState()
    val prova by vm.ultimaProva.collectAsState()
    val scaricamento by vm.c.modelli.stato.collectAsState()
    val permessoNotifiche = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val zona = i.preferenze().zona
    fun mod(f: (Impostazioni) -> Impostazioni) = vm.modifica(f)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Titolo("Obiettivi e dati personali")
        CampoNumero("Obiettivo di passi al giorno", i.obiettivoPassi, false) { it?.let { n -> mod { s -> s.copy(obiettivoPassi = n.coerceIn(500, 50_000)) } } }
        CampoNumero("Obiettivo di sonno (minuti)", i.obiettivoSonnoMin, false) { it?.let { n -> mod { s -> s.copy(obiettivoSonnoMin = n.coerceIn(240, 720)) } } }
        Text("Obiettivo di sonno: ${Formato.durata(i.obiettivoSonnoMin.toDouble())}. È una tua scelta, non una prescrizione.", style = MaterialTheme.typography.bodySmall)
        CampoNumero("Anno di nascita (facoltativo, per la FC massima stimata)", i.annoNascita) { n -> mod { it.copy(annoNascita = n?.takeIf { a -> a in 1900..2025 }) } }
        CampoNumero("FC massima misurata (facoltativa, bpm)", i.fcMax) { n -> mod { it.copy(fcMax = n?.takeIf { v -> v in 100..230 }) } }
        CampoNumero("FC a riposo di riferimento (facoltativa, bpm)", i.fcRiposo) { n -> mod { it.copy(fcRiposo = n?.takeIf { v -> v in 30..120 }) } }
        Text("Metodo delle zone cardiache", style = MaterialTheme.typography.labelLarge)
        MetodoZone.entries.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = i.metodoZone == m.name, onClick = { mod { it.copy(metodoZone = m.name) } }, label = { Text(m.nome) })
            }
            Text(m.descrizione, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(i.zona, { z -> mod { it.copy(zona = z.trim()) } }, label = { Text("Fuso orario (IANA)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Unità: sistema metrico. Lingua: italiano.", style = MaterialTheme.typography.bodySmall)

        Titolo("Aggiornamenti automatici")
        Interruttore("Sincronizzazione periodica con Health Connect", i.sincronizzazioneAutomatica, "Richiede il permesso di lettura in secondo piano. Android e MagicOS decidono il momento esatto; in Impostazioni › Batteria conviene consentire l'attività in background a Polso.") { v -> mod { it.copy(sincronizzazioneAutomatica = v) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(3, 6, 12, 24).forEach { h -> FilterChip(selected = i.oreSincronizzazione == h, onClick = { mod { it.copy(oreSincronizzazione = h) } }, label = { Text("ogni $h ore") }) }
        }

        Titolo("Notifiche (solo variazioni importanti)")
        Interruttore("Notifiche attive", i.notifiche.attive, "Regole fisse sul tuo storico personale; nessuna soglia clinica, nessuna IA.") { v -> mod { it.copy(notifiche = it.notifiche.copy(attive = v)) } }
        if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { permessoNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Consenti le notifiche ad Android") }
        Regole.tutte.forEach { r ->
            Interruttore(r.nome, r.id in i.notifiche.regoleAttive, r.descrizione) { v -> mod { it.copy(notifiche = it.notifiche.copy(regoleAttive = if (v) it.notifiche.regoleAttive + r.id else it.notifiche.regoleAttive - r.id)) } }
        }
        Text("Giorni consecutivi richiesti", style = MaterialTheme.typography.labelLarge)
        Row { listOf(2, 3, 4, 5).forEach { n -> FilterChip(selected = i.notifiche.giorniPersistenza == n, onClick = { mod { it.copy(notifiche = it.notifiche.copy(giorniPersistenza = n)) } }, label = { Text("$n") }, modifier = Modifier.padding(end = 6.dp)) } }
        CampoNumero("Intervallo minimo fra notifiche (ore)", i.notifiche.intervalloMinimoOre, false) { it?.let { n -> mod { s -> s.copy(notifiche = s.notifiche.copy(intervalloMinimoOre = n.coerceIn(1, 168))) } } }
        CampoNumero("Ore silenziose: dalle", i.notifiche.silenzioDa, false) { it?.let { n -> mod { s -> s.copy(notifiche = s.notifiche.copy(silenzioDa = n.coerceIn(0, 23))) } } }
        CampoNumero("Ore silenziose: alle", i.notifiche.silenzioA, false) { it?.let { n -> mod { s -> s.copy(notifiche = s.notifiche.copy(silenzioA = n.coerceIn(0, 23))) } } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.provaRegole() }) { Text("Verifica le regole sui dati") }
            OutlinedButton(onClick = { vm.valutaOra() }) { Text("Valuta e invia ora") }
        }
        prova?.let { l -> Text(if (l.isEmpty()) "Nessuna regola scatterebbe ora." else l.joinToString("\n\n") { "${it.titolo}: ${it.testo}\nMotivo: ${it.motivo}" }, style = MaterialTheme.typography.bodySmall) }
        if (notifiche.isNotEmpty()) {
            Text("Notifiche inviate", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            notifiche.take(10).forEach { Text("${data(it.inviataIl, zona)} · ${it.titolo} · ${it.motivo}", style = MaterialTheme.typography.bodySmall) }
        }

        Titolo("Intelligenza artificiale")
        Text("L'app funziona anche senza modello: i riepiloghi e le risposte sono allora scritti dal calcolo. Il modello locale lavora solo sul telefono.", style = MaterialTheme.typography.bodySmall)
        ProfiloIa.entries.forEach { p ->
            val installato = vm.c.modelli.installato(p)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.nome + if (i.profiloIa == p.name) " · in uso" else "", style = MaterialTheme.typography.titleSmall)
                    Text("${p.descrizione} Licenza: ${p.licenza}. Download da Hugging Face: ${Formato.numero(p.byte / 1_000_000.0)} MB, con verifica dell'impronta.", style = MaterialTheme.typography.bodySmall)
                    val st = scaricamento
                    when {
                        st is StatoScaricamento.InCorso && st.profilo == p -> {
                            LinearProgressIndicator(progress = { st.scaricati.toFloat() / st.totale }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            Text("${Formato.numero(st.scaricati / 1_000_000.0)} di ${Formato.numero(st.totale / 1_000_000.0)} MB", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { vm.annullaScaricamento() }) { Text("Annulla") }
                        }
                        st is StatoScaricamento.Verifica && st.profilo == p -> Text("Verifica dell'impronta in corso…")
                        else -> {
                            if (st is StatoScaricamento.Errore && st.profilo == p) Text(st.messaggio, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!installato) OutlinedButton(onClick = { vm.scarica(p) }) { Text(if (vm.c.modelli.scaricatoParziale(p) > 0) "Riprendi il download" else "Scarica") }
                                if (installato && i.profiloIa != p.name) OutlinedButton(onClick = { mod { it.copy(profiloIa = p.name) } }) { Text("Usa questo") }
                                if (installato || vm.c.modelli.scaricatoParziale(p) > 0) TextButton(onClick = { vm.eliminaModello(p) }) { Text("Elimina") }
                            }
                        }
                    }
                }
            }
        }
        Text("Servizio online (facoltativo)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Interruttore("Consenti il servizio online", i.onlineAttivo, "Disattivato all'inizio. Ogni invio mostra prima il testo esatto e chiede conferma. Non c'è mai un passaggio automatico dal locale all'online.") { v -> mod { it.copy(onlineAttivo = v) } }
        if (i.onlineAttivo) {
            OutlinedTextField(i.onlineIndirizzo, { v -> mod { it.copy(onlineIndirizzo = v.trim()) } }, label = { Text("Indirizzo compatibile OpenAI") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(i.onlineModello, { v -> mod { it.copy(onlineModello = v.trim()) } }, label = { Text("Modello") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            var chiave by remember { mutableStateOf("") }
            OutlinedTextField(chiave, { chiave = it }, label = { Text(if (vm.c.segreti.presente()) "Chiave salvata (inseriscine una nuova per cambiarla)" else "Chiave API") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row { TextButton(onClick = { vm.salvaChiaveOnline(chiave); chiave = "" }) { Text("Salva chiave") }; TextButton(onClick = { vm.salvaChiaveOnline(null) }) { Text("Elimina chiave") } }
            Text("Predefinito: Google Gemini tramite la sua interfaccia compatibile OpenAI. Il piano gratuito di Google AI Studio ha limiti giornalieri e può cambiare; sul piano gratuito Google può usare i contenuti per migliorare i propri servizi (verifica i termini aggiornati).", style = MaterialTheme.typography.bodySmall)
        }

        Titolo("Sicurezza, aspetto e modalità dimostrativa")
        Interruttore("Blocco con impronta o PIN", i.bloccoBiometrico, "Chiede lo sblocco a ogni apertura dell'app.") { v -> mod { it.copy(bloccoBiometrico = v) } }
        Text("Tema", style = MaterialTheme.typography.labelLarge)
        Row { listOf("sistema" to "Come il sistema", "chiaro" to "Chiaro", "scuro" to "Scuro").forEach { (k, n) -> FilterChip(selected = i.tema == k, onClick = { mod { it.copy(tema = k) } }, label = { Text(n) }, modifier = Modifier.padding(end = 6.dp)) } }
        Text("La dimensione del testo segue quella scelta in Impostazioni › Display di Android.", style = MaterialTheme.typography.bodySmall)
        Interruttore("Modalità dimostrativa", i.modalitaDemo, "Mostra dati sintetici in un archivio separato, chiaramente etichettati. I tuoi dati non vengono toccati.") { v -> vm.modalitaDemo(v) }

        Titolo("Privacy: quali dati escono dal telefono")
        Riquadro("Riepilogo", """
• Archivio, analisi e IA locale: tutto resta sul telefono. Il backup automatico di Android è disattivato per questa app.
• Download del modello: si contatta huggingface.co per scaricare il file; non si inviano dati personali.
• IA online (solo se attivata e confermata ogni volta): esce il testo mostrato nella finestra di conferma, verso l'indirizzo configurato.
• Backup: esce solo se lo salvi tu in una cartella cloud, ed è cifrato con la tua passphrase.
• Esportazioni PDF, Excel e CSV: escono solo dove le salvi o condividi tu, e non sono cifrate.
• Nessuna pubblicità, nessuna statistica d'uso, nessun server dell'app. I dati sanitari non vengono scritti nei registri di sistema.
""".trimIndent(), Icons.Default.PrivacyTip)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PrimoAvvio(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Benvenuto in Polso", style = MaterialTheme.typography.headlineSmall)
        Text("Polso legge i dati del tuo smartwatch da Health Connect (dove HONOR Health li copia), li conserva solo sul telefono e li trasforma in statistiche, grafici e riepiloghi comprensibili.")
        Riquadro("Cosa serve", "1) In HONOR Health attiva la sincronizzazione con Health Connect.\n2) In Polso concedi i permessi di lettura (compresi storico e secondo piano).\n3) Premi «Sincronizza ora».")
        Riquadro("Cosa non fa", "Non sostituisce il medico e non fa diagnosi. Non invia dati a nessuno senza il tuo consenso esplicito. Non ha pubblicità né abbonamenti.")
        Button(onClick = { vm.modifica { it.copy(primoAvvioFatto = true) } }, modifier = Modifier.fillMaxWidth()) { Text("Inizia con i miei dati") }
        OutlinedButton(onClick = { vm.modalitaDemo(true); vm.modifica { it.copy(primoAvvioFatto = true) } }, modifier = Modifier.fillMaxWidth()) { Text("Prova con dati dimostrativi") }
    }
}
