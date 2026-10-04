package org.costalonga.sportintv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import org.costalonga.sportintv.promemoria.Pianificatore
import org.costalonga.sportintv.ui.Modello
import org.costalonga.sportintv.ui.SchermataDettaglio
import org.costalonga.sportintv.ui.SchermataEventi
import org.costalonga.sportintv.ui.SchermataImpostazioni
import org.costalonga.sportintv.ui.SchermataInformazioni
import org.costalonga.sportintv.ui.SchermataPreferiti
import org.costalonga.sportintv.ui.TemaSport
import org.costalonga.sportintv.ui.Vista

private data class Sezione(val rotta: String, val etichetta: String, val icona: ImageVector)

@Suppress("DEPRECATION")
private val sezioni = listOf(
    Sezione("programma", "Programma", Icons.Filled.EventAvailable),
    Sezione("da-confermare", "Da confermare", Icons.Filled.HelpOutline),
    Sezione("preferiti", "Preferiti", Icons.Filled.Star),
    Sezione("impostazioni", "Impostazioni", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    private val modello: Modello by viewModels()

    /** Evento da aprire quando si tocca la notifica di un promemoria. */
    private val daAprire = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        daAprire.value = intent?.getStringExtra(Pianificatore.EXTRA_ID)
        setContent {
            val pref by modello.preferenze.collectAsState()
            TemaSport(pref.tema, pref.coloriDinamici) { App(modello, daAprire) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Pianificatore.EXTRA_ID)?.let { daAprire.value = it }
    }
}

@Composable
private fun App(modello: Modello, daAprire: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val messaggio by modello.messaggio.collectAsState()
    val apri by daAprire.collectAsState()

    LaunchedEffect(messaggio) {
        messaggio?.let {
            snackbar.showSnackbar(it)
            modello.messaggio.value = null
        }
    }
    LaunchedEffect(apri) {
        apri?.let {
            nav.navigate("evento/$it")
            daAprire.value = null
        }
    }

    val voce by nav.currentBackStackEntryAsState()
    val rottaAttuale = voce?.destination?.route
    Scaffold(
        // Il margine superiore lo gestisce ogni schermata (barra del titolo): qui solo quello inferiore.
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (sezioni.any { it.rotta == rottaAttuale }) {
                // Con i caratteri molto ingranditi l'etichetta resta solo sulla voce scelta.
                val grandi = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                NavigationBar {
                    for (s in sezioni) {
                        NavigationBarItem(
                            alwaysShowLabel = !grandi,
                            selected = rottaAttuale == s.rotta,
                            onClick = { vaiA(nav, s.rotta) },
                            icon = { Icon(s.icona, contentDescription = if (grandi && rottaAttuale != s.rotta) s.etichetta else null) },
                            label = { Text(s.etichetta, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                        )
                    }
                }
            }
        },
    ) { margini ->
        NavHost(nav, startDestination = "programma", modifier = Modifier.padding(margini)) {
            composable("programma") { SchermataEventi(modello, Vista.CONFERMATI) { nav.navigate("evento/$it") } }
            composable("da-confermare") { SchermataEventi(modello, Vista.DA_CONFERMARE) { nav.navigate("evento/$it") } }
            composable("preferiti") { SchermataPreferiti(modello) { nav.navigate("evento/$it") } }
            composable("impostazioni") { SchermataImpostazioni(modello) { nav.navigate("informazioni") } }
            composable("informazioni") { SchermataInformazioni(modello) { nav.popBackStack() } }
            composable("evento/{id}") { voce ->
                SchermataDettaglio(modello, voce.arguments?.getString("id").orEmpty()) { nav.popBackStack() }
            }
        }
    }
}

private fun vaiA(nav: NavHostController, rotta: String) {
    nav.navigate(rotta) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
