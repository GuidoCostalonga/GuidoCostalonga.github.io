package org.costalonga.fattidelgiorno.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import org.costalonga.fattidelgiorno.FattiApp

class MainActivity : ComponentActivity() {

    private val vista: VistaModello by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                VistaModello(application as FattiApp) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaFattiDelGiorno {
                var impostazioniAperte by remember { mutableStateOf(false) }
                if (impostazioniAperte) {
                    SchermataImpostazioni(
                        app = application as FattiApp,
                        chiudi = {
                            impostazioniAperte = false
                            vista.aggiornaStatoGemini()
                        },
                    )
                } else {
                    SchermataPrincipale(vista = vista, apriImpostazioni = { impostazioniAperte = true })
                }
            }
        }
    }
}
