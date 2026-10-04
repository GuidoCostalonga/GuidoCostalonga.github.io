package org.costalonga.sportintv.dati

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Cache degli eventi: una riga per evento, con le colonne utili alle query e
 * l'evento completo in JSON. Viene sostituita in blocco a ogni aggiornamento
 * riuscito, così sul telefono resta sempre l'ultimo pacchetto valido.
 */
@Entity(tableName = "eventi")
data class EventoRiga(
    @PrimaryKey val id: String,
    val inizio: Long,
    val sport: String,
    val verificata: Boolean,
    val json: String,
)

@Entity(tableName = "fonti")
data class FonteRiga(
    @PrimaryKey val id: String,
    val ordine: Int,
    val json: String,
)

/** Squadra, atleta o competizione seguiti. */
@Entity(tableName = "seguiti", primaryKeys = ["tipo", "nome"])
data class Seguito(
    val tipo: TipoSeguito,
    val nome: String,
    val aggiunto: Long = System.currentTimeMillis(),
)

enum class TipoSeguito(val etichetta: String) {
    SQUADRA("Squadra"),
    ATLETA("Atleta"),
    COMPETIZIONE("Competizione"),
}

/** Evento salvato singolarmente. [impronta] serve a ritrovarlo se cambia giorno. */
@Entity(tableName = "salvati")
data class Salvato(
    @PrimaryKey val eventoId: String,
    val impronta: String,
    val titolo: String,
    val inizio: Long,
)

/**
 * Promemoria pianificato. [automatico] vale per quelli creati dai preferiti;
 * [notificato] evita di ripetere un avviso già mostrato.
 */
@Entity(tableName = "promemoria")
data class Promemoria(
    @PrimaryKey val eventoId: String,
    val impronta: String,
    val titolo: String,
    val inizio: Long,
    val allarme: Long,
    val automatico: Boolean,
    val notificato: Boolean = false,
)

@Dao
interface EventiDao {
    @Query("SELECT * FROM eventi ORDER BY inizio")
    fun tutti(): Flow<List<EventoRiga>>

    @Query("SELECT * FROM eventi ORDER BY inizio")
    suspend fun elenco(): List<EventoRiga>

    @Query("SELECT * FROM eventi WHERE id = :id")
    fun uno(id: String): Flow<EventoRiga?>

    @Query("SELECT * FROM fonti ORDER BY ordine")
    fun fonti(): Flow<List<FonteRiga>>

    @Query("SELECT * FROM fonti ORDER BY ordine")
    suspend fun elencoFonti(): List<FonteRiga>

    @Query("DELETE FROM eventi")
    suspend fun svuotaEventi()

    @Query("DELETE FROM fonti")
    suspend fun svuotaFonti()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciEventi(righe: List<EventoRiga>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserisciFonti(righe: List<FonteRiga>)

    @Transaction
    suspend fun sostituisci(eventi: List<EventoRiga>, fonti: List<FonteRiga>) {
        svuotaEventi()
        svuotaFonti()
        inserisciEventi(eventi)
        inserisciFonti(fonti)
    }
}

@Dao
interface PreferitiDao {
    @Query("SELECT * FROM seguiti ORDER BY tipo, nome")
    fun seguiti(): Flow<List<Seguito>>

    @Query("SELECT * FROM seguiti")
    suspend fun elencoSeguiti(): List<Seguito>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun segui(s: Seguito)

    @Query("DELETE FROM seguiti WHERE tipo = :tipo AND nome = :nome")
    suspend fun smetti(tipo: TipoSeguito, nome: String)

    @Query("SELECT * FROM salvati ORDER BY inizio")
    fun salvati(): Flow<List<Salvato>>

    @Query("SELECT * FROM salvati")
    suspend fun elencoSalvati(): List<Salvato>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salva(s: Salvato)

    @Query("DELETE FROM salvati WHERE eventoId = :id")
    suspend fun togliSalvato(id: String)

    @Query("SELECT * FROM promemoria ORDER BY allarme")
    fun promemoria(): Flow<List<Promemoria>>

    @Query("SELECT * FROM promemoria")
    suspend fun elencoPromemoria(): List<Promemoria>

    @Query("SELECT * FROM promemoria WHERE eventoId = :id")
    suspend fun promemoriaDi(id: String): Promemoria?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvaPromemoria(p: Promemoria)

    @Query("DELETE FROM promemoria WHERE eventoId = :id")
    suspend fun togliPromemoria(id: String)
}

@Database(entities = [EventoRiga::class, FonteRiga::class, Seguito::class, Salvato::class, Promemoria::class], version = 1, exportSchema = true)
abstract class SportDatabase : RoomDatabase() {
    abstract fun eventi(): EventiDao
    abstract fun preferiti(): PreferitiDao

    companion object {
        fun crea(context: Context): SportDatabase =
            Room.databaseBuilder(context, SportDatabase::class.java, "sportintv.db").build()
    }
}
