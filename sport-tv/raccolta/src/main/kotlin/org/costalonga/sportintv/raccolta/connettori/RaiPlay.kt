package org.costalonga.sportintv.raccolta.connettori

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.costalonga.sportintv.raccolta.Accesso
import org.costalonga.sportintv.raccolta.Connettore
import org.costalonga.sportintv.raccolta.Elemento
import org.costalonga.sportintv.raccolta.Finestra
import org.costalonga.sportintv.raccolta.Http
import org.costalonga.sportintv.raccolta.JsonSportTv
import org.costalonga.sportintv.raccolta.ROMA
import org.costalonga.sportintv.raccolta.RisultatoFonte
import org.costalonga.sportintv.raccolta.RispostaNonValida
import org.costalonga.sportintv.raccolta.Sport
import org.costalonga.sportintv.raccolta.Testo
import org.costalonga.sportintv.raccolta.TipoFonte
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Palinsesto ufficiale Rai, lo stesso file JSON che usa la pagina
 * https://www.raiplay.it/palinsesto. Un file per canale e per giorno, con
 * orari in ora italiana. Rai pubblica di norma 7-9 giorni in avanti.
 */
class RaiPlay : Connettore {
    override val id = "raiplay"
    override val nome = "RaiPlay · palinsesto Rai"
    override val tipo = TipoFonte.PALINSESTO
    override val url = "https://www.raiplay.it/palinsesto"
    override val copertura = "Tutti gli sport trasmessi da Rai 1, Rai 2, Rai 3 e Rai Sport"
    override val canali = CANALI.map { it.nome }
    override val condizioni = "File pubblico usato dalla pagina del palinsesto RaiPlay, senza autenticazione. " +
        "Non è un servizio documentato: può cambiare senza preavviso. Letto poche volte al giorno, con rimando alla pagina Rai."

    data class Canale(val codice: String, val nome: String, val diretta: String)

    companion object {
        val CANALI = listOf(
            Canale("rai-1", "Rai 1", "https://www.raiplay.it/dirette/rai1"),
            Canale("rai-2", "Rai 2", "https://www.raiplay.it/dirette/rai2"),
            Canale("rai-3", "Rai 3", "https://www.raiplay.it/dirette/rai3"),
            Canale("rai-sport", "Rai Sport", "https://www.raiplay.it/dirette/raisport"),
        )
        private val formatoUrl = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        private val formatoData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val durata = Regex("(\\d+):(\\d{2}):(\\d{2})")

        /** Ora italiana della fonte convertita in UTC, con l'ora legale gestita da java.time. */
        fun istante(data: String, ora: String): Instant =
            ZonedDateTime.of(LocalDate.parse(data, formatoData), LocalTime.parse(ora), ROMA).toInstant()

        fun durata(testo: String?): Duration? {
            val m = durata.matchEntire(testo ?: return null) ?: return null
            val (h, mi, s) = m.destructured
            return Duration.ofHours(h.toLong()).plusMinutes(mi.toLong()).plusSeconds(s.toLong())
        }

        /** Legge un giorno di palinsesto e tiene i soli eventi sportivi. */
        fun leggi(json: String, canale: Canale, verificato: Instant): List<Elemento> {
            val radice = JsonSportTv.parseToJsonElement(json).jsonObject
            val eventi = radice["events"]?.jsonArray ?: return emptyList()
            return eventi.mapNotNull { leggiEvento(it.jsonObject, canale, verificato) }
        }

        private fun JsonObject.testo(chiave: String) = this[chiave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun leggiEvento(e: JsonObject, canale: Canale, verificato: Instant): Elemento? {
            val nome = e.testo("name") ?: return null
            val dfp = e["dfp"]?.jsonObject
            val tipologia = dfp?.testo("escaped_typology_name")
            val genere = dfp?.testo("escaped_genre_name")
            val programma = e["program"]?.jsonObject?.testo("name")
            val descrizione = e.testo("description")
            // Su Rai Sport è tutto sport; sugli altri canali solo ciò che Rai classifica come Sport.
            if (canale.codice != "rai-sport" && tipologia != "Sport") return null
            if (Testo.eRubrica(nome, programma)) return null

            val inizio = istante(e.testo("date") ?: return null, e.testo("hour") ?: return null)
            val fine = durata(e.testo("duration"))?.let { inizio.plus(it) }

            // "Calcio: Serie A Women 2026-2027 - 2a giornata: Roma - Lazio"
            val sfida = nome.substringAfterLast(':', "").trim().ifEmpty { null }
            val partecipanti = Testo.partecipanti(sfida)
            val sport = Sport.riconosci(nome.substringBefore(':'), programma, nome, genere)
            val competizione = competizione(nome, programma, sport)
            val titolo = if (partecipanti.isNotEmpty()) partecipanti.joinToString(" - ") else senzaPrefissoSport(nome, programma)
            val link = e.testo("event_weblink")?.let { "https://www.raiplay.it$it" } ?: canale.diretta

            return Elemento(
                fonte = "raiplay",
                sport = sport,
                genere = Testo.genere(nome, programma),
                competizione = competizione,
                titolo = titolo,
                partecipanti = partecipanti,
                inizio = inizio,
                fine = fine,
                verificato = verificato,
                canale = canale.nome,
                piattaforma = "Rai · digitale terrestre e RaiPlay",
                tipo = Testo.tipoDaTesto(nome, descrizione),
                accesso = Accesso.IN_CHIARO,
                link = link,
                titoloOriginale = nome,
            )
        }

        private val prefisso = Regex("^([^:.\\-–]{3,40}?)\\s*(?::|\\.|\\s[-–])\\s+")

        /**
         * Toglie dal titolo il nome dello sport messo in testa:
         * "Ciclismo - Coppa Agostoni 2026" diventa "Coppa Agostoni 2026",
         * "Equitazione. Coppa degli Assi" diventa "Coppa degli Assi".
         */
        fun senzaPrefissoSport(nome: String, programma: String?): String {
            val m = prefisso.find(nome) ?: return nome
            val testa = Testo.semplifica(m.groupValues[1])
            val eSport = Sport.entries.any { Testo.semplifica(it.nome) == testa || Sport.riconosci(testa) == it && testa.split(' ').size <= 2 } ||
                (programma != null && Testo.semplifica(programma) == testa)
            val resto = nome.substring(m.range.last + 1).trim()
            return if (eSport && resto.length >= 4) resto else nome
        }

        /**
         * La competizione è il nome del programma, se non è il nome generico
         * dello sport; altrimenti il primo tratto del titolo, prima dei due punti.
         */
        fun competizione(nome: String, programma: String?, sport: Sport): String? {
            val p = programma?.substringAfter(": ")?.trim()?.takeIf { !Sport.eGenerico(it) }
            if (p != null) return p
            val senza = senzaPrefissoSport(nome, programma)
            if (!senza.contains(':')) return null
            return senza.substringBeforeLast(':').substringBefore(" - ").trim().ifEmpty { null }
        }
    }

    override suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte = coroutineScope {
        val esiti = CANALI.map { canale ->
            async { raccogliCanale(canale, finestra, http) }
        }.awaitAll()
        RisultatoFonte(esiti.flatMap { it.elementi }, esiti.sumOf { it.riuscite }, esiti.sumOf { it.fallite }, esiti.firstNotNullOfOrNull { it.messaggio })
    }

    /** Giorno dopo giorno finché Rai non risponde 404: oltre quel giorno il palinsesto non è ancora pubblicato. */
    private suspend fun raccogliCanale(canale: Canale, finestra: Finestra, http: Http): RisultatoFonte {
        val elementi = mutableListOf<Elemento>()
        var riuscite = 0
        var fallite = 0
        var primoErrore: String? = null
        for (giorno in finestra.date) {
            val indirizzo = "https://www.raiplay.it/palinsesto/app/${canale.codice}/${giorno.format(formatoUrl)}.json"
            try {
                val json = http.testo(indirizzo)
                elementi += leggi(json, canale, Instant.now())
                riuscite++
            } catch (e: RispostaNonValida) {
                if (e.codice == 404) break
                fallite++
                if (primoErrore == null) primoErrore = org.costalonga.sportintv.raccolta.Raccoglitore.descrivi(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallite++
                if (primoErrore == null) primoErrore = org.costalonga.sportintv.raccolta.Raccoglitore.descrivi(e)
            }
        }
        return RisultatoFonte(elementi, riuscite, fallite, primoErrore)
    }
}
