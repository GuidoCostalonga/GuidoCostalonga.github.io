package org.costalonga.polso.dati

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.IntervalloSonno
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.SessioneSonno
import org.costalonga.polso.motore.VoceDiario
import org.costalonga.polso.motore.importa.RisultatoImportazione

data class EsitoScrittura(val nuovi: Int, val aggiornati: Int, val eliminati: Int = 0)

/** Unico punto di accesso all'archivio: converte fra entità Room e modelli del motore. */
class Archivio(val db: PolsoDb) {
    val dao = db.dao()
    private val codec = Json { ignoreUnknownKeys = true }
    private val fasiSer = ListSerializer(IntervalloSonno.serializer())

    private fun MisuraEntita.modello() = Misura(metrica, inizio, fine, valore, unita, scartoSec, fonte, origine, dispositivo, idEsterno, modificataIl)
    private fun Misura.entita(lotto: Long, id: Long = 0) = MisuraEntita(id, chiave(), metrica, inizio, fine, valore, unita, scartoSec, fonte, origine, dispositivo, idEsterno, modificataIl, lotto)
    private fun SonnoEntita.modello() = SessioneSonno(inizio, fine, scartoSec, fonte, origine, idEsterno, codec.decodeFromString(fasiSer, fasi))
    private fun SessioneSonno.entita(lotto: Long, id: Long = 0) = SonnoEntita(id, chiave(), inizio, fine, scartoSec, fonte, origine, idEsterno, codec.encodeToString(fasiSer, fasi), lotto)
    private fun AllenamentoEntita.modello() = codec.decodeFromString(Allenamento.serializer(), this.json)
    private fun Allenamento.entita(lotto: Long, id: Long = 0) = AllenamentoEntita(id, chiave(), codec.encodeToString(Allenamento.serializer(), this), inizio, fonte, idEsterno, lotto)
    fun DiarioEntita.modello() = VoceDiario(id, tipo, istante, valore, valore2, testo, creataIl, modificataIl)

    /**
     * Scrive un'importazione. Le misure con la stessa chiave vengono
     * aggiornate (importazione ripetuta o record modificato alla fonte),
     * quelle nuove inserite. Restituisce quanti nuovi e quanti aggiornati.
     */
    suspend fun scrivi(r: RisultatoImportazione, lotto: Long): EsitoScrittura {
        var nuovi = 0
        var aggiornati = 0
        // Doppioni dentro lo stesso arrivo: vale l'ultimo.
        r.misure.associateBy { it.chiave() }.values.chunked(500).forEach { blocco ->
            val esistenti = dao.chiaviMisure(blocco.map { it.chiave() }).associate { it.chiave to it.id }
            aggiornati += esistenti.size
            nuovi += blocco.size - esistenti.size
            dao.upsertMisure(blocco.map { it.entita(lotto, esistenti[it.chiave()] ?: 0) })
        }
        r.sonni.associateBy { it.chiave() }.values.chunked(200).forEach { blocco ->
            val esistenti = dao.chiaviSonno(blocco.map { it.chiave() }).associate { it.chiave to it.id }
            aggiornati += esistenti.size
            nuovi += blocco.size - esistenti.size
            dao.upsertSonno(blocco.map { it.entita(lotto, esistenti[it.chiave()] ?: 0) })
        }
        r.allenamenti.associateBy { it.chiave() }.values.chunked(200).forEach { blocco ->
            val esistenti = dao.chiaviAllenamenti(blocco.map { it.chiave() }).associate { it.chiave to it.id }
            aggiornati += esistenti.size
            nuovi += blocco.size - esistenti.size
            dao.upsertAllenamenti(blocco.map { it.entita(lotto, esistenti[it.chiave()] ?: 0) })
        }
        return EsitoScrittura(nuovi, aggiornati)
    }

    /** Elimina ciò che la fonte dichiara cancellato (stesso identificativo). */
    suspend fun elimina(fonte: String, idEsterno: String): Int =
        dao.eliminaMisure(fonte, idEsterno) + dao.eliminaSonno(fonte, idEsterno) + dao.eliminaAllenamento(fonte, idEsterno)

    suspend fun misure(da: Long, a: Long): List<Misura> = dao.misure(da, a).map { it.modello() }
    suspend fun misureDi(metriche: List<String>): List<Misura> = dao.misureDi(metriche).map { it.modello() }
    suspend fun sonni(da: Long, a: Long): List<SessioneSonno> = dao.sonno(da, a).map { it.modello() }
    suspend fun allenamenti(da: Long, a: Long): List<Allenamento> = dao.allenamenti(da, a).map { it.modello() }
    suspend fun diario(): List<VoceDiario> = dao.tuttoIlDiario().map { it.modello() }

    suspend fun salvaVoce(v: VoceDiario): Long =
        dao.salvaVoce(DiarioEntita(v.id, v.tipo, v.istante, v.valore, v.valore2, v.testo, v.creataIl, v.modificataIl))
}
