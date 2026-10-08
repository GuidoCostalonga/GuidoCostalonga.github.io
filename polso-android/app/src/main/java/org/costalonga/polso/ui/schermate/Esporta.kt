package org.costalonga.polso.ui.schermate

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.costalonga.polso.motore.analisi.Sezione
import org.costalonga.polso.motore.backup.Backup
import org.costalonga.polso.motore.esporta.Contenuto
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.componenti.Riquadro
import org.costalonga.polso.ui.componenti.SelettorePeriodo
import java.time.LocalDate
import org.costalonga.polso.ui.componenti.Sezione as Titolo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EsportaSchermata(vm: AppViewModel) {
    val p by vm.periodo.collectAsState()
    val imp by vm.impostazioni.collectAsState()
    var contenuti by remember { mutableStateOf(Contenuto.entries.toSet()) }
    var sezioni by remember { mutableStateOf(Sezione.entries.toSet()) }
    var formato by remember { mutableStateOf("pdf") }
    val crea = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> uri?.let { vm.esporta(it, formato, contenuti, sezioni) } }
    var passphrase by remember { mutableStateOf("") }
    var conferma by remember { mutableStateOf("") }
    val creaBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { vm.backup(it, passphrase.toCharArray()); passphrase = ""; conferma = "" } }
    var daRipristinare by remember { mutableStateOf<Uri?>(null) }
    val apriBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { daRipristinare = it }
    var cancella by remember { mutableStateOf(false) }
    val oggi = LocalDate.now()
    val base = "polso-${p.da}-${p.a}"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Titolo("Esportazioni")
        SelettorePeriodo(p, vm.oggi, vm::impostaTipo, vm::sposta, vm::personalizza)
        Text("Contenuti (Excel e CSV)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Contenuto.entries.forEach { c -> FilterChip(selected = c in contenuti, onClick = { contenuti = if (c in contenuti) contenuti - c else contenuti + c }, label = { Text(c.nome) }) }
        }
        Text("Sezioni (PDF)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sezione.entries.forEach { s -> FilterChip(selected = s in sezioni, onClick = { sezioni = if (s in sezioni) sezioni - s else sezioni + s }, label = { Text(s.nome) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { formato = "pdf"; crea.launch("$base.pdf") }, enabled = sezioni.isNotEmpty()) { Text("PDF") }
            Button(onClick = { formato = "xlsx"; crea.launch("$base.xlsx") }, enabled = contenuti.isNotEmpty()) { Text("Excel") }
            Button(onClick = { formato = "csv"; crea.launch(if (contenuti.size == 1) "$base.csv" else "$base-csv.zip") }, enabled = contenuti.isNotEmpty()) { Text("CSV") }
        }
        Text("PDF con riepilogo, grafici, fonti, copertura e limiti. Excel con fogli distinti (misure originali, valori calcolati, sonno, allenamenti, diario, statistiche, definizioni). CSV: un file per contenuto (in un archivio ZIP se più di uno). Date in ora locale con scarto UTC; nessuna stima è presentata come misura.", style = MaterialTheme.typography.bodySmall)
        if (imp.modalitaDemo) Text("Attenzione: sei in modalità dimostrativa, verranno esportati dati sintetici.", color = MaterialTheme.colorScheme.error)

        Titolo("Backup cifrato")
        Riquadro("Come funziona", "Il backup contiene misure, sonno, allenamenti, diario, riepiloghi, impostazioni e versione dello schema, cifrati con AES-256 e una chiave ricavata dalla tua passphrase. Si ripristina su qualsiasi telefono con la stessa passphrase; senza, nessuno può leggerlo (nemmeno tu: non c'è recupero). La chiave dell'IA online non è inclusa.", Icons.Default.CloudUpload)
        Text("Per salvarlo nel cloud scegli Google Drive (o un altro servizio installato) nel selettore che si apre: non serve alcun account aggiuntivo né abbonamento.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Passphrase (almeno ${Backup.LUNGHEZZA_MINIMA} caratteri)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(conferma, { conferma = it }, label = { Text("Ripeti la passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        val problema = Backup.passphraseValida(passphrase.toCharArray()) ?: if (passphrase != conferma) "Le due passphrase non coincidono." else null
        if (passphrase.isNotEmpty()) problema?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { creaBackup.launch("polso-backup-$oggi.polso") }, enabled = problema == null && !imp.modalitaDemo) { Text("Crea backup") }
            OutlinedButton(onClick = { apriBackup.launch(arrayOf("*/*")) }) { Text("Ripristina da backup") }
        }

        Titolo("Cancellazione")
        OutlinedButton(onClick = { cancella = true }) { Text("Cancella tutti i dati dell'app", color = MaterialTheme.colorScheme.error) }
        Text("Elimina archivio, diario, riepiloghi, impostazioni, chiave dell'IA online e modelli scaricati. Non tocca i dati in HONOR Health o in Health Connect.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }

    daRipristinare?.let { uri ->
        var pass by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { daRipristinare = null },
            title = { Text("Ripristinare il backup?") },
            text = {
                Column {
                    Text("I dati attuali dell'app verranno sostituiti da quelli del backup, solo se la passphrase è corretta e il file è integro. Alla fine il ripristino viene verificato.")
                    OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.ripristina(uri, pass.toCharArray()); daRipristinare = null }, enabled = pass.isNotEmpty()) { Text("Ripristina") } },
            dismissButton = { TextButton(onClick = { daRipristinare = null }) { Text("Annulla") } })
    }
    if (cancella) {
        var scritta by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { cancella = false }, icon = { androidx.compose.material3.Icon(Icons.Default.DeleteForever, null) },
            title = { Text("Cancellare tutto?") },
            text = { Column { Text("Operazione irreversibile. Scrivi CANCELLA per confermare."); OutlinedTextField(scritta, { scritta = it }, singleLine = true) } },
            confirmButton = { TextButton(onClick = { vm.cancellaTutto(); cancella = false }, enabled = scritta.trim() == "CANCELLA") { Text("Cancella tutto") } },
            dismissButton = { TextButton(onClick = { cancella = false }) { Text("Annulla") } })
    }
}
