package org.costalonga.fattidelgiorno.dati.rete

import org.costalonga.fattidelgiorno.dominio.Avviso
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.TipoAvviso

/** Ciò che una fonte restituisce: i fatti trovati e, se qualcosa non è andato, un avviso. */
data class EsitoFonte(
    val fatti: List<Fatto>,
    val avviso: Avviso? = null,
    /** Vero se la fonte ha risposto, anche con zero risultati. */
    val haRisposto: Boolean = avviso == null,
)

/** Una fonte interrogabile per giorno e mese. */
interface FonteFatti {
    val nome: String
    suspend fun cerca(giorno: Int, mese: Int): EsitoFonte
}

internal fun avvisoDa(nomeFonte: String, esito: EsitoHttp): Avviso? = when (esito) {
    is EsitoHttp.Ok -> null
    is EsitoHttp.LimiteRaggiunto -> Avviso(
        nomeFonte,
        TipoAvviso.LIMITE,
        "Limite di richieste raggiunto" + (esito.riprovaFraSecondi?.let { ": riprova fra $it secondi" } ?: ". Riprova fra qualche minuto"),
    )
    EsitoHttp.Timeout -> Avviso(nomeFonte, TipoAvviso.TIMEOUT, "La fonte non ha risposto in tempo")
    is EsitoHttp.Errore -> Avviso(nomeFonte, TipoAvviso.ERRORE, "Errore della fonte: ${esito.descrizione}")
}
