package org.costalonga.sportintv.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.costalonga.sportintv.BuildConfig
import org.costalonga.sportintv.dati.Origine
import org.costalonga.sportintv.dati.Tema
import org.costalonga.sportintv.promemoria.Pianificatore
import org.costalonga.sportintv.raccolta.StatoFonte
import org.costalonga.sportintv.raccolta.TipoFonte
import java.time.Instant

@Composable
private fun Titolo(testo: String) {
    Text(testo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
}

@Composable
private fun Interruttore(titolo: String, spiegazione: String?, attivo: Boolean, cambia: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(attivo, role = Role.Switch, onValueChange = cambia).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titolo, style = MaterialTheme.typography.bodyLarge)
            spiegazione?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = attivo, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchermataImpostazioni(modello: Modello, apriInformazioni: () -> Unit) {
    val pref by modello.preferenze.collectAsState()
    val stato by modello.statoAggiornamento.collectAsState()
    val inAggiornamento by modello.inAggiornamento.collectAsState()
    val adesso by modello.adesso.collectAsState()
    val context = LocalContext.current
    var notificheOk by rememberSaveable { mutableStateOf(Pianificatore.notificheConsentite(context)) }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificheOk = it }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Impostazioni", fontWeight = FontWeight.Bold) })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Titolo("Promemoria")
            Text("Anticipo dell'avviso", style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in listOf(5, 15, 30, 60)) {
                    FilterChip(selected = pref.anticipoMinuti == m, onClick = { modello.anticipo(m) }, label = { Text("$m minuti") })
                }
            }
            Interruttore(
                "Promemoria automatici per i preferiti",
                "Un avviso per ogni evento delle squadre, degli atleti e delle competizioni che segui",
                pref.promemoriaPreferiti,
            ) {
                if (it && Build.VERSION.SDK_INT >= 33 && !Pianificatore.notificheConsentite(context)) richiesta.launch(Manifest.permission.POST_NOTIFICATIONS)
                modello.promemoriaPreferiti(it)
            }
            if (!notificheOk) {
                Riquadro(
                    titolo = "Notifiche disattivate",
                    testo = "Senza il permesso per le notifiche i promemoria non possono avvisarti.",
                    avviso = true,
                    azione = "Consenti le notifiche" to {
                        if (Build.VERSION.SDK_INT >= 33) richiesta.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    },
                )
            }
            Text(
                "Gli avvisi usano gli allarmi standard di Android, senza il permesso per le sveglie esatte: col telefono in risparmio energetico possono arrivare con qualche minuto di ritardo. " +
                    "Dopo un riavvio del telefono o un cambio di fuso orario i promemoria vengono ripianificati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            Titolo("Aggiornamento dei dati")
            val ricevuto = stato.ricevuto?.let { Instant.ofEpochMilli(it) }
            val generato = stato.generato?.let { Instant.ofEpochMilli(it) }
            Text(
                if (ricevuto != null) "Ultimo aggiornamento riuscito: ${Formato.momento(ricevuto, adesso)}" +
                    (generato?.let { "\nDati raccolti dalle fonti ${Formato.momento(it, adesso)} (${if (stato.provenienza == "servizio") "dal servizio online" else "direttamente dal telefono"})" } ?: "")
                else "Nessun aggiornamento riuscito finora.",
                style = MaterialTheme.typography.bodyMedium,
            )
            stato.ultimoErrore?.takeIf { (stato.ultimoTentativo ?: 0) > (stato.ricevuto ?: 0) }?.let {
                Text("Ultimo tentativo non riuscito: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { modello.aggiorna() }, enabled = !inAggiornamento) { Text(if (inAggiornamento) "Aggiornamento in corso…" else "Aggiorna adesso") }
            Interruttore(
                "Aggiornamento automatico",
                "Ogni 6 ore circa, solo con la rete e la batteria non scarica",
                pref.aggiornamentoAutomatico,
            ) { modello.aggiornamentoAutomatico(it) }
            Text("Provenienza dei dati", style = MaterialTheme.typography.bodyLarge)
            for (o in Origine.entries) {
                Row(
                    Modifier.fillMaxWidth().selectable(pref.origine == o, role = Role.RadioButton) { modello.origine(o) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = pref.origine == o, onClick = null)
                    Spacer(Modifier.size(8.dp))
                    Column {
                        Text(o.etichetta, style = MaterialTheme.typography.bodyLarge)
                        Text(o.spiegazione, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            var url by rememberSaveable(pref.urlServizio) { mutableStateOf(pref.urlServizio) }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Indirizzo del servizio online") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { modello.urlServizio(url) }, enabled = url != pref.urlServizio) { Text("Salva indirizzo") }
                TextButton(onClick = { modello.urlServizio(""); url = BuildConfig.URL_SERVIZIO }) { Text("Ripristina") }
            }

            HorizontalDivider()
            Titolo("Aspetto")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in Tema.entries) FilterChip(selected = pref.tema == t, onClick = { modello.tema(t) }, label = { Text(t.etichetta) })
            }
            if (Build.VERSION.SDK_INT >= 31) {
                Interruttore("Colori dello sfondo del telefono", "Usa la tavolozza di Android al posto dei colori dell'app", pref.coloriDinamici) { modello.coloriDinamici(it) }
            }
            Text(
                "Il testo segue la dimensione dei caratteri impostata in Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()
            OutlinedButton(onClick = apriInformazioni, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Info, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Informazioni, fonti e licenze")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataInformazioni(modello: Modello, indietro: () -> Unit) {
    val istantanea by modello.istantanea.collectAsState()
    val adesso by modello.adesso.collectAsState()
    val context = LocalContext.current
    val fonti = istantanea?.fonti.orEmpty()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Informazioni") },
            navigationIcon = { IconButton(onClick = indietro) { Icon(Icons.Filled.ArrowBack, contentDescription = "Indietro") } },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sport in TV Italia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Versione ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Realizzato da Guido Costalonga. Tutti i diritti riservati.",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Guida agli eventi sportivi trasmessi legalmente in Italia oggi e nei 14 giorni successivi, in TV in chiaro, pay TV e streaming. " +
                    "L'app non trasmette, non incorpora e non ritrasmette alcun contenuto: rimanda alle pagine ufficiali delle emittenti e dei servizi.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Titolo("Che cosa copre")
            Text(
                "Tutti gli sport che compaiono nei palinsesti delle fonti integrate. Le fonti gratuite non coprono però tutta l'offerta italiana: " +
                    "mancano in particolare Sky Sport, NOW, TV8 e Cielo (la guida TV di Sky è protetta da un controllo anti-automazione che l'app non aggira), " +
                    "Prime Video, Eurosport e HBO Max (tranne gli eventi Eurosport pubblicati nel palinsesto DAZN), Sportitalia e le emittenti locali.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "«Trasmissione confermata» significa che l'evento compare nel palinsesto ufficiale di un'emittente o piattaforma. " +
                    "«Da confermare» significa che l'evento è nel calendario della competizione ma in nessun palinsesto consultato.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Titolo("Fonti integrate")
            if (fonti.isEmpty()) Text("Le fonti compariranno dopo il primo aggiornamento.", style = MaterialTheme.typography.bodyMedium)
            for (f in fonti) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(f.nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (f.tipo == TipoFonte.PALINSESTO) "Palinsesto: dice chi trasmette e quando. " else "Calendario: dice quando si gioca, non chi trasmette. ") + f.copertura,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (f.canali.isNotEmpty()) Text("Canali: ${f.canali.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    Text("Condizioni: ${f.condizioni}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Stato: " + when (f.stato) {
                            StatoFonte.OK -> "funzionante"
                            StatoFonte.VUOTA -> "funzionante, nessun evento sportivo nel periodo"
                            StatoFonte.PARZIALE -> "dati parziali"
                            StatoFonte.ERRORE -> "non raggiungibile"
                            StatoFonte.DATI_PRECEDENTI -> "non raggiungibile, restano i dati precedenti"
                        } + " · verificata ${Formato.momento(f.ultimaVerifica, adesso)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { apriLink(context, f.url) }) { Text("Apri la fonte") }
                }
            }

            Titolo("Attribuzioni e licenze")
            Text(
                "• Calendario della Formula 1: Jolpica F1 (github.com/jolpica/jolpica-f1), dati con licenza CC BY-NC-SA 4.0, uso non commerciale.\n" +
                    "• Calendario della Serie A: progetto openfootball (github.com/openfootball/football.json), dati di pubblico dominio CC0 1.0.\n" +
                    "• Palinsesti: RaiPlay (Rai), Mediaset Infinity (Mediaset), DAZN, SuperTennis. Marchi e contenuti appartengono ai rispettivi titolari; l'app ne riporta solo orari e titoli rimandando alle pagine ufficiali.\n" +
                    "• Librerie: AndroidX, Jetpack Compose, Material Design 3 e icone Material (Apache 2.0, Google); Kotlin e kotlinx (Apache 2.0, JetBrains); OkHttp (Apache 2.0, Square).",
                style = MaterialTheme.typography.bodySmall,
            )

            Titolo("Limiti noti")
            Text(
                "• Rai non indica se un programma è in diretta o in replica: l'app lo scrive invece di indovinarlo.\n" +
                    "• Le fonti pubblicano i palinsesti con circa una settimana di anticipo: oltre, gli elenchi sono incompleti.\n" +
                    "• Gli stati «rinviato» e «annullato» compaiono solo se una fonte li dichiara.\n" +
                    "• I promemoria possono arrivare con qualche minuto di ritardo quando Android risparmia batteria.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
