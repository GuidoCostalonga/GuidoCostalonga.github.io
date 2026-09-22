package org.costalonga.meteofvg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import org.costalonga.meteofvg.ui.SchermataComuni
import org.costalonga.meteofvg.ui.SchermataMeteo
import org.costalonga.meteofvg.ui.TemaMeteoFVG

class MainActivity : ComponentActivity() {

    private var modello: MeteoViewModel? = null

    override fun onCreate(statoSalvato: Bundle?) {
        super.onCreate(statoSalvato)
        setContent {
            val vista: MeteoViewModel = viewModel()
            modello = vista

            // Collegamento del tipo costalonga.org/meteo/#roveredo-in-piano.
            val slug = remember { intent?.data?.fragment }
            LaunchedEffect(slug) { vista.scegliDaSlug(slug) }

            var elencoAperto by remember { mutableStateOf(false) }

            TemaMeteoFVG {
                if (elencoAperto) {
                    SchermataComuni(
                        scelto = vista.comune,
                        onScelto = {
                            vista.scegli(it)
                            elencoAperto = false
                        },
                        onChiudi = { elencoAperto = false },
                    )
                } else {
                    SchermataMeteo(
                        comune = vista.comune,
                        previsione = vista.previsione,
                        caricamento = vista.caricamento,
                        errore = vista.errore,
                        giornoScelto = vista.giornoScelto,
                        onGiornoScelto = vista::giorno,
                        onComune = vista::scegli,
                        onApriElenco = { elencoAperto = true },
                        onRicarica = vista::carica,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        modello?.let {
            it.inPrimoPiano = true
            it.aggiornaSeVecchia()
        }
    }

    override fun onPause() {
        super.onPause()
        modello?.inPrimoPiano = false
    }
}
