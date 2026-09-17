package org.costalonga.meteofvg.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * L'interfaccia pubblica di Open-Meteo, la stessa che alimenta la pagina
 * costalonga.org/meteo/ : nessuna chiave di accesso, fuso Europe/Rome.
 */
object MeteoApi {

    private const val RADICE = "https://api.open-meteo.com/v1/forecast"

    private const val CORRENTI =
        "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"

    private const val ORARIE =
        "temperature_2m,apparent_temperature,relative_humidity_2m," +
            "precipitation_probability,weather_code,wind_speed_10m,is_day"

    private const val GIORNALIERE =
        "weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,wind_speed_10m_max"

    /** Il modello che Open-Meteo giudica migliore per il punto richiesto. */
    const val MODELLO_BASE = "best_match"

    /** I tre modelli confrontati per calcolare l'attendibilita'. */
    val MODELLI = listOf("icon_seamless", "ecmwf_ifs025", "meteofrance_seamless")

    /** Come si chiamano, per chi legge. */
    val NOMI_MODELLI = listOf("ICON-D2 · Deutscher Wetterdienst", "ECMWF IFS", "Météo-France")

    fun indirizzo(
        comune: Comune,
        modello: String = MODELLO_BASE,
        giorni: Int = 7,
        conOrarie: Boolean = true,
    ): String = buildString {
        append(RADICE)
        append("?latitude=").append(comune.lat)
        append("&longitude=").append(comune.lon)
        append("&current=").append(CORRENTI)
        if (conOrarie) append("&hourly=").append(ORARIE)
        append("&daily=").append(GIORNALIERE)
        append("&timezone=Europe%2FRome")
        append("&forecast_days=").append(giorni)
        append("&models=").append(modello)
    }

    /** Scarica e legge la risposta. Solleva [IOException] se non arriva. */
    @Throws(IOException::class)
    fun scarica(indirizzo: String, attesaMs: Int = 12_000): JSONObject {
        val collegamento = (URL(indirizzo).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = attesaMs
            readTimeout = attesaMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MeteoFVG/1.0 (costalonga.org)")
        }
        try {
            val codice = collegamento.responseCode
            if (codice !in 200..299) throw IOException("Open-Meteo ha risposto $codice")
            val testo = collegamento.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(testo)
        } finally {
            collegamento.disconnect()
        }
    }
}
