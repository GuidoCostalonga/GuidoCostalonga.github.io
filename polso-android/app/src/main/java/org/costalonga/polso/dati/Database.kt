package org.costalonga.polso.dati

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Archivio locale. I dati originali (misure, sonno, allenamenti) e il diario
 * manuale stanno in tabelle distinte da quelle dei risultati calcolati
 * (riepiloghi). «chiave» identifica la stessa registrazione fra importazioni
 * ripetute: un nuovo arrivo con la stessa chiave aggiorna, non duplica.
 */
@Entity(tableName = "misure", indices = [Index(value = ["chiave"], unique = true), Index("metrica", "inizio"), Index("idEsterno")])
data class MisuraEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chiave: String,
    val metrica: String,
    val inizio: Long,
    val fine: Long,
    val valore: Double,
    val unita: String,
    val scartoSec: Int?,
    val fonte: String,
    val origine: String,
    val dispositivo: String,
    val idEsterno: String?,
    val modificataIl: Long?,
    val lotto: Long,
)

@Entity(tableName = "sonno", indices = [Index(value = ["chiave"], unique = true), Index("fine"), Index("idEsterno")])
data class SonnoEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chiave: String,
    val inizio: Long,
    val fine: Long,
    val scartoSec: Int?,
    val fonte: String,
    val origine: String,
    val idEsterno: String?,
    /** Fasi in JSON (elenco di IntervalloSonno). */
    val fasi: String,
    val lotto: Long,
)

@Entity(tableName = "allenamenti", indices = [Index(value = ["chiave"], unique = true), Index("inizio"), Index("idEsterno")])
data class AllenamentoEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chiave: String,
    /** L'allenamento completo in JSON (percorso compreso). */
    val json: String,
    val inizio: Long,
    val fonte: String,
    val idEsterno: String?,
    val lotto: Long,
)

@Entity(tableName = "diario", indices = [Index("istante")])
data class DiarioEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val istante: Long,
    val valore: Double?,
    val valore2: Double?,
    val testo: String,
    val creataIl: Long,
    val modificataIl: Long,
)

/** Registro di ogni sincronizzazione o importazione, riuscita o no. */
@Entity(tableName = "importazioni")
data class ImportazioneEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fonte: String,
    val descrizione: String,
    val avviataIl: Long,
    val conclusaIl: Long?,
    val da: Long?,
    val a: Long?,
    val nuovi: Int,
    val aggiornati: Int,
    val eliminati: Int,
    val errori: String,
    val esito: String,
)

/** Risultati calcolati: riepiloghi dell'IA o deterministici, separati dai dati. */
@Entity(tableName = "riepiloghi", indices = [Index("tipo", "da")])
data class RiepilogoEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val da: String,
    val a: String,
    val testo: String,
    val motore: String,
    val verificato: Boolean,
    val fatti: String,
    val creatoIl: Long,
)

@Entity(tableName = "notifiche")
data class NotificaEntita(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val regola: String,
    val titolo: String,
    val testo: String,
    val motivo: String,
    val inviataIl: Long,
)

data class Conteggio(val metrica: String, val n: Int, val primo: Long?, val ultimo: Long?)
data class ConteggioOrigine(val origine: String, val n: Int)

@Dao
interface ArchivioDao {
    @Query("SELECT id, chiave FROM misure WHERE chiave IN (:chiavi)")
    suspend fun chiaviMisure(chiavi: List<String>): List<IdChiave>

    @Upsert suspend fun upsertMisure(m: List<MisuraEntita>)
    @Upsert suspend fun upsertSonno(s: List<SonnoEntita>)
    @Upsert suspend fun upsertAllenamenti(a: List<AllenamentoEntita>)

    @Query("SELECT id, chiave FROM sonno WHERE chiave IN (:chiavi)") suspend fun chiaviSonno(chiavi: List<String>): List<IdChiave>
    @Query("SELECT id, chiave FROM allenamenti WHERE chiave IN (:chiavi)") suspend fun chiaviAllenamenti(chiavi: List<String>): List<IdChiave>

    @Query("SELECT * FROM misure WHERE fine >= :da AND inizio < :a ORDER BY inizio")
    suspend fun misure(da: Long, a: Long): List<MisuraEntita>

    @Query("SELECT * FROM misure WHERE metrica IN (:metriche) ORDER BY inizio")
    suspend fun misureDi(metriche: List<String>): List<MisuraEntita>

    @Query("SELECT * FROM sonno WHERE fine >= :da AND inizio < :a ORDER BY inizio") suspend fun sonno(da: Long, a: Long): List<SonnoEntita>
    @Query("SELECT * FROM allenamenti WHERE inizio >= :da AND inizio < :a ORDER BY inizio") suspend fun allenamenti(da: Long, a: Long): List<AllenamentoEntita>

    @Query("DELETE FROM misure WHERE fonte = :fonte AND (idEsterno = :id OR idEsterno LIKE :id || '#%')") suspend fun eliminaMisure(fonte: String, id: String): Int
    @Query("DELETE FROM sonno WHERE fonte = :fonte AND idEsterno = :id") suspend fun eliminaSonno(fonte: String, id: String): Int
    @Query("DELETE FROM allenamenti WHERE fonte = :fonte AND idEsterno = :id") suspend fun eliminaAllenamento(fonte: String, id: String): Int

    @Query("SELECT metrica, COUNT(*) AS n, MIN(inizio) AS primo, MAX(fine) AS ultimo FROM misure GROUP BY metrica")
    suspend fun conteggi(): List<Conteggio>

    @Query("SELECT origine, COUNT(*) AS n FROM misure GROUP BY origine ORDER BY n DESC")
    suspend fun origini(): List<ConteggioOrigine>

    @Query("SELECT COUNT(*) FROM sonno") suspend fun numeroSonni(): Int
    @Query("SELECT COUNT(*) FROM allenamenti") suspend fun numeroAllenamenti(): Int
    @Query("SELECT MAX(fine) FROM misure") suspend fun ultimaMisura(): Long?

    // Diario
    @Query("SELECT * FROM diario ORDER BY istante DESC") fun diario(): Flow<List<DiarioEntita>>
    @Query("SELECT * FROM diario ORDER BY istante") suspend fun tuttoIlDiario(): List<DiarioEntita>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvaVoce(v: DiarioEntita): Long
    @Insert suspend fun inserisciVoci(v: List<DiarioEntita>)
    @Query("DELETE FROM diario WHERE id = :id") suspend fun eliminaVoce(id: Long)

    // Registro
    @Insert suspend fun nuovaImportazione(i: ImportazioneEntita): Long
    @Upsert suspend fun aggiornaImportazione(i: ImportazioneEntita)
    @Query("SELECT * FROM importazioni ORDER BY avviataIl DESC LIMIT :n") fun registro(n: Int = 50): Flow<List<ImportazioneEntita>>
    @Query("SELECT * FROM importazioni WHERE fonte = :fonte AND esito = 'riuscita' ORDER BY conclusaIl DESC LIMIT 1") suspend fun ultimaRiuscita(fonte: String): ImportazioneEntita?

    // Riepiloghi
    @Insert suspend fun salvaRiepilogo(r: RiepilogoEntita): Long
    @Query("SELECT * FROM riepiloghi ORDER BY creatoIl DESC LIMIT 100") fun riepiloghi(): Flow<List<RiepilogoEntita>>
    @Query("SELECT * FROM riepiloghi ORDER BY creatoIl") suspend fun tuttiIRiepiloghi(): List<RiepilogoEntita>
    @Query("DELETE FROM riepiloghi WHERE id = :id") suspend fun eliminaRiepilogo(id: Long)

    // Notifiche
    @Insert suspend fun registraNotifica(n: NotificaEntita)
    @Query("SELECT * FROM notifiche ORDER BY inviataIl DESC LIMIT 50") fun notifiche(): Flow<List<NotificaEntita>>
    @Query("SELECT regola, MAX(inviataIl) AS ultimo FROM notifiche GROUP BY regola") suspend fun ultimeNotifiche(): List<UltimaNotifica>

    // Cancellazioni complete
    @Query("DELETE FROM misure") suspend fun svuotaMisure()
    @Query("DELETE FROM sonno") suspend fun svuotaSonno()
    @Query("DELETE FROM allenamenti") suspend fun svuotaAllenamenti()
    @Query("DELETE FROM diario") suspend fun svuotaDiario()
    @Query("DELETE FROM importazioni") suspend fun svuotaRegistro()
    @Query("DELETE FROM riepiloghi") suspend fun svuotaRiepiloghi()
    @Query("DELETE FROM notifiche") suspend fun svuotaNotifiche()

    @Transaction
    suspend fun svuotaTutto() {
        svuotaMisure(); svuotaSonno(); svuotaAllenamenti(); svuotaDiario(); svuotaRegistro(); svuotaRiepiloghi(); svuotaNotifiche()
    }
}

data class IdChiave(val id: Long, val chiave: String)
data class UltimaNotifica(val regola: String, val ultimo: Long)

@Database(
    entities = [MisuraEntita::class, SonnoEntita::class, AllenamentoEntita::class, DiarioEntita::class, ImportazioneEntita::class, RiepilogoEntita::class, NotificaEntita::class],
    version = PolsoDb.VERSIONE,
    exportSchema = true,
)
abstract class PolsoDb : RoomDatabase() {
    abstract fun dao(): ArchivioDao

    companion object {
        const val VERSIONE = 1
        const val NOME = "polso.db"
        /** Archivio separato per la modalità dimostrativa: i dati sintetici non toccano quelli veri. */
        const val NOME_DEMO = "polso_demo.db"

        fun apri(context: Context, nome: String): PolsoDb =
            Room.databaseBuilder(context.applicationContext, PolsoDb::class.java, nome).build()
    }
}
