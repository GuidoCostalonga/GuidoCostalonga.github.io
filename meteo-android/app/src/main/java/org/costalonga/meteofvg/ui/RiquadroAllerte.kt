package org.costalonga.meteofvg.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as ColorAndroid
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
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
import org.costalonga.meteofvg.data.Comune
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * I tre casi che il riquadro della Protezione Civile puo' presentare.
 * Il terzo non viene mai spacciato per assenza di allerta.
 */
enum class StatoAllerte { ATTESA, PIENO, VUOTO, NON_RAGGIUNGIBILE }

/**
 * Il ponte fra il riquadro della Regione e l'app: il JavaScript della pagina
 * dice se l'avviso c'e', se non c'e' o se il riquadro non si carica, e quanto
 * spazio occupa. I nomi dei metodi non vanno cambiati: li chiama la pagina.
 */
class PonteAllerte(private val esito: (StatoAllerte, Int) -> Unit) {

    private val mano = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun stato(stato: String, altezza: Int) {
        val tradotto = when (stato) {
            "pieno" -> StatoAllerte.PIENO
            "vuoto" -> StatoAllerte.VUOTO
            else -> StatoAllerte.NON_RAGGIUNGIBILE
        }
        mano.post { esito(tradotto, altezza) }
    }
}

private const val INDIRIZZO_REGIONE = "https://www.protezionecivile.fvg.it/"
private const val SCRIPT_REGIONE = "https://www.protezionecivile.fvg.it/widgets/pcrfvgit_alert.js"

/**
 * La pagina che ospita il riquadro ufficiale.
 *
 * L'ordine e' quello della pagina costalonga.org/meteo/ : prima lo script
 * della Regione, poi il contenitore con il codice ISTAT del Comune. Il
 * controllo ogni mezzo secondo distingue i tre casi.
 */
private fun paginaAllerte(istat: String): String = """
<!doctype html>
<html lang="it">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
  html,body{margin:0;padding:0;background:transparent;color:#1f1c17;
    font:15px/1.55 sans-serif;-webkit-text-size-adjust:100%}
  #misura{overflow:hidden}
  a{color:#1a6098}
  img{max-width:100%;height:auto}
</style>
<script>var caricato = null, giri = 0;</script>
<script src="$SCRIPT_REGIONE" onload="caricato=true" onerror="caricato=false"></script>
</head>
<body>
<div id="misura"><div class="pcrfvgit_alert_widget" data-istatcode="$istat"></div></div>
<script>
  function misura(){
    var m = document.getElementById('misura');
    return m ? Math.ceil(m.getBoundingClientRect().height) : 0;
  }
  function guarda(){
    giri++;
    var w = document.querySelector('.pcrfvgit_alert_widget');
    if (w && (w.children.length || w.textContent.trim().length)) {
      Ponte.stato('pieno', misura()); return;
    }
    if (caricato === false) { Ponte.stato('ko', 0); return; }
    if (caricato === true && giri > 6) { Ponte.stato('vuoto', 0); return; }
    if (giri > 24) { Ponte.stato('ko', 0); }
  }
  setInterval(guarda, 500);
  guarda();
</script>
</body>
</html>
"""

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
                        PonteAllerte { nuovo, nuovaAltezza ->
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
