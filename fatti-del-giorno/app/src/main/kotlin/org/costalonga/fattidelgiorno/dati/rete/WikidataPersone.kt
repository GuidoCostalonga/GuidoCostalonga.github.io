package org.costalonga.fattidelgiorno.dati.rete

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Avviso
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Classificatore
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.dominio.Date
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.Fonte
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica
import org.costalonga.fattidelgiorno.dominio.TipoAvviso
import java.net.URLEncoder

/**
 * Persone nate o scomparse nel giorno scelto, dal servizio di interrogazione
 * di Wikidata (https://query.wikidata.org/sparql, dati CC0, senza chiave).
 * Si chiedono solo le date con precisione al giorno: chi ha in archivio il
 * solo anno di nascita non viene spacciato per nato il 1 gennaio.
 */
class WikidataPersone(
    private val client: ClientHttp,
    private val ambito: Ambito,
    private val oggiIso: () -> String = { Date.giornoConsultazione() },
    /** Indirizzo del servizio: nelle prove punta a un servizio finto. */
    private val endpoint: String = ENDPOINT,
) : FonteFatti {

    enum class Ambito(val etichetta: String) {
        NATI_FVG("nati in Friuli Venezia Giulia"),
        MORTI_FVG("scomparsi in Friuli Venezia Giulia"),
        NATI_ITALIA("nati con cittadinanza italiana"),
    }

    override val nome: String = "Wikidata (${ambito.etichetta})"

    override suspend fun cerca(giorno: Int, mese: Int): EsitoFonte = withContext(Dispatchers.IO) {
        val url = endpoint + "?format=json&query=" + URLEncoder.encode(query(giorno, mese), "UTF-8")
        when (val esito = client.scarica(url, mapOf("Accept" to "application/sparql-results+json"))) {
            is EsitoHttp.Ok -> runCatching { EsitoFonte(interpreta(esito.corpo, giorno, mese)) }
                .getOrElse { EsitoFonte(emptyList(), Avviso(nome, TipoAvviso.ERRORE, "Risposta non leggibile: ${it.message}")) }
            else -> EsitoFonte(emptyList(), avvisoDa(nome, esito))
        }
    }

    /** L'interrogazione SPARQL, pubblica per le prove e per chi vuole leggerla. */
    fun query(giorno: Int, mese: Int): String {
        val proprietaData = if (ambito == Ambito.MORTI_FVG) "P570" else "P569"
        val luogo = when (ambito) {
            Ambito.NATI_FVG -> "?p wdt:P19 ?luogo . ?luogo wdt:P131* wd:Q1250 ."
            Ambito.MORTI_FVG -> "?p wdt:P20 ?luogo . ?luogo wdt:P131* wd:Q1250 ."
            Ambito.NATI_ITALIA -> "?p wdt:P27 wd:Q38 . OPTIONAL { ?p wdt:P19 ?luogo } FILTER(?nlink >= 8)"
        }
        return """
            SELECT ?p ?pLabel ?descr ?quando ?luogoLabel ?itwiki ?nlink WHERE {
              ?p wdt:P31 wd:Q5 ; wdt:$proprietaData ?quando ; wikibase:sitelinks ?nlink .
              ?p p:$proprietaData/psv:$proprietaData [ wikibase:timePrecision 11 ] .
              $luogo
              FILTER(MONTH(?quando) = $mese && DAY(?quando) = $giorno)
              OPTIONAL { ?itwiki schema:about ?p ; schema:isPartOf <https://it.wikipedia.org/> . }
              OPTIONAL { ?p schema:description ?descr FILTER(LANG(?descr) = "it") }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "it,en". }
            } ORDER BY DESC(?nlink) LIMIT $LIMITE
        """.trimIndent()
    }

    fun interpreta(json: String, giorno: Int, mese: Int): List<Fatto> {
        val radice = Json.parseToJsonElement(json).jsonObject
        val righe = radice["results"]?.jsonObject?.elenco("bindings").orEmpty()
        val consultata = oggiIso()
        val visti = HashSet<String>()
        return righe.mapNotNull { r -> riga(r, giorno, mese, consultata) }.filter { visti.add(it.entita ?: it.id) }
    }

    private fun riga(r: JsonObject, giorno: Int, mese: Int, consultata: String): Fatto? {
        val urlEntita = r["p"].testo() ?: return null
        val qid = urlEntita.substringAfterLast('/')
        val etichetta = r["pLabel"].testo()?.trim().orEmpty()
        if (etichetta.isEmpty() || etichetta == qid) return null
        val descrizione = r["descr"].testo()?.trim().orEmpty()
        val quando = r["quando"].testo().orEmpty()
        val anno = quando.take(4).toIntOrNull()
        val luogo = r["luogoLabel"].testo()?.trim()
        val itwiki = r["itwiki"].testo()
        val morte = ambito == Ambito.MORTI_FVG
        val collegamento = if (morte) Collegamento.MORTO else Collegamento.NATO
        val area = when (ambito) {
            Ambito.NATI_ITALIA -> if (luogo != null && Classificatore.riguardaFvg(luogo)) Area.FVG else Area.ITALIA
            else -> Area.FVG
        }

        // Nella descrizione di Wikidata compare spesso "(1879-1957)": se l'anno
        // non coincide con la data, la scheda viene segnata come discordante.
        val anniDescrizione = Regex("\\((\\d{4})\\s*-\\s*(\\d{4})?\\)?").find(descrizione)
        val annoDescrizione = anniDescrizione?.let { if (morte) it.groupValues[2].toIntOrNull() else it.groupValues[1].toIntOrNull() }
        val discordante = anno != null && annoDescrizione != null && annoDescrizione != anno

        val fonti = buildList {
            if (itwiki != null) add(Fonte("Wikipedia (it): $etichetta", itwiki, consultata))
            add(Fonte("Wikidata: $etichetta ($qid)", urlEntita, consultata))
        }
        val testo = buildString {
            if (descrizione.isNotEmpty()) append(descrizione.replaceFirstChar { it.uppercase() }).append(". ")
            append(if (morte) "Scomparso/a" else "Nato/a")
            if (luogo != null) append(" a $luogo")
            append(" il ${Date.giornoMese(giorno, mese)}")
            if (anno != null) append(" $anno")
            append(".")
        }
        val note = mutableListOf("Dato strutturato di Wikidata (licenza CC0)")
        if (discordante) note += "La descrizione indica l'anno $annoDescrizione, la data registrata l'anno $anno"
        return Fatto(
            id = "wikidata-${qid.lowercase()}-${collegamento.name.lowercase()}",
            titolo = etichetta,
            testo = testo,
            giorno = giorno,
            mese = mese,
            anno = anno,
            collegamento = collegamento,
            categoria = if (morte) Categoria.SCOMPARSA else Categoria.NASCITA,
            area = area,
            fonti = fonti,
            verifica = when {
                discordante -> LivelloVerifica.INCOERENTE
                itwiki != null -> LivelloVerifica.MEDIO
                else -> LivelloVerifica.BASSO
            },
            origine = "Wikidata, interrogazione SPARQL (${ambito.etichetta})",
            note = note,
            entita = qid,
        )
    }

    companion object {
        const val ENDPOINT = "https://query.wikidata.org/sparql"
        const val LIMITE = 40
    }
}
