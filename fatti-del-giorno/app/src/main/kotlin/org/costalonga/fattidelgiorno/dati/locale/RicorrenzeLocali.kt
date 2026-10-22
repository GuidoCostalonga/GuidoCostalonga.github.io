package org.costalonga.fattidelgiorno.dati.locale

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.costalonga.fattidelgiorno.dominio.Area
import org.costalonga.fattidelgiorno.dominio.Categoria
import org.costalonga.fattidelgiorno.dominio.Collegamento
import org.costalonga.fattidelgiorno.dominio.Fatto
import org.costalonga.fattidelgiorno.dominio.Fonte
import org.costalonga.fattidelgiorno.dominio.LivelloVerifica

/**
 * Ricorrenze e fatti del Friuli Venezia Giulia e dell'Italia controllati a
 * mano su fonti istituzionali, depositati nel file assets/ricorrenze.json.
 * Funzionano anche senza rete. Ogni voce porta la fonte e la data del controllo.
 */
class RicorrenzeLocali(private val leggiJson: () -> String) {

    @Serializable
    data class Voce(
        val id: String,
        val titolo: String,
        val testo: String,
        val giorno: Int,
        val mese: Int,
        val anno: Int? = null,
        val categoria: Categoria,
        val collegamento: Collegamento,
        val area: Area,
        val fonti: List<Fonte>,
        val note: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    val voci: List<Voce> by lazy { json.decodeFromString<List<Voce>>(leggiJson()) }

    fun perData(giorno: Int, mese: Int): List<Fatto> =
        voci.filter { it.giorno == giorno && it.mese == mese }.map { v ->
            Fatto(
                id = "locale-${v.id}",
                titolo = v.titolo,
                testo = v.testo,
                giorno = v.giorno,
                mese = v.mese,
                anno = v.anno,
                collegamento = v.collegamento,
                categoria = v.categoria,
                area = v.area,
                fonti = v.fonti,
                verifica = LivelloVerifica.ALTO,
                origine = "Archivio dell'app, controllato su fonte istituzionale",
                note = v.note + listOf("Disponibile anche senza connessione"),
            )
        }
}
