package org.costalonga.meteofvg.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/** Le chiamate a Open-Meteo, raccolte in un punto solo. */
object MeteoRete {

    /**
     * La previsione completa: il modello di riferimento piu' i tre modelli del
     * confronto, chiesti insieme. Se un modello del confronto non risponde, il
     * resto va avanti: manca solo la sua riga.
     */
    suspend fun previsione(comune: Comune): Previsione = withContext(Dispatchers.IO) {
        coroutineScope {
            val riferimento = async { MeteoApi.scarica(MeteoApi.indirizzo(comune)) }
            val confronto = MeteoApi.MODELLI.map { modello ->
                async {
                    runCatching { MeteoApi.scarica(MeteoApi.indirizzo(comune, modello)) }.getOrNull()
                }
            }
            Lettura.previsione(riferimento.await(), confronto.awaitAll(), comune)
        }
    }

    /**
     * Solo quel che serve al widget, in una chiamata sola e senza coroutine:
     * il widget lavora dentro un ricevitore, dove il tempo e' contato.
     */
    @Throws(IOException::class)
    fun istantanea(comune: Comune, attesaMs: Int = 6_000): Istantanea {
        val risposta = MeteoApi.scarica(
            MeteoApi.indirizzo(comune, giorni = 1, conOrarie = false),
            attesaMs,
        )
        val corrente = risposta.getJSONObject("current")
        val oggi = Lettura.leggiGiorni(risposta.getJSONObject("daily")).firstOrNull()
        return Istantanea(
            comune = comune,
            temperatura = corrente.getDouble("temperature_2m"),
            codice = corrente.optInt("weather_code", -1),
            diGiorno = corrente.optInt("is_day", 1) == 1,
            minima = oggi?.minima ?: 0.0,
            massima = oggi?.massima ?: 0.0,
            pioggia = oggi?.pioggia ?: 0,
            quando = System.currentTimeMillis(),
        )
    }
}
