package org.costalonga.meteofvg.allerte

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Legge il riquadro della Regione senza mostrarlo, per il controllo in
 * sottofondo. E' la stessa pagina del riquadro a schermo: cambia solo che
 * nessuno la guarda.
 *
 * Va chiamata dal filo principale, perche' la WebView vive li'.
 */
@SuppressLint("SetJavaScriptEnabled")
suspend fun leggiAllerta(
    contesto: Context,
    istat: String,
    attesaMs: Long = 20_000,
): EsitoAllerte = suspendCancellableCoroutine { continuazione ->

    val mano = Handler(Looper.getMainLooper())
    var vista: WebView? = WebView(contesto.applicationContext)
    var concluso = false

    // La WebView va smontata sempre: altrimenti il timer del JavaScript
    // continuerebbe a girare per conto suo.
    fun chiudi(esito: EsitoAllerte) {
        if (concluso) return
        concluso = true
        vista?.let { v ->
            v.stopLoading()
            v.removeJavascriptInterface("Ponte")
            v.destroy()
        }
        vista = null
        if (continuazione.isActive) continuazione.resume(esito)
    }

    val scadenza = Runnable {
        // Silenzio dopo l'attesa: non raggiungibile, che non vuol dire
        // "nessuna allerta".
        chiudi(EsitoAllerte(StatoAllerte.NON_RAGGIUNGIBILE))
    }

    continuazione.invokeOnCancellation {
        mano.post {
            mano.removeCallbacks(scadenza)
            chiudi(EsitoAllerte(StatoAllerte.NON_RAGGIUNGIBILE))
        }
    }

    vista?.apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        settings.setSupportMultipleWindows(false)
        addJavascriptInterface(
            PonteAllerte { stato, _, testo ->
                if (stato != StatoAllerte.ATTESA) {
                    mano.removeCallbacks(scadenza)
                    chiudi(EsitoAllerte(stato, testo))
                }
            },
            "Ponte",
        )
        loadDataWithBaseURL(
            INDIRIZZO_REGIONE,
            paginaAllerte(istat),
            "text/html",
            "utf-8",
            null,
        )
    }

    mano.postDelayed(scadenza, attesaMs)
}
