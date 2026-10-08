package org.costalonga.polso.ui.schermate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.costalonga.polso.dati.DiarioEntita
import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.TipoDiario
import org.costalonga.polso.motore.VoceDiario
import org.costalonga.polso.motore.importa.Numeri
import org.costalonga.polso.ui.AppViewModel
import org.costalonga.polso.ui.componenti.Etichetta
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun DiarioSchermata(vm: AppViewModel) {
    val voci by vm.diario.collectAsState()
    val zona = vm.impostazioni.collectAsState().value.preferenze().zona
    var modifica by remember { mutableStateOf<DiarioEntita?>(null) }
    var nuova by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Text("Annotazioni facoltative, sempre modificabili ed eliminabili. Compaiono come «inserimento manuale» in analisi ed esportazioni. Nessun campo è obbligatorio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (voci.isEmpty()) item { Text("Nessuna voce. Usa il pulsante «Aggiungi».", Modifier.padding(16.dp)) }
            val perGiorno = voci.groupBy { Tempo.giorno(it.istante, zona) }
            perGiorno.forEach { (g, lista) ->
                item(key = "g$g") { Text(Tempo.etichetta(g), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }
                items(lista, key = { it.id }) { v ->
                    val t = TipoDiario.daCodice(v.tipo)
                    Row(Modifier.fillMaxWidth().clickable { modifica = v }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t?.nome ?: v.tipo} · ${Formato.orario(Tempo.minutoDelGiorno(v.istante, zona).toDouble())}", style = MaterialTheme.typography.bodyLarge)
                            val valore = when {
                                t == TipoDiario.PRESSIONE -> "${Formato.numero(v.valore)}/${Formato.numero(v.valore2)} mmHg"
                                v.valore != null -> Formato.conUnita(v.valore, t?.unita.orEmpty(), 1)
                                else -> ""
                            }
                            Text(listOf(valore, v.testo).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        }
                        Etichetta("Manuale", Icons.Default.Edit)
                    }
                    HorizontalDivider()
                }
            }
            item { Spacer(Modifier.height(90.dp)) }
        }
        ExtendedFloatingActionButton(onClick = { nuova = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Aggiungi") }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }
    if (nuova || modifica != null) EditorVoce(vm, modifica, zona) { nuova = false; modifica = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorVoce(vm: AppViewModel, esistente: DiarioEntita?, zona: java.time.ZoneId, chiudi: () -> Unit) {
    val adesso = LocalDateTime.now(zona)
    val iniziale = esistente?.let { Instant.ofEpochMilli(it.istante).atZone(zona).toLocalDateTime() } ?: adesso
    var tipo by remember { mutableStateOf(esistente?.tipo?.let { TipoDiario.daCodice(it) } ?: TipoDiario.PESO) }
    var valore by remember { mutableStateOf(esistente?.valore?.let { Formato.numero(it, 1) } ?: "") }
    var valore2 by remember { mutableStateOf(esistente?.valore2?.let { Formato.numero(it) } ?: "") }
    var testo by remember { mutableStateOf(esistente?.testo ?: "") }
    var giorno by remember { mutableStateOf(iniziale.toLocalDate()) }
    var ora by remember { mutableStateOf(iniziale.toLocalTime().withSecond(0).withNano(0)) }
    var menu by remember { mutableStateOf(false) }
    var sceltaData by remember { mutableStateOf(false) }
    var sceltaOra by remember { mutableStateOf(false) }
    var errore by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = chiudi,
        title = { Text(if (esistente == null) "Nuova voce" else "Modifica voce") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                    OutlinedTextField(tipo.nome, {}, readOnly = true, label = { Text("Tipo") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                    ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        TipoDiario.entries.forEach { t -> DropdownMenuItem(text = { Text(t.nome) }, onClick = { tipo = t; menu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { sceltaData = true }) { Text(Tempo.etichetta(giorno)) }
                    OutlinedButton(onClick = { sceltaOra = true }) { Text("%02d:%02d".format(ora.hour, ora.minute)) }
                }
                if (tipo.unita != null) {
                    OutlinedTextField(valore, { valore = it }, label = { Text(if (tipo == TipoDiario.PRESSIONE) "Sistolica (mmHg)" else "Valore (${tipo.unita})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    if (tipo == TipoDiario.PRESSIONE) OutlinedTextField(valore2, { valore2 = it }, label = { Text("Diastolica (mmHg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                OutlinedTextField(testo, { testo = it }, label = { Text(if (tipo.unita == null) "Descrizione" else "Nota (facoltativa)") }, minLines = 2)
                if (tipo == TipoDiario.FARMACO) Text("Solo diario personale: l'app non dà indicazioni su farmaci o dosi.", style = MaterialTheme.typography.bodySmall)
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = valore.takeIf { it.isNotBlank() }?.let { Numeri.leggi(it) }
                val v2 = valore2.takeIf { it.isNotBlank() }?.let { Numeri.leggi(it) }
                errore = when {
                    valore.isNotBlank() && v == null -> "Il valore non è un numero."
                    tipo in setOf(TipoDiario.UMORE, TipoDiario.ENERGIA, TipoDiario.RIPOSO_PERCEPITO) && v != null && v !in 1.0..5.0 -> "Usa una scala da 1 a 5."
                    tipo == TipoDiario.PRESSIONE && (v == null || v2 == null) -> "Servono sistolica e diastolica."
                    tipo.unita != null && v == null && testo.isBlank() -> "Inserisci un valore o una nota."
                    tipo.unita == null && testo.isBlank() -> "Scrivi una descrizione."
                    else -> null
                }
                if (errore == null) {
                    val istante = giorno.atTime(ora).atZone(zona).toInstant().toEpochMilli()
                    val ora2 = System.currentTimeMillis()
                    vm.salvaVoce(VoceDiario(esistente?.id ?: 0, tipo.codice, istante, v, v2, testo.trim(), esistente?.creataIl ?: ora2, ora2))
                    chiudi()
                }
            }) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (esistente != null) TextButton(onClick = { vm.eliminaVoce(esistente.id); chiudi() }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = chiudi) { Text("Annulla") }
            }
        },
    )
    if (sceltaData) {
        val s = rememberDatePickerState(initialSelectedDateMillis = giorno.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { sceltaData = false }, confirmButton = {
            TextButton(onClick = { s.selectedDateMillis?.let { giorno = Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate() }; sceltaData = false }) { Text("OK") }
        }) { DatePicker(s) }
    }
    if (sceltaOra) {
        val s = rememberTimePickerState(ora.hour, ora.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { sceltaOra = false }, confirmButton = { TextButton(onClick = { ora = LocalTime.of(s.hour, s.minute); sceltaOra = false }) { Text("OK") } },
            text = { TimePicker(s) })
    }
}
