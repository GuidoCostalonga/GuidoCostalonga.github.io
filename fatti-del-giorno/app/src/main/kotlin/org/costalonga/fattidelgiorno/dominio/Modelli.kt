package org.costalonga.fattidelgiorno.dominio

import kotlinx.serialization.Serializable

/** Area geografica a cui il fatto appartiene. */
@Serializable
enum class Area(val etichetta: String) {
    FVG("Friuli Venezia Giulia"),
    ITALIA("Italia"),
    MONDO("Mondo"),
}

/** Categorie selezionabili nella schermata principale. */
@Serializable
enum class Categoria(val etichetta: String) {
    RICORRENZA("Ricorrenze e festività"),
    EVENTO("Eventi storici"),
    NASCITA("Nascite e anniversari"),
    SCOMPARSA("Scomparse"),
    CULTURA("Cultura, scienza, sport e società"),
    CURIOSITA("Aneddoti e curiosità"),
}

/** Perché il fatto è legato alla data selezionata. */
@Serializable
enum class Collegamento(val etichetta: String) {
    AVVENUTO("avvenuto il"),
    NATO("nato/a il"),
    MORTO("scomparso/a il"),
    RICORRENZA("ricorrenza celebrata il"),
}

/** Quanto si può considerare verificata la proposta. */
@Serializable
enum class LivelloVerifica(val etichetta: String, val spiegazione: String) {
    ALTO(
        "Fonte istituzionale verificata",
        "Il fatto e la data sono stati controllati su una fonte ufficiale, indicata fra i collegamenti.",
    ),
    MEDIO(
        "Da Wikipedia: controllare le note",
        "Il fatto viene da una voce di Wikipedia. Prima di pubblicare, apri la voce e controlla le note e le fonti citate.",
    ),
    BASSO(
        "Dato strutturato: verifica consigliata",
        "Il fatto viene da un dato strutturato (Wikidata) senza una voce di enciclopedia collegata. Cerca una conferma in una seconda fonte.",
    ),
    INCOERENTE(
        "Dati discordanti: da verificare",
        "Le fonti consultate non concordano su una data o su un dettaglio. Non pubblicare senza aver chiarito.",
    ),
}

@Serializable
data class Fonte(
    val nome: String,
    val url: String,
    /** Data di consultazione, in formato AAAA-MM-GG, quando nota. */
    val consultataIl: String? = null,
)

@Serializable
data class Fatto(
    val id: String,
    val titolo: String,
    val testo: String,
    val giorno: Int,
    val mese: Int,
    val anno: Int? = null,
    val collegamento: Collegamento,
    val categoria: Categoria,
    val area: Area,
    val fonti: List<Fonte>,
    val verifica: LivelloVerifica,
    /** Codice lingua del testo: "it" oppure "en". */
    val lingua: String = "it",
    /** Da dove arriva il dato, in forma leggibile. */
    val origine: String,
    val note: List<String> = emptyList(),
    /** Identificativo Wikidata (Q...) quando noto: serve a riconoscere lo stesso fatto in fonti diverse. */
    val entita: String? = null,
) {
    /** Frase che spiega il legame con la data, per esempio "avvenuto il 22 ottobre 1922". */
    fun spiegazioneData(): String {
        val base = "${collegamento.etichetta} ${Date.giornoMese(giorno, mese)}"
        return if (anno != null && collegamento != Collegamento.RICORRENZA) "$base $anno" else base
    }
}

enum class TipoAvviso { OFFLINE, LIMITE, TIMEOUT, ERRORE, VUOTO }

@Serializable
data class Avviso(
    val fonte: String,
    val tipo: TipoAvviso,
    val messaggio: String,
)

@Serializable
data class RisultatoRicerca(
    val giorno: Int,
    val mese: Int,
    val fatti: List<Fatto>,
    val avvisi: List<Avviso> = emptyList(),
    /** Vero quando i fatti vengono dalla memoria del telefono e non da una consultazione appena fatta. */
    val daMemoria: Boolean = false,
    /** Momento della consultazione delle fonti, in formato ISO 8601. */
    val consultatoIl: String,
)
