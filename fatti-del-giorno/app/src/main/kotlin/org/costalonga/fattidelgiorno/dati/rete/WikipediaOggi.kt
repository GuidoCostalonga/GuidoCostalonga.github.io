package org.costalonga.fattidelgiorno.dati.rete

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
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

/**
 * Il feed "Accadde oggi" dell'API REST di Wikipedia
 * (https://<lingua>.wikipedia.org/api/rest_v1/feed/onthisday/all/MM/GG).
 * Non serve nessuna chiave. I testi sono CC BY-SA 4.0: ogni scheda porta il
 * collegamento alla voce da cui proviene.
 */
class WikipediaOggi(
    private val client: ClientHttp,
    private val lingua: String = "it",
    /** Per la lingua inglese si prende solo la selezione in evidenza, per non sommergere l'elenco. */
    private val soloSelezione: Boolean = lingua != "it",
    private val oggiIso: () -> String = { Date.giornoConsultazione() },
    /** Indirizzo del sito: nelle prove punta a un servizio finto. */
    private val base: String = "https://$lingua.wikipedia.org",
) : FonteFatti {

    override val nome: String = "Wikipedia ($lingua)"

    override suspend fun cerca(giorno: Int, mese: Int): EsitoFonte = withContext(Dispatchers.IO) {
        val tipo = if (soloSelezione) "selected" else "all"
        val url = "$base/api/rest_v1/feed/onthisday/$tipo/${Date.dueCifre(mese)}/${Date.dueCifre(giorno)}"
        when (val esito = client.scarica(url, mapOf("Accept" to "application/json"))) {
            is EsitoHttp.Ok -> runCatching { EsitoFonte(interpreta(esito.corpo, giorno, mese)) }
                .getOrElse { EsitoFonte(emptyList(), Avviso(nome, TipoAvviso.ERRORE, "Risposta non leggibile: ${it.message}")) }
            else -> EsitoFonte(emptyList(), avvisoDa(nome, esito))
        }
    }

    /** Trasforma la risposta JSON del feed in schede. Pubblica per le prove. */
    fun interpreta(json: String, giorno: Int, mese: Int): List<Fatto> {
        val radice = Json.parseToJsonElement(json).jsonObject
        val fatti = mutableListOf<Fatto>()
        val consultata = oggiIso()
        fun sezione(nome: String, azione: (JsonObject) -> Fatto?) {
            (radice[nome] as? JsonArray)?.forEach { el -> (el as? JsonObject)?.let { o -> azione(o)?.let(fatti::add) } }
        }
        sezione("holidays") { voce(it, giorno, mese, Collegamento.RICORRENZA, Categoria.RICORRENZA, consultata, inEvidenza = false) }
        sezione("selected") { voce(it, giorno, mese, Collegamento.AVVENUTO, null, consultata, inEvidenza = true) }
        sezione("events") { voce(it, giorno, mese, Collegamento.AVVENUTO, null, consultata, inEvidenza = false) }
        sezione("births") { voce(it, giorno, mese, Collegamento.NATO, Categoria.NASCITA, consultata, inEvidenza = false) }
        sezione("deaths") { voce(it, giorno, mese, Collegamento.MORTO, Categoria.SCOMPARSA, consultata, inEvidenza = false) }
        return fatti
    }

    private fun voce(
        o: JsonObject,
        giorno: Int,
        mese: Int,
        collegamento: Collegamento,
        categoriaFissa: Categoria?,
        consultata: String,
        inEvidenza: Boolean,
    ): Fatto? {
        val testo = o["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (testo.isEmpty()) return null
        val anno = if (collegamento == Collegamento.RICORRENZA) null else o["year"]?.jsonPrimitive?.intOrNull
        val pagine = (o["pages"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val prima = pagine.firstOrNull()
        val titoloPagina = prima?.get("titles")?.jsonObject?.get("normalized")?.jsonPrimitive?.contentOrNull
        val descrizioni = pagine.mapNotNull { it["description"]?.jsonPrimitive?.contentOrNull }
        val estratto = prima?.get("extract")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val fonti = pagine.take(3).mapNotNull { p ->
            val url = p["content_urls"]?.jsonObject?.get("desktop")?.jsonObject?.get("page")?.jsonPrimitive?.contentOrNull
            val titolo = p["titles"]?.jsonObject?.get("normalized")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            url?.let { Fonte("Wikipedia ($lingua): $titolo", it, consultata) }
        }
        if (fonti.isEmpty()) return null
        val entita = prima?.get("wikibase_item")?.jsonPrimitive?.contentOrNull
        val testoPerArea = (listOf(testo) + descrizioni).joinToString(" ")
        val area = Classificatore.area(testoPerArea)
        val categoria = categoriaFissa ?: Classificatore.categoriaEvento(testo)
        val titolo = when (collegamento) {
            Collegamento.NATO, Collegamento.MORTO -> titoloPagina ?: testo.substringBefore(",").take(80)
            Collegamento.RICORRENZA -> testo.take(110)
            else -> if (testo.length <= 110) testo.trimEnd('.') else testo.take(107).trimEnd() + "…"
        }
        val corpo = buildString {
            append(testo)
            if (estratto.isNotEmpty() && collegamento != Collegamento.RICORRENZA) {
                append("\n\nDalla voce collegata: ")
                append(accorcia(estratto, 320))
            }
        }
        val note = mutableListOf<String>()
        if (inEvidenza) note += "In evidenza nel feed di Wikipedia per questa data"
        if (lingua != "it") note += "Testo in inglese: la fonte è Wikipedia in inglese"
        note += "Area assegnata dall'app in base al testo: ${area.etichetta}"
        val id = "wikipedia-$lingua-${collegamento.name.lowercase()}-${Classificatore.normalizza(titolo).take(40).replace(' ', '-')}-${anno ?: ""}"
        return Fatto(
            id = id,
            titolo = titolo,
            testo = corpo,
            giorno = giorno,
            mese = mese,
            anno = anno,
            collegamento = collegamento,
            categoria = categoria,
            area = area,
            fonti = fonti,
            verifica = LivelloVerifica.MEDIO,
            lingua = lingua,
            origine = "Wikipedia ($lingua), feed \"Accadde oggi\"",
            note = note,
            entita = entita,
        )
    }

    private fun accorcia(testo: String, massimo: Int): String {
        if (testo.length <= massimo) return testo
        val taglio = testo.take(massimo)
        val fine = taglio.lastIndexOfAny(charArrayOf('.', ';'))
        return if (fine > massimo / 2) taglio.take(fine + 1) else taglio.trimEnd() + "…"
    }
}

/** Accesso comodo a un campo testuale di un oggetto JSON, nullo se manca. */
internal fun JsonElement?.testo(): String? = (this as? JsonObject)?.get("value")?.jsonPrimitive?.contentOrNull

internal fun JsonObject.elenco(nome: String): List<JsonObject> =
    (this[nome] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

internal val JsonElement.oggetti: List<JsonObject>
    get() = jsonArray.mapNotNull { it as? JsonObject }
