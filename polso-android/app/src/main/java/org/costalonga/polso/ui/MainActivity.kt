package org.costalonga.polso.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.costalonga.polso.motore.analisi.Sezione
import org.costalonga.polso.ui.componenti.TemaPolso
import org.costalonga.polso.ui.schermate.AssistenteSchermata
import org.costalonga.polso.ui.schermate.CatalogoSchermata
import org.costalonga.polso.ui.schermate.DettaglioAnalisi
import org.costalonga.polso.ui.schermate.DiarioSchermata
import org.costalonga.polso.ui.schermate.EsportaSchermata
import org.costalonga.polso.ui.schermate.FontiSchermata
import org.costalonga.polso.ui.schermate.ImpostazioniSchermata
import org.costalonga.polso.ui.schermate.Panoramica
import org.costalonga.polso.ui.schermate.PrimoAvvio
import org.costalonga.polso.ui.schermate.SezioneSchermata

/** Voci del menu: le sezioni richieste più le pagine di servizio. */
enum class Pagina(val titolo: String, val icona: ImageVector, val sezione: Sezione? = null) {
    PANORAMICA("Panoramica", Icons.Default.Dashboard),
    ATTIVITA("Attività", Icons.AutoMirrored.Filled.DirectionsRun, Sezione.ATTIVITA),
    CUORE("Cuore", Icons.Default.Favorite, Sezione.CUORE),
    SONNO("Sonno", Icons.Default.Bedtime, Sezione.SONNO),
    ALLENAMENTI("Allenamenti", Icons.Default.FitnessCenter, Sezione.ALLENAMENTI),
    ALTRI("Altri parametri", Icons.Default.Monitor, Sezione.ALTRI),
    RELAZIONI("Relazioni", Icons.Default.Hub, Sezione.RELAZIONI),
    INDICI("Indici e qualità", Icons.Default.Speed, Sezione.INDICI),
    DIARIO("Diario", Icons.AutoMirrored.Filled.MenuBook),
    ASSISTENTE("Assistente", Icons.Default.AutoAwesome),
    CATALOGO("Catalogo delle analisi", Icons.Default.Checklist),
    FONTI("Fonti dati e importazione", Icons.Default.Sync),
    ESPORTA("Esporta e backup", Icons.Default.Save),
    IMPOSTAZIONI("Impostazioni", Icons.Default.Settings),
}

class MainActivity : AppCompatActivity() {
    private val vm: AppViewModel by viewModels()
    private val sbloccato = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val imp by vm.impostazioni.collectAsState()
            TemaPolso(imp.tema) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        imp.bloccoBiometrico && !sbloccato.value -> Blocco { chiediSblocco() }
                        !imp.primoAvvioFatto -> PrimoAvvio(vm)
                        else -> App(vm)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.impostazioni.value.bloccoBiometrico && !sbloccato.value) chiediSblocco()
        vm.aggiornaStatoHc()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) sbloccato.value = false
    }

    private fun chiediSblocco() {
        val autenticatori = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(autenticatori) != BiometricManager.BIOMETRIC_SUCCESS) { sbloccato.value = true; return }
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { sbloccato.value = true }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Sblocca Polso").setSubtitle("I tuoi dati sanitari sono protetti").setAllowedAuthenticators(autenticatori).build())
    }
}

@Composable
private fun Blocco(onSblocca: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, null)
        Spacer(Modifier.height(12.dp))
        Text("Polso è bloccato", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSblocca) { Text("Sblocca") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel) {
    var pagina by rememberSaveable { mutableStateOf(Pagina.PANORAMICA) }
    var dettaglio by rememberSaveable { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snack = androidx.compose.runtime.remember { SnackbarHostState() }
    val messaggio by vm.messaggio.collectAsState()
    val caricamento by vm.caricamento.collectAsState()
    val imp by vm.impostazioni.collectAsState()
    val consenso by vm.consenso.collectAsState()

    LaunchedEffect(messaggio) { messaggio?.let { snack.showSnackbar(it); vm.messaggio.value = null } }
    BackHandler(enabled = dettaglio != null || pagina != Pagina.PANORAMICA) { if (dettaglio != null) dettaglio = null else pagina = Pagina.PANORAMICA }

    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Polso", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                Pagina.entries.forEach { p ->
                    NavigationDrawerItem(icon = { Icon(p.icona, null) }, label = { Text(p.titolo) }, selected = p == pagina,
                        onClick = { pagina = p; dettaglio = null; scope.launch { drawer.close() } }, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (dettaglio != null) "Dettaglio" else pagina.titolo) },
                    navigationIcon = {
                        if (dettaglio != null) IconButton(onClick = { dettaglio = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                        else IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") }
                    },
                    actions = { IconButton(onClick = { vm.sincronizza() }) { Icon(Icons.Default.Sync, "Sincronizza ora") } },
                )
            },
            snackbarHost = { SnackbarHost(snack) },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                if (imp.modalitaDemo) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text("MODALITÀ DIMOSTRATIVA · dati sintetici, non tuoi. Disattivala in Impostazioni.", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
                if (caricamento) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) {
                    val apri: (String) -> Unit = { dettaglio = it }
                    when {
                        dettaglio != null -> DettaglioAnalisi(vm, dettaglio!!)
                        pagina == Pagina.PANORAMICA -> Panoramica(vm, apri) { pagina = it }
                        pagina.sezione != null -> SezioneSchermata(vm, pagina.sezione!!, apri)
                        pagina == Pagina.DIARIO -> DiarioSchermata(vm)
                        pagina == Pagina.ASSISTENTE -> AssistenteSchermata(vm)
                        pagina == Pagina.CATALOGO -> CatalogoSchermata(vm, apri)
                        pagina == Pagina.FONTI -> FontiSchermata(vm)
                        pagina == Pagina.ESPORTA -> EsportaSchermata(vm)
                        pagina == Pagina.IMPOSTAZIONI -> ImpostazioniSchermata(vm)
                    }
                }
            }
        }
    }

    consenso?.let { c ->
        AlertDialog(
            onDismissRequest = { vm.consenso.value = null },
            title = { Text("Inviare questi dati online?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Destinatario: ${c.destinatario}. Il testo qui sotto, e solo questo, uscirà dal telefono. Con i piani gratuiti il fornitore può conservare e usare i contenuti secondo i propri termini.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(c.testoInviato, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { vm.consenso.value = null; c.esegui() }) { Text("Invia") } },
            dismissButton = { TextButton(onClick = { vm.consenso.value = null }) { Text("Non inviare") } },
        )
    }
}
