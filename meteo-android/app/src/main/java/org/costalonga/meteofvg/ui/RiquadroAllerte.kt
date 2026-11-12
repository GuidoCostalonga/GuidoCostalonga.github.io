package org.costalonga.meteofvg.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as ColorAndroid
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.costalonga.meteofvg.R
import org.costalonga.meteofvg.allerte.INDIRIZZO_REGIONE
import org.costalonga.meteofvg.allerte.PonteAllerte
import org.costalonga.meteofvg.allerte.StatoAllerte
import org.costalonga.meteofvg.allerte.paginaAllerte
import org.costalonga.meteofvg.data.Comune
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RiquadroAllerte(comune: Comune, modifier: Modifier = Modifier) {
    var stato by remember(comune.istat) { mutableStateOf(StatoAllerte.ATTESA) }
    var altezza by remember(comune.istat) { mutableIntStateOf(1) }
    val verificatoAlle = remember(comune.istat, stato) {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Il riquadro della Regione resta montato anche mentre si misura: e'
        // lui a dire se l'avviso c'e'. Quando non c'e', occupa un filo.
        AndroidView(
            factory = { contesto ->
                WebView(contesto).apply {
                    setBackgroundColor(ColorAndroid.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.setSupportMultipleWindows(false)
                    addJavascriptInterface(
                        PonteAllerte { nuovo, nuovaAltezza, _ ->
                            stato = nuovo
                            if (nuovo == StatoAllerte.PIENO && nuovaAltezza > 20) {
                                altezza = nuovaAltezza.coerceAtMost(1200)
                            }
                        },
                        "Ponte",
                    )
                    webViewClient = object : WebViewClient() {
                        // I collegamenti dell'avviso portano al sito della
                        // Regione: si aprono nel browser, non qui dentro.
                        override fun shouldOverrideUrlLoading(
                            vista: WebView,
                            richiesta: WebResourceRequest,
                        ): Boolean {
                            val intento = Intent(Intent.ACTION_VIEW, richiesta.url).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { contesto.startActivity(intento) }
                            return true
                        }
                    }
                }
            },
            update = { vista ->
                val chiave = vista.getTag(R.id.istat_caricato) as? String
                if (chiave != comune.istat) {
                    vista.setTag(R.id.istat_caricato, comune.istat)
                    vista.loadDataWithBaseURL(
                        INDIRIZZO_REGIONE,
                        paginaAllerte(comune.istat),
                        "text/html",
                        "utf-8",
                        null,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(if (stato == StatoAllerte.PIENO) altezza.dp else 1.dp),
        )

        when (stato) {
            StatoAllerte.PIENO -> Unit

            StatoAllerte.ATTESA -> Text(
                text = "Lettura del riquadro della Protezione Civile regionale…",
                style = MaterialTheme.typography.bodyMedium,
                color = Testo3,
                modifier = Modifier.padding(top = 4.dp),
            )

            StatoAllerte.VUOTO -> {
                Text(
                    text = "Nessun avviso in corso",
                    style = MaterialTheme.typography.titleSmall,
                    color = Ok,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "La Protezione Civile regionale non sta pubblicando allerte per " +
                        "${comune.nome}. Verificato alle $verificatoAlle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Testo2,
                )
            }

            StatoAllerte.NON_RAGGIUNGIBILE -> {
                Text(
                    text = "Riquadro non raggiungibile",
                    style = MaterialTheme.typography.titleSmall,
                    color = Pericolo,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Non è stato possibile caricare il riquadro della Protezione Civile " +
                        "regionale. Questo non significa che non ci siano allerte: controlla " +
                        "direttamente il sito della Regione.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Testo2,
                )
            }
        }
    }
}
