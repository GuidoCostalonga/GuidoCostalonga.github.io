package org.costalonga.polso.ui.schermate

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.launch
import org.costalonga.polso.dati.ConteggioOrigine
import org.costalonga.polso.fonti.HonorHealthKit
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.importa.Mappatura
import org.costalonga.polso.motore.importa.TipoValore
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.componenti.Riquadro
import org.costalonga.polso.ui.componenti.data
import org.costalonga.polso.ui.componenti.Sezione as Titolo

private fun nomeFile(ctx: android.content.Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "file"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontiSchermata(vm: AppViewModel) {
    val ctx = LocalContext.current
    val stato by vm.statoHc.collectAsState()
    val sinc by vm.sincronizzando.collectAsState()
    val registro by vm.registro.collectAsState()
    val imp by vm.impostazioni.collectAsState()
    val zona = imp.preferenze().zona
    val richiesta = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { vm.aggiornaStatoHc() }
    var tipoImport by remember { mutableStateOf("polso") }
    val apriFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importa(it, nomeFile(ctx, it), tipoImport) } }
    var generico by remember { mutableStateOf<Uri?>(null) }
    val apriGenerico = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { generico = it }
    var origini by remember { mutableStateOf<List<ConteggioOrigine>>(emptyList()) }
    LaunchedEffect(registro.size, imp.modalitaDemo) { origini = vm.archivio().dao.origini() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Titolo("Health Connect (fonte principale)")
        val s = stato
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(12.dp)) {
                when {
                    s == null -> CircularProgressIndicator()
                    s.sdk == HealthConnectClient.SDK_UNAVAILABLE -> Text("Health Connect non è disponibile su questo telefono.")
                    s.sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        Text("Health Connect va aggiornato.")
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=com.google.android.apps.healthdata".toUri())) }) { Text("Aggiorna") }
                    }
                    else -> {
                        Text("Stato: disponibile · permessi concessi per ${s.tipiConcessi.size} tipi di dato su ${vm.tipiHc.size}", style = MaterialTheme.typography.bodyMedium)
                        Text("Storico oltre 30 giorni: ${if (s.cronologiaConcessa) "concesso" else if (s.cronologia) "non concesso" else "non supportato"} · Lettura in secondo piano: ${if (s.secondoPianoConcesso) "concessa" else if (s.secondoPiano) "non concessa" else "non supportata"}", style = MaterialTheme.typography.bodySmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            vm.tipiHc.forEach { (k, nome) ->
                                val ok = HealthPermission.getReadPermission(k) in s.permessi
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.RemoveCircleOutline, if (ok) "concesso" else "non concesso", Modifier.size(16.dp),
                                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                    Spacer(Modifier.width(3.dp)); Text(nome, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { richiesta.launch(vm.permessiHc) }) { Text("Concedi i permessi") }
                            OutlinedButton(onClick = { vm.sincronizza() }, enabled = !sinc && s.tipiConcessi.isNotEmpty()) { Text(if (sinc) "In corso…" else "Sincronizza ora") }
                        }
                        TextButton(onClick = { runCatching { ctx.startActivity(Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, ctx.packageName)) }
                            .onFailure { runCatching { ctx.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } } }) { Text("Apri le impostazioni di Health Connect") }
                    }
                }
                val ultima = registro.firstOrNull { it.fonte == "health_connect" && it.esito == "riuscita" }
                Text("Ultima sincronizzazione riuscita: ${data(ultima?.conclusaIl, zona)}${ultima?.da?.let { " · intervallo letto dal ${data(it, zona).take(10)}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                registro.firstOrNull { it.fonte == "health_connect" }?.takeIf { it.esito != "riuscita" }?.let { Text("Ultimo tentativo: ${it.esito}. ${it.errori}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text("Aggiornamento automatico: ${if (imp.sincronizzazioneAutomatica) "ogni ${imp.oreSincronizzazione} ore circa, quando Android lo consente (MagicOS può ritardarlo per risparmiare batteria)" else "disattivato"}. Non è un monitoraggio continuo.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Riquadro("Come collegare HONOR Health",
            "1) In HONOR Health apri Io (o Profilo) › Impostazioni › Health Connect e attiva la sincronizzazione con Health Connect (la voce esatta può cambiare con la versione dell'app).\n" +
                "2) In Impostazioni di Android › Health Connect › Autorizzazioni app verifica che HONOR Health possa SCRIVERE i dati.\n" +
                "3) Torna qui e premi «Concedi i permessi», compresi storico e secondo piano, poi «Sincronizza ora».\n" +
                "Secondo i permessi dichiarati da HONOR Health, arrivano passi, distanza, calorie, frequenza cardiaca e a riposo, sonno, SpO₂, allenamenti e peso. Non risultano condivisi: stress, HRV, respirazione, temperatura cutanea, percorsi GPS. Da verificare sul tuo telefono: fasi del sonno e quanto storico HONOR Health copia in Health Connect.")
        if (origini.isNotEmpty()) {
            Titolo("Origini presenti e priorità")
            Text("Se telefono e orologio registrano la stessa cosa, si usa l'origine più in alto; mai la somma.", style = MaterialTheme.typography.bodySmall)
            val ordinate = (imp.prioritaOrigini + origini.map { it.origine }).distinct().filter { o -> origini.any { it.origine == o } }
            ordinate.forEachIndexed { i, o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}. $o (${origini.firstOrNull { it.origine == o }?.n ?: 0} misure)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (i > 0) IconButton(onClick = { vm.modifica { it.copy(prioritaOrigini = ordinate.toMutableList().apply { removeAt(i); add(i - 1, o) }) } }) { Icon(Icons.Default.ArrowUpward, "Più prioritaria") }
                }
            }
        }
        Titolo("HONOR Health Kit (accesso diretto)")
        Riquadro("${HonorHealthKit.info.nome}: ${HonorHealthKit.info.stato.etichetta}", HonorHealthKit.info.dettaglio, Icons.Default.Block)

        Titolo("Importazione da file")
        Text("Per dati che non passano da Health Connect. Le importazioni ripetute non creano doppioni.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tipoImport = "polso"; apriFile.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("CSV di Polso") }
            OutlinedButton(onClick = { apriGenerico.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("CSV di altro formato") }
            OutlinedButton(onClick = { tipoImport = "gpx"; apriFile.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream")) }) { Text("Traccia GPX") }
            OutlinedButton(onClick = { tipoImport = "tcx"; apriFile.launch(arrayOf("application/vnd.garmin.tcx+xml", "application/xml", "text/xml", "application/octet-stream")) }) { Text("Allenamento TCX") }
        }
        Text("Il formato CSV di Polso è descritto in IMPORTAZIONE.md. Un archivio ottenuto da HONOR con una richiesta privacy non ha un formato pubblicato: si può importare con «CSV di altro formato» scegliendo le colonne.", style = MaterialTheme.typography.bodySmall)

        Titolo("Registro delle sincronizzazioni e importazioni")
        registro.take(20).forEach { r ->
            Text("${data(r.avviataIl, zona)} · ${r.descrizione} · ${r.esito} · ${r.nuovi} nuovi, ${r.aggiornati} aggiornati, ${r.eliminati} eliminati", style = MaterialTheme.typography.bodySmall)
            if (r.errori.isNotBlank()) Text(r.errori.lineSequence().take(3).joinToString("\n"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
    }
    generico?.let { uri -> MappaturaCsv(vm, uri, nomeFile(ctx, uri)) { generico = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappaturaCsv(vm: AppViewModel, uri: Uri, nome: String, chiudi: () -> Unit) {
    var righe by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    LaunchedEffect(uri) { righe = runCatching { vm.anteprimaCsv(uri) }.getOrDefault(emptyList()) }
    var metrica by remember { mutableStateOf(Metrica.PASSI) }
    var cIni by remember { mutableStateOf("0") }
    var cFine by remember { mutableStateOf("") }
    var cVal by remember { mutableStateOf("1") }
    var unita by remember { mutableStateOf("") }
    var formato by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(TipoValore.ISTANTANEO) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = chiudi,
        title = { Text("Importa $nome") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Anteprima (le colonne sono numerate da 0):", style = MaterialTheme.typography.labelMedium)
                righe.take(5).forEach { r -> Text(r.mapIndexed { i, c -> "[$i] $c" }.joinToString("  "), style = MaterialTheme.typography.labelSmall, maxLines = 2) }
                ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                    OutlinedTextField("${metrica.nome} (${metrica.unita})", {}, readOnly = true, label = { Text("Grandezza") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { Metrica.entries.forEach { m -> DropdownMenuItem(text = { Text(m.nome) }, onClick = { metrica = m; menu = false }) } }
                }
                OutlinedTextField(cIni, { cIni = it }, label = { Text("Colonna data/ora (inizio)") }, singleLine = true)
                OutlinedTextField(cFine, { cFine = it }, label = { Text("Colonna fine (facoltativa)") }, singleLine = true)
                OutlinedTextField(cVal, { cVal = it }, label = { Text("Colonna valore") }, singleLine = true)
                OutlinedTextField(unita, { unita = it }, label = { Text("Unità del file (vuoto = ${metrica.unita})") }, singleLine = true)
                OutlinedTextField(formato, { formato = it }, label = { Text("Formato data (es. dd/MM/yyyy HH:mm; vuoto = automatico)") }, singleLine = true)
                Text("Tipo di valore", style = MaterialTheme.typography.labelMedium)
                Row { TipoValore.entries.forEach { t -> TextButton(onClick = { tipo = t }) { Text((if (tipo == t) "● " else "○ ") + t.name.lowercase()) } } }
                Text("«cumulativo»: contatore che cresce (es. passi da mezzanotte); viene convertito in incrementi, mai sommato così com'è.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ini = cIni.toIntOrNull(); val v = cVal.toIntOrNull()
                if (ini != null && v != null) {
                    vm.importa(uri, nome, "generico", Mappatura(metrica, ini, cFine.toIntOrNull(), v, unita, formato.ifBlank { null }, tipo))
                    chiudi()
                }
            }) { Text("Importa") }
        },
        dismissButton = { TextButton(onClick = chiudi) { Text("Annulla") } },
    )
}
