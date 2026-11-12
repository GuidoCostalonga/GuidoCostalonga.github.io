package org.costalonga.meteofvg.allerte

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * I tre casi che il riquadro della Protezione Civile puo' presentare, piu'
 * l'attesa iniziale. Il terzo non viene mai spacciato per assenza di allerta.
 */
enum class StatoAllerte { ATTESA, PIENO, VUOTO, NON_RAGGIUNGIBILE }

/** Quel che si legge dal riquadro della Regione in un dato momento. */
data class EsitoAllerte(val stato: StatoAllerte, val testo: String = "")

const val INDIRIZZO_REGIONE = "https://www.protezionecivile.fvg.it/"

private const val SCRIPT_REGIONE =
    "https://www.protezionecivile.fvg.it/widgets/pcrfvgit_alert.js"

/**
 * Il ponte fra il riquadro della Regione e l'app: il JavaScript della pagina
 * dice se l'avviso c'e', se non c'e' o se il riquadro non si carica, quanto
 * spazio occupa e che cosa c'e' scritto. I nomi dei metodi non vanno
 * cambiati: li chiama la pagina qui sotto.
 */
class PonteAllerte(private val esito: (StatoAllerte, Int, String) -> Unit) {

    private val mano = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun stato(stato: String, altezza: Int, testo: String) {
        val tradotto = when (stato) {
            "pieno" -> StatoAllerte.PIENO
            "vuoto" -> StatoAllerte.VUOTO
            else -> StatoAllerte.NON_RAGGIUNGIBILE
        }
        mano.post { esito(tradotto, altezza, testo) }
    }
}

/**
 * La pagina che ospita il riquadro ufficiale.
 *
 * L'ordine e' quello della pagina costalonga.org/meteo/ : prima lo script
 * della Regione, poi il contenitore con il codice ISTAT del Comune. Il
 * controllo ogni mezzo secondo distingue i tre casi. E' la stessa pagina sia
 * per il riquadro a schermo sia per il controllo in sottofondo, cosi' i due
 * non possono dire cose diverse.
 */
fun paginaAllerte(istat: String): String = """
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
  function scritto(w){
    return w ? w.textContent.replace(/\s+/g, ' ').trim() : '';
  }
  function guarda(){
    giri++;
    var w = document.querySelector('.pcrfvgit_alert_widget');
    if (w && (w.children.length || w.textContent.trim().length)) {
      Ponte.stato('pieno', misura(), scritto(w)); return;
    }
    if (caricato === false) { Ponte.stato('ko', 0, ''); return; }
    if (caricato === true && giri > 6) { Ponte.stato('vuoto', 0, ''); return; }
    if (giri > 24) { Ponte.stato('ko', 0, ''); }
  }
  setInterval(guarda, 500);
  guarda();
</script>
</body>
</html>
"""
