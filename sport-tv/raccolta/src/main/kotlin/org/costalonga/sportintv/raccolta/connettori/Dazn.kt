package org.costalonga.sportintv.raccolta.connettori

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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
import org.costalonga.sportintv.raccolta.RisultatoFonte
import org.costalonga.sportintv.raccolta.StatoEvento
import org.costalonga.sportintv.raccolta.Sport
import org.costalonga.sportintv.raccolta.Testo
import org.costalonga.sportintv.raccolta.TipoFonte
import org.costalonga.sportintv.raccolta.TipoTrasmissione
import java.time.Instant

/**
 * Programmazione ufficiale DAZN per l'Italia: lo stesso servizio che alimenta
 * la pagina "Programmazione" di dazn.com. Un giorno per richiesta, orari UTC.
 * DAZN pubblica gli eventi con anticipo variabile: completi per 7-8 giorni,
 * poi solo quelli già fissati.
 */
class Dazn : Connettore {
    override val id = "dazn"
    override val nome = "DAZN · programmazione Italia"
    override val tipo = TipoFonte.PALINSESTO
    override val url = PAGINA
    override val copertura = "Tutti gli eventi in programma su DAZN in Italia (Serie A, Serie A femminile, " +
        "basket, pallavolo, NFL, motori, eventi Eurosport su DAZN e altro)"
    override val canali = listOf("DAZN")
    override val condizioni = "Servizio pubblico usato dalla pagina di programmazione DAZN, senza autenticazione. " +
        "Non documentato: può cambiare senza preavviso. Letto poche volte al giorno, con rimando alla pagina DAZN."

    companion object {
        const val PAGINA = "https://www.dazn.com/it-IT/schedule"

        /** Trasmissioni di contorno e canali lineari, da non confondere con gli eventi. */
        private val contorno = Regex("\\b(vamos|fuoriclasse|powered by|zona serie a|il sabato della serie a|la domenica della serie a|radio tv serie a)\\b")

        private fun JsonObject.testo(chiave: String) = this[chiave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        private fun JsonObject.titoloDi(chiave: String) = (this[chiave] as? JsonObject)?.testo("Title")

        fun leggi(json: String, verificato: Instant): List<Elemento> {
            val radice = JsonSportTv.parseToJsonElement(json).jsonObject
            val tessere = radice["Tiles"]?.jsonArray ?: return emptyList()
            return tessere.mapNotNull { leggiTessera(it.jsonObject, verificato) }
        }

        private fun leggiTessera(t: JsonObject, verificato: Instant): Elemento? {
            val tipo = t.testo("Type") ?: return null
            // CatchUp e Highlights sono contenuti già disponibili su richiesta, non messe in onda.
            if (tipo != "Live" && tipo != "UpComing") return null
            if (t["IsLinear"]?.jsonPrimitive?.booleanOrNull == true) return null
            val fineTesto = t.testo("End")
            if (fineTesto != null && fineTesto.startsWith("3000")) return null

            val titoloOriginale = t.testo("Title") ?: return null
            val competizione = t.titoloDi("Competition")
            val sportFonte = t.titoloDi("Sport")
            if (sportFonte == null || sportFonte == "Live TV") return null
            val contendenti = (t["Contestants"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.testo("Title") } ?: emptyList()

            // Il titolo usa i nomi italiani e l'ordine casa-ospite; i Contestants
            // non sempre rispettano l'ordine e servono solo se il titolo ha un prefisso.
            val sfida = titoloOriginale.substringAfterLast(" | ")
            val dalTitolo = if (sfida.contains('!') || sfida.contains(':')) emptyList() else Testo.partecipanti(sfida)
            val partecipanti = when {
                dalTitolo.size == 2 -> dalTitolo
                contendenti.size == 2 -> contendenti.sortedBy { c ->
                    val g = Testo.gettoni(c).firstOrNull() ?: c
                    Testo.semplifica(titoloOriginale).indexOf(g).let { if (it < 0) Int.MAX_VALUE else it }
                }
                else -> emptyList()
            }.map { Testo.nomeLeggibile(it) }
            if (partecipanti.isEmpty() && (Testo.eRubrica(titoloOriginale, competizione) || contorno.containsMatchIn(Testo.semplifica("$titoloOriginale ${competizione ?: ""}")))) {
                return null
            }

            val inizio = Instant.parse(t.testo("Start") ?: return null)
            val fine = fineTesto?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val gratis = t["IsFreeToView"]?.jsonPrimitive?.booleanOrNull == true
            val nflGamePass = competizione?.contains("Game Pass", ignoreCase = true) == true
            val etichetta = titoloOriginale.substringBefore(" | ", "").trim().takeIf { partecipanti.isNotEmpty() && it.isNotEmpty() }
            return Elemento(
                fonte = "dazn",
                sport = Sport.riconosci(sportFonte, competizione, titoloOriginale),
                genere = Testo.genere(competizione, titoloOriginale, t.titoloDi("TournamentCalendar")),
                competizione = competizione,
                titolo = if (partecipanti.isNotEmpty()) partecipanti.joinToString(" - ") else titoloOriginale.replace(" | ", " · "),
                partecipanti = partecipanti,
                inizio = inizio,
                fine = fine,
                verificato = verificato,
                canale = "DAZN",
                piattaforma = "DAZN · streaming",
                // DAZN segna come Live o UpComing solo le messe in onda in diretta.
                tipo = TipoTrasmissione.DIRETTA,
                accesso = if (gratis) Accesso.GRATUITO_CON_REGISTRAZIONE else Accesso.ABBONAMENTO,
                link = PAGINA,
                titoloOriginale = titoloOriginale,
                nota = listOfNotNull(
                    etichetta?.let { "Versione «$it»" },
                    if (nflGamePass) "Richiede il pacchetto NFL Game Pass su DAZN" else null,
                    if (gratis) "Visibile gratis con un account DAZN" else null,
                ).joinToString(". ").ifEmpty { null },
                stato = when (t.testo("Status")?.lowercase()) {
                    "postponed" -> StatoEvento.RINVIATO
                    "cancelled", "canceled" -> StatoEvento.ANNULLATO
                    else -> null
                },
            )
        }
    }

    override suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte = coroutineScope {
        val esiti = finestra.date.map { giorno ->
            async {
                try {
                    val json = http.testo("https://epg.discovery.indazn.com/eu/v5/Epg?date=$giorno&country=it&languageCode=it&openBrowse=true&timeZoneOffset=0")
                    Result.success(leggi(json, Instant.now()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
        RisultatoFonte(
            elementi = esiti.flatMap { it.getOrNull() ?: emptyList() },
            riuscite = esiti.count { it.isSuccess },
            fallite = esiti.count { it.isFailure },
        )
    }
}
