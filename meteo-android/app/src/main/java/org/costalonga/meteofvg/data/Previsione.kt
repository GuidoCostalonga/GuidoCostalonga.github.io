package org.costalonga.meteofvg.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Il fuso con cui Open-Meteo consegna gli orari: quello di casa. */
val FUSO: ZoneId = ZoneId.of("Europe/Rome")

data class Ora(
    val quando: LocalDateTime,
    val temperatura: Double,
    val percepita: Double,
    val pioggia: Int,
    val vento: Double,
    val codice: Int,
    val diGiorno: Boolean,
) {
    val giorno: LocalDate get() = quando.toLocalDate()
    val condizione: Condizione get() = condizioneDi(codice, diGiorno)
}

data class Giorno(
    val data: LocalDate,
    val codice: Int,
    val minima: Double,
    val massima: Double,
    val pioggia: Int,
    val ventoMax: Double,
) {
    val condizione: Condizione get() = condizioneDi(codice, diGiorno = true)
}

/** Una riga del confronto fra i modelli. */
data class Confronto(val modello: String, val esito: String)

/** Una delle tre indicazioni pratiche del riquadro "Da sapere". */
data class Consiglio(val titolo: String, val testo: String, val rilievo: Boolean)

data class Previsione(
    val comune: Comune,
    val temperatura: Double,
    val percepita: Double,
    val umidita: Int,
    val vento: Double,
    val codice: Int,
    val diGiorno: Boolean,
    val ore: List<Ora>,
    val giorni: List<Giorno>,
    val attendibilita: Int,
    val confronti: List<Confronto>,
    val consigli: List<Consiglio>,
    val aggiornato: Long,
) {
    val condizione: Condizione get() = condizioneDi(codice, diGiorno)

    /** Giudizio sull'attendibilita', con le soglie della pagina. */
    val giudizio: String
        get() = when {
            attendibilita >= 85 -> "Alta"
            attendibilita >= 70 -> "Buona"
            else -> "Moderata"
        }

    val accordo: String
        get() = when {
            attendibilita >= 85 ->
                "I modelli concordano nettamente: scenario stabile nelle prossime 24 ore."
            attendibilita >= 70 ->
                "Buona coerenza fra i modelli, con qualche differenza locale."
            else ->
                "I modelli divergono: controlla più spesso gli aggiornamenti."
        }

    /** Le ore da mostrare: dalla prima non ancora passata, diciotto in tutto. */
    fun oreDaAdesso(quante: Int = 18): List<Ora> {
        val adesso = LocalDateTime.now(FUSO)
        val da = ore.indexOfFirst { !it.quando.isBefore(adesso) }.coerceAtLeast(0)
        return ore.drop(da).take(quante)
    }

    fun oreDel(giorno: LocalDate): List<Ora> = ore.filter { it.giorno == giorno }
}

/** Numeri di un vettore JSON, con i buchi segnalati come null. */
private fun JSONArray.numeri(): List<Double?> =
    (0 until length()).map { if (isNull(it)) null else optDouble(it) }
        .map { if (it == null || it.isNaN()) null else it }

private fun JSONArray.interi(): List<Int?> =
    (0 until length()).map { if (isNull(it)) null else optInt(it) }

private fun JSONArray.testi(): List<String> = (0 until length()).map { optString(it) }

object Lettura {

    /**
     * Costruisce la previsione dalla risposta del modello di riferimento e da
     * quelle dei tre modelli confrontati, che possono anche mancare.
     */
    fun previsione(
        base: JSONObject,
        modelli: List<JSONObject?>,
        comune: Comune,
    ): Previsione {
        val corrente = base.getJSONObject("current")
        val orarie = base.getJSONObject("hourly")
        val giornaliere = base.getJSONObject("daily")

        val istanti = orarie.getJSONArray("time").testi()
        val temp = orarie.getJSONArray("temperature_2m").numeri()
        val perc = orarie.getJSONArray("apparent_temperature").numeri()
        val pio = orarie.getJSONArray("precipitation_probability").interi()
        val vento = orarie.getJSONArray("wind_speed_10m").numeri()
        val codici = orarie.getJSONArray("weather_code").interi()
        val giornoNotte = orarie.getJSONArray("is_day").interi()

        val ore = istanti.indices.mapNotNull { i ->
            val t = temp.getOrNull(i) ?: return@mapNotNull null
            Ora(
                quando = LocalDateTime.parse(istanti[i]),
                temperatura = t,
                percepita = perc.getOrNull(i) ?: t,
                pioggia = pio.getOrNull(i) ?: 0,
                vento = vento.getOrNull(i) ?: 0.0,
                codice = codici.getOrNull(i) ?: -1,
                diGiorno = (giornoNotte.getOrNull(i) ?: 1) == 1,
            )
        }

        val giorni = leggiGiorni(giornaliere)

        return Previsione(
            comune = comune,
            temperatura = corrente.getDouble("temperature_2m"),
            percepita = corrente.optDouble("apparent_temperature", corrente.getDouble("temperature_2m")),
            umidita = corrente.optInt("relative_humidity_2m", 0),
            vento = corrente.optDouble("wind_speed_10m", 0.0),
            codice = corrente.optInt("weather_code", -1),
            diGiorno = corrente.optInt("is_day", 1) == 1,
            ore = ore,
            giorni = giorni,
            attendibilita = attendibilita(modelli),
            confronti = confronti(modelli),
            consigli = consigli(ore, corrente.getDouble("temperature_2m")),
            aggiornato = System.currentTimeMillis(),
        )
    }

    fun leggiGiorni(giornaliere: JSONObject): List<Giorno> {
        val date = giornaliere.getJSONArray("time").testi()
        val codici = giornaliere.getJSONArray("weather_code").interi()
        val minime = giornaliere.getJSONArray("temperature_2m_min").numeri()
        val massime = giornaliere.getJSONArray("temperature_2m_max").numeri()
        val pioggia = giornaliere.getJSONArray("precipitation_probability_max").interi()
        val vento = giornaliere.getJSONArray("wind_speed_10m_max").numeri()
        return date.indices.map { i ->
            Giorno(
                data = LocalDate.parse(date[i]),
                codice = codici.getOrNull(i) ?: -1,
                minima = minime.getOrNull(i) ?: 0.0,
                massima = massime.getOrNull(i) ?: 0.0,
                pioggia = pioggia.getOrNull(i) ?: 0,
                ventoMax = vento.getOrNull(i) ?: 0.0,
            )
        }
    }

    /**
     * Attendibilita' a 24 ore, con lo stesso calcolo della pagina: media della
     * distanza fra la temperatura piu' alta e quella piu' bassa previste dai
     * modelli, ora per ora. Piu' i modelli concordano, piu' il punteggio sale.
     * Risultato fra 45 e 96; con meno di due modelli disponibili resta 70.
     */
    fun attendibilita(modelli: List<JSONObject?>): Int {
        val serie = modelli.filterNotNull().mapNotNull { m ->
            runCatching {
                m.getJSONObject("hourly").getJSONArray("temperature_2m").numeri().take(24)
            }.getOrNull()
        }
        if (serie.size < 2) return 70
        var somma = 0.0
        for (h in 0 until 24) {
            val valori = serie.mapNotNull { it.getOrNull(h) }
            if (valori.size >= 2) somma += valori.max() - valori.min()
        }
        val media = somma / 24.0
        return (96.0 - media * 12.0).roundToInt().coerceIn(45, 96)
    }

    /** Che cosa dice ciascun modello fra sei ore. */
    fun confronti(modelli: List<JSONObject?>): List<Confronto> =
        MeteoApi.NOMI_MODELLI.mapIndexed { i, nome ->
            val valore = runCatching {
                modelli.getOrNull(i)
                    ?.getJSONObject("hourly")
                    ?.getJSONArray("temperature_2m")
                    ?.numeri()
                    ?.getOrNull(6)
            }.getOrNull()
            Confronto(nome, if (valore == null) "non disponibile" else "${valore.roundToInt()}° fra 6 ore")
        }

    /**
     * Le tre indicazioni pratiche, con le soglie della pagina: pioggia oltre
     * il 55 per cento nelle dodici ore, vento oltre 35 chilometri orari nelle
     * ventiquattro, caldo oltre 27 gradi adesso.
     */
    fun consigli(ore: List<Ora>, adesso: Double): List<Consiglio> {
        val da = ore.indexOfFirst { !it.quando.isBefore(LocalDateTime.now(FUSO)) }.coerceAtLeast(0)
        val prossime12 = ore.drop(da).take(12)
        val prossime24 = ore.drop(da).take(24)
        val pioggia = prossime12.maxOfOrNull { it.pioggia } ?: 0
        val vento = prossime24.maxOfOrNull { it.vento } ?: 0.0

        val suPioggia = if (pioggia > 55) {
            Consiglio(
                "Ombrello consigliato",
                "Probabilità di pioggia fino al $pioggia per cento nelle prossime 12 ore.",
                rilievo = true,
            )
        } else {
            Consiglio(
                "Pioggia poco probabile",
                "Rischio massimo $pioggia per cento nelle prossime 12 ore.",
                rilievo = false,
            )
        }

        val suVento = if (vento > 35) {
            Consiglio(
                "Attenzione al vento",
                "Raffiche o vento sostenuto fino a circa ${vento.roundToInt()} chilometri orari.",
                rilievo = true,
            )
        } else {
            Consiglio(
                "Vento contenuto",
                "Velocità massima prevista circa ${vento.roundToInt()} chilometri orari.",
                rilievo = false,
            )
        }

        val suTemperatura = if (adesso > 27) {
            Consiglio(
                "Sole e caldo",
                "Nelle ore centrali preferisci ombra e acqua.",
                rilievo = true,
            )
        } else {
            Consiglio(
                "Temperatura",
                "Condizioni generalmente gestibili nelle prossime ore.",
                rilievo = false,
            )
        }

        return listOf(suPioggia, suVento, suTemperatura)
    }
}

/** Arrotondamento ai gradi interi, come sulla pagina. */
fun Double.gradi(): String = "${this.roundToInt()}°"

/** Serve a non scrivere "-0°" quando la temperatura sfiora lo zero. */
fun Double.gradiPuliti(): String {
    val v = this.roundToInt()
    return if (v == 0 && abs(this) < 0.5) "0°" else "$v°"
}
