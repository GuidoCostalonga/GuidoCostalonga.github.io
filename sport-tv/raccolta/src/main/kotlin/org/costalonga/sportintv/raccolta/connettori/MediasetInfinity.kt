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
import kotlinx.serialization.json.longOrNull
import org.costalonga.sportintv.raccolta.Accesso
import org.costalonga.sportintv.raccolta.Connettore
import org.costalonga.sportintv.raccolta.Elemento
import org.costalonga.sportintv.raccolta.Finestra
import org.costalonga.sportintv.raccolta.Http
import org.costalonga.sportintv.raccolta.JsonSportTv
import org.costalonga.sportintv.raccolta.ROMA
import org.costalonga.sportintv.raccolta.RisultatoFonte
import org.costalonga.sportintv.raccolta.Sport
import org.costalonga.sportintv.raccolta.Testo
import org.costalonga.sportintv.raccolta.TipoFonte
import java.time.Instant

/**
 * Guida TV ufficiale di Mediaset Infinity: lo stesso servizio JSON che usa la
 * pagina https://mediasetinfinity.mediaset.it/guidatv. Orari in millisecondi
 * UTC. Mediaset pubblica di norma 8-10 giorni in avanti.
 */
class MediasetInfinity : Connettore {
    override val id = "mediaset"
    override val nome = "Mediaset Infinity · guida TV"
    override val tipo = TipoFonte.PALINSESTO
    override val url = "https://mediasetinfinity.mediaset.it/guidatv"
    override val copertura = "Eventi sportivi su Canale 5, Italia 1, Rete 4, 20 Mediaset e Italia 2"
    override val canali = CANALI.map { it.nome }
    override val condizioni = "Servizio pubblico usato dalla guida TV di Mediaset Infinity, senza autenticazione. " +
        "Non documentato: può cambiare senza preavviso. Letto poche volte al giorno, con rimando alla pagina Mediaset."

    data class Canale(val sigla: String, val nome: String, val diretta: String)

    companion object {
        val CANALI = listOf(
            Canale("C5", "Canale 5", "https://mediasetinfinity.mediaset.it/diretta/canale5_cC5"),
            Canale("I1", "Italia 1", "https://mediasetinfinity.mediaset.it/diretta/italia1_cI1"),
            Canale("R4", "Rete 4", "https://mediasetinfinity.mediaset.it/diretta/rete4_cR4"),
            Canale("LB", "20 Mediaset", "https://mediasetinfinity.mediaset.it/diretta/20_cLB"),
            Canale("I2", "Italia 2", "https://mediasetinfinity.mediaset.it/diretta/italia2_cI2"),
        )

        private val generiSport = setOf("sport", "calcio", "altri sport", "motori", "tennis", "basket", "ciclismo", "pallavolo", "volley", "motociclismo", "automobilismo", "pugilato", "football americano")

        /** Parole che indicano un evento e non un programma di contorno. */
        private val paroleEvento = Regex(
            "\\b(gara|gran premio|grand prix|gp|finale|semifinale|qualifiche|partita|match|tappa|giro|coppa|cup|trofeo|torneo|" +
                "campionato|mondial[ei]|europe[io]|olimpi|open|rally|meeting|maratona|classica|derby|giornata|super ?bowl)\\b"
        )

        private val conVoci = Regex("\"listings\"\\s*:\\s*\\[\\s*\\{")

        private fun JsonObject.testo(chiave: String) = this[chiave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        fun leggi(json: String, canale: Canale, verificato: Instant): List<Elemento> {
            val radice = JsonSportTv.parseToJsonElement(json).jsonObject
            val voci = radice["response"]?.jsonObject?.get("entries")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("listings")?.jsonArray ?: return emptyList()
            return voci.mapNotNull { leggiVoce(it.jsonObject, canale, verificato) }
        }

        private fun leggiVoce(v: JsonObject, canale: Canale, verificato: Instant): Elemento? {
            val programma = v["program"]?.jsonObject ?: return null
            val generi = programma["mediasetprogram\$genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            if (generi.none { Testo.semplifica(it) in generiSport }) return null

            val titoloEpg = v.testo("mediasetlisting\$epgTitle")
            val titoloProgramma = programma.testo("title")
            val marchio = programma.testo("mediasetprogram\$brandTitle")
            val descrizione = v.testo("description") ?: programma.testo("description")
            if (Testo.eRubrica(titoloEpg, titoloProgramma, marchio)) return null

            // "Indianapolis Colts-Washington Commanders: partita integrale"
            val sfida = (titoloProgramma ?: titoloEpg ?: return null).substringBefore(':')
            val partecipanti = Testo.partecipanti(sfida, trattinoStretto = true)
            val testoEvento = Testo.semplifica(listOfNotNull(titoloEpg, titoloProgramma, marchio).joinToString(" "))
            if (partecipanti.isEmpty() && !paroleEvento.containsMatchIn(testoEvento)) return null

            val inizio = Instant.ofEpochMilli(v["startTime"]?.jsonPrimitive?.longOrNull ?: return null)
            val fine = v["endTime"]?.jsonPrimitive?.longOrNull?.let { Instant.ofEpochMilli(it) }
            val sport = Sport.riconosci(generi.firstOrNull { Testo.semplifica(it) !in setOf("sport", "altri sport") }, marchio, titoloProgramma, titoloEpg)
            val pagina = programma.testo("mediasetprogram\$videoPageUrl") ?: programma.testo("mediasetprogram\$pageUrl")
            val titolo = when {
                partecipanti.isNotEmpty() -> partecipanti.joinToString(" - ")
                else -> titoloProgramma ?: titoloEpg!!
            }
            return Elemento(
                fonte = "mediaset",
                sport = sport,
                genere = Testo.genere(titoloEpg, titoloProgramma, marchio),
                competizione = marchio?.takeIf { Testo.semplifica(it) != Testo.semplifica(titolo) }?.let { Testo.nomeLeggibile(it) },
                titolo = titolo,
                partecipanti = partecipanti,
                inizio = inizio,
                fine = fine,
                verificato = verificato,
                canale = canale.nome,
                piattaforma = "Mediaset · digitale terrestre e Mediaset Infinity",
                tipo = Testo.tipoDaTesto(titoloEpg, titoloProgramma, descrizione),
                accesso = Accesso.IN_CHIARO,
                link = pagina?.let { if (it.startsWith("//")) "https:$it" else it } ?: canale.diretta,
                titoloOriginale = titoloProgramma ?: titoloEpg!!,
            )
        }
    }

    override suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte = coroutineScope {
        val esiti = CANALI.map { canale -> async { raccogliCanale(canale, finestra, http) } }.awaitAll()
        RisultatoFonte(esiti.flatMap { it.first }, esiti.sumOf { it.second }, esiti.sumOf { it.third })
    }

    /** Un giorno per richiesta (intervalli più lunghi tornano vuoti); ci si ferma al primo giorno vuoto. */
    private suspend fun raccogliCanale(canale: Canale, finestra: Finestra, http: Http): Triple<List<Elemento>, Int, Int> {
        val elementi = mutableListOf<Elemento>()
        var riuscite = 0
        var fallite = 0
        for (giorno in finestra.date) {
            val da = giorno.atStartOfDay(ROMA).toInstant().toEpochMilli()
            val a = giorno.plusDays(1).atStartOfDay(ROMA).toInstant().toEpochMilli()
            val indirizzo = "https://api-ott-prod-fe.mediaset.net/PROD/play/feed/allListingFeedEpg/v2.0" +
                "?byListingTime=$da~$a&byCallSign=${canale.sigla}"
            try {
                val json = http.testo(indirizzo)
                riuscite++
                if (!conVoci.containsMatchIn(json)) break
                elementi += leggi(json, canale, Instant.now())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fallite++
            }
        }
        return Triple(elementi, riuscite, fallite)
    }
}
