package org.costalonga.sportintv.raccolta.connettori

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

private fun JsonObject.testo(chiave: String) = this[chiave]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Calendario della Serie A dal progetto openfootball (dati di pubblico dominio,
 * CC0). Dice quando si gioca, non chi trasmette: le partite che non compaiono
 * in un palinsesto restano "Trasmissione in Italia da confermare".
 * Gli orari sono in ora italiana (verificato sui primi turni confrontandoli
 * con gli orari UTC di altre fonti).
 */
class OpenFootballSerieA : Connettore {
    override val id = "openfootball"
    override val nome = "openfootball · calendario Serie A"
    override val tipo = TipoFonte.CALENDARIO
    override val url = "https://github.com/openfootball/football.json"
    override val canali = emptyList<String>()
    override val copertura = "Calendario della Serie A maschile (solo date e orari, nessun dato televisivo)"
    override val condizioni = "Dati di pubblico dominio (CC0 1.0), file statici su GitHub aggiornati una volta al giorno."

    companion object {
        /**
         * Indicazione generale, non relativa alla singola partita: assegnazione
         * dei diritti 2024-2029 deliberata dall'assemblea della Lega Serie A il
         * 23 ottobre 2023 (DAZN tutte le partite, Sky tre per giornata in
         * co-esclusiva).
         */
        const val NOTA_DIRITTI = "Indicazione generale, non verificata per questa partita: secondo l'assegnazione dei diritti " +
            "2024-2029 della Lega Serie A, DAZN trasmette tutte le partite e Sky tre per giornata in co-esclusiva. " +
            "La partita non compare ancora in un palinsesto pubblicato."

        fun stagione(oggi: LocalDate): String {
            val inizio = if (oggi.monthValue >= 7) oggi.year else oggi.year - 1
            return "$inizio-" + "%02d".format((inizio + 1) % 100)
        }

        fun leggi(json: String, verificato: Instant): List<Elemento> {
            val partite = JsonSportTv.parseToJsonElement(json).jsonObject["matches"]?.jsonArray ?: return emptyList()
            return partite.mapNotNull { p ->
                val o = p.jsonObject
                val data = o.testo("date") ?: return@mapNotNull null
                val ora = o.testo("time")?.takeIf { Regex("\\d{1,2}:\\d{2}").matches(it) } ?: return@mapNotNull null
                val casa = Testo.nomeBreveSquadra(o.testo("team1") ?: return@mapNotNull null)
                val ospite = Testo.nomeBreveSquadra(o.testo("team2") ?: return@mapNotNull null)
                val inizio = ZonedDateTime.of(LocalDate.parse(data), LocalTime.parse(ora.padStart(5, '0')), ROMA).toInstant()
                val giornata = o.testo("round")?.replace("Matchday", "giornata")?.let { r ->
                    Regex("giornata (\\d+)").find(r)?.groupValues?.get(1)?.let { "${it}ª giornata" } ?: r
                }
                Elemento(
                    fonte = "openfootball",
                    sport = Sport.CALCIO,
                    genere = "m",
                    competizione = "Serie A",
                    titolo = "$casa - $ospite",
                    partecipanti = listOf(casa, ospite),
                    inizio = inizio,
                    fine = null,
                    verificato = verificato,
                    linkUfficiale = "https://www.legaseriea.it/serie-a",
                    notaEvento = giornata,
                    notaSeNonTrasmesso = NOTA_DIRITTI,
                )
            }
        }
    }

    override suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte {
        val s = stagione(finestra.primoGiorno)
        val json = http.testo("https://raw.githubusercontent.com/openfootball/football.json/master/$s/it.1.json")
        return RisultatoFonte(leggi(json, Instant.now()).filter { finestra.contiene(it.inizio) }, 1, 0)
    }
}

/**
 * Calendario della Formula 1 dal servizio Jolpica F1 (erede di Ergast):
 * dati CC BY-NC-SA 4.0, uso non commerciale con attribuzione, limite di 500
 * richieste l'ora. Anche qui nessun dato televisivo.
 */
class JolpicaF1 : Connettore {
    override val id = "jolpica"
    override val nome = "Jolpica F1 · calendario Formula 1"
    override val tipo = TipoFonte.CALENDARIO
    override val url = "https://github.com/jolpica/jolpica-f1"
    override val canali = emptyList<String>()
    override val copertura = "Calendario della Formula 1: prove, qualifiche, sprint e gran premi (nessun dato televisivo)"
    override val condizioni = "Servizio gratuito con dati CC BY-NC-SA 4.0: uso non commerciale con attribuzione; 4 richieste al secondo, 500 l'ora."

    companion object {
        private val sessioni = listOf(
            "FirstPractice" to "Prove libere 1",
            "SecondPractice" to "Prove libere 2",
            "ThirdPractice" to "Prove libere 3",
            "SprintQualifying" to "Qualifiche sprint",
            "Sprint" to "Sprint",
            "Qualifying" to "Qualifiche",
        )

        private val nomiGp = mapOf(
            "United States" to "degli Stati Uniti", "Mexico City" to "di Città del Messico", "Brazilian" to "del Brasile",
            "Las Vegas" to "di Las Vegas", "Qatar" to "del Qatar", "Abu Dhabi" to "di Abu Dhabi", "Singapore" to "di Singapore",
            "Azerbaijan" to "dell'Azerbaigian", "Italian" to "d'Italia", "Dutch" to "d'Olanda", "Japanese" to "del Giappone",
            "Australian" to "d'Australia", "Chinese" to "di Cina", "Bahrain" to "del Bahrein", "Saudi Arabian" to "dell'Arabia Saudita",
            "Miami" to "di Miami", "Emilia Romagna" to "dell'Emilia-Romagna", "Monaco" to "di Monaco", "Spanish" to "di Spagna",
            "Barcelona-Catalunya" to "di Barcellona", "Madrid" to "di Madrid", "Canadian" to "del Canada", "Austrian" to "d'Austria",
            "British" to "di Gran Bretagna", "Belgian" to "del Belgio", "Hungarian" to "d'Ungheria",
        )

        fun nomeItaliano(nome: String): String {
            val chiave = nome.removeSuffix(" Grand Prix").trim()
            return nomiGp[chiave]?.let { "Gran Premio $it" } ?: nome
        }

        fun leggi(json: String, verificato: Instant): List<Elemento> {
            val gare = JsonSportTv.parseToJsonElement(json).jsonObject["MRData"]?.jsonObject
                ?.get("RaceTable")?.jsonObject?.get("Races")?.jsonArray ?: return emptyList()
            return gare.flatMap { g ->
                val o = g.jsonObject
                val gp = nomeItaliano(o.testo("raceName") ?: return@flatMap emptyList())
                val pagina = "https://www.formula1.com/en/racing/${o.testo("season")}"
                val voci = sessioni.mapNotNull { (chiave, nome) ->
                    val s = o[chiave] as? JsonObject ?: return@mapNotNull null
                    Triple(nome, s.testo("date"), s.testo("time"))
                } + Triple("Gara", o.testo("date"), o.testo("time"))
                voci.mapNotNull { (sessione, data, ora) ->
                    if (data == null || ora == null) return@mapNotNull null
                    Elemento(
                        fonte = "jolpica",
                        sport = Sport.MOTORI,
                        genere = "m",
                        competizione = "Formula 1",
                        titolo = "$gp · $sessione",
                        partecipanti = emptyList(),
                        inizio = Instant.parse("${data}T$ora"),
                        fine = null,
                        verificato = verificato,
                        linkUfficiale = pagina,
                    )
                }
            }
        }
    }

    override suspend fun raccogli(finestra: Finestra, http: Http): RisultatoFonte {
        val json = http.testo("https://api.jolpi.ca/ergast/f1/${finestra.primoGiorno.year}/races.json?limit=100")
        var elementi = leggi(json, Instant.now())
        // A cavallo d'anno serve anche la stagione successiva.
        if (finestra.date.last().year != finestra.primoGiorno.year) {
            elementi = elementi + leggi(http.testo("https://api.jolpi.ca/ergast/f1/${finestra.date.last().year}/races.json?limit=100"), Instant.now())
        }
        return RisultatoFonte(elementi.filter { finestra.contiene(it.inizio) }, 1, 0)
    }
}

/** Tutte le fonti, nell'ordine in cui compaiono nell'app. */
fun tuttiIConnettori(): List<Connettore> = listOf(
    RaiPlay(), MediasetInfinity(), Dazn(), SuperTennis(), OpenFootballSerieA(), JolpicaF1(),
)
