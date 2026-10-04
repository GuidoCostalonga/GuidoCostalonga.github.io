package org.costalonga.sportintv.raccolta

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant

/** Gli istanti viaggiano nel file come testo ISO 8601 in UTC, es. 2026-10-04T18:45:00Z. */
object IstanteSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Istante", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

typealias Istante = @Serializable(with = IstanteSerializer::class) Instant

@Serializable
enum class TipoTrasmissione {
    @SerialName("diretta") DIRETTA,
    @SerialName("differita") DIFFERITA,
    @SerialName("replica") REPLICA,
    /** La fonte non dice se è diretta, differita o replica. */
    @SerialName("non_indicato") NON_INDICATO,
}

@Serializable
enum class Accesso {
    @SerialName("in_chiaro") IN_CHIARO,
    @SerialName("gratuito_con_registrazione") GRATUITO_CON_REGISTRAZIONE,
    @SerialName("abbonamento") ABBONAMENTO,
    @SerialName("acquisto_singolo") ACQUISTO_SINGOLO,
    @SerialName("non_indicato") NON_INDICATO;

    val gratuito: Boolean get() = this == IN_CHIARO || this == GRATUITO_CON_REGISTRAZIONE
    val aPagamento: Boolean get() = this == ABBONAMENTO || this == ACQUISTO_SINGOLO
}

@Serializable
enum class StatoEvento {
    @SerialName("confermato") CONFERMATO,
    @SerialName("da_confermare") DA_CONFERMARE,
    @SerialName("rinviato") RINVIATO,
    @SerialName("annullato") ANNULLATO,
}

@Serializable
enum class StatoFonte {
    /** Risposta ricevuta e letta. */
    @SerialName("ok") OK,
    /** Risposta ricevuta, ma nessun evento sportivo nella finestra. */
    @SerialName("vuota") VUOTA,
    /** Una parte delle richieste è fallita: i dati ci sono ma possono mancare giorni o canali. */
    @SerialName("parziale") PARZIALE,
    /** Fonte non raggiungibile, nessun dato precedente da conservare. */
    @SerialName("errore") ERRORE,
    /** Fonte non raggiungibile: restano i dati dell'ultimo aggiornamento riuscito, forse superati. */
    @SerialName("dati_precedenti") DATI_PRECEDENTI,
}

@Serializable
enum class TipoFonte {
    /** Palinsesto di un'emittente o piattaforma: dice chi trasmette e quando. */
    @SerialName("palinsesto") PALINSESTO,
    /** Calendario della competizione: dice quando si gioca, non chi trasmette in Italia. */
    @SerialName("calendario") CALENDARIO,
}

/** Una singola possibilità di visione in Italia. */
@Serializable
data class Trasmissione(
    val canale: String,
    val piattaforma: String,
    val tipo: TipoTrasmissione,
    val accesso: Accesso,
    /** Pagina ufficiale della trasmissione o del servizio. */
    val link: String,
    val inizio: Istante,
    /** Solo se la fonte la dà. */
    val fine: Istante? = null,
    val fonte: String,
    val verificato: Istante,
    /** Titolo esattamente come lo scrive la fonte. */
    val titoloOriginale: String,
    val nota: String? = null,
)

@Serializable
data class Evento(
    /** Identificativo stabile: stessi partecipanti, stesso sport e stesso giorno danno lo stesso valore. */
    val id: String,
    val sport: String,
    /** "f" per le competizioni femminili, "m" per le altre. */
    val genere: String = "m",
    val competizione: String? = null,
    val titolo: String,
    val partecipanti: List<String> = emptyList(),
    val inizio: Istante,
    val fine: Istante? = null,
    /** Vero se [fine] è una stima e non un dato della fonte. */
    val fineStimata: Boolean = false,
    val stato: StatoEvento,
    val trasmissioni: List<Trasmissione> = emptyList(),
    /** Identificativi delle fonti che citano l'evento. */
    val fonti: List<String>,
    /** Informazioni discordanti fra le fonti, scritte per esteso. */
    val incertezze: List<String> = emptyList(),
    /** Pagina ufficiale dell'evento o della competizione, se nota. */
    val linkUfficiale: String? = null,
    val nota: String? = null,
    /** Inizio secondo il calendario della competizione, se una fonte di calendario lo dà. */
    val inizioCalendario: Istante? = null,
    val fonteCalendario: String? = null,
) {
    /** Vero se almeno un palinsesto ufficiale conferma una trasmissione in Italia. */
    val trasmissioneVerificata: Boolean get() = trasmissioni.isNotEmpty()
}

@Serializable
data class Fonte(
    val id: String,
    val nome: String,
    val tipo: TipoFonte,
    /** Pagina pubblica di riferimento della fonte. */
    val url: String,
    val copertura: String,
    val canali: List<String> = emptyList(),
    val stato: StatoFonte,
    /** Ultimo tentativo di lettura. */
    val ultimaVerifica: Istante,
    /** Ultima lettura riuscita: può essere precedente a [ultimaVerifica]. */
    val ultimoSuccesso: Istante? = null,
    val eventi: Int = 0,
    val messaggio: String? = null,
    val condizioni: String,
)

@Serializable
data class Pacchetto(
    val versione: Int = VERSIONE,
    val generato: Istante,
    val da: Istante,
    val a: Istante,
    val fonti: List<Fonte>,
    val eventi: List<Evento>,
) {
    companion object {
        const val VERSIONE = 1
    }
}

val JsonSportTv = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = false
}
