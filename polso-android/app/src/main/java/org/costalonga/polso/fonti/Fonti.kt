package org.costalonga.polso.fonti

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.time.TimeRangeFilter
import org.costalonga.polso.dati.Archivio
import org.costalonga.polso.dati.ArchivioImpostazioni
import org.costalonga.polso.dati.ImportazioneEntita
import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.importa.RisultatoImportazione
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

enum class StatoFonte(val etichetta: String) {
    ATTIVA("Attiva"), DA_CONFIGURARE("Da configurare"), NON_DISPONIBILE("Non disponibile"), NON_IMPLEMENTATA("Non utilizzabile"),
}

/** Descrizione di una fonte per la pagina «Fonti dati». */
data class InfoFonte(
    val id: String,
    val nome: String,
    val stato: StatoFonte,
    val dettaglio: String,
    val passi: List<String> = emptyList(),
)

/**
 * HONOR Health Kit: esiste, ma non è utilizzabile da questa app. Secondo la
 * documentazione ufficiale (developer.honor.com, verificata il 5 ottobre
 * 2026) è riservato a sviluppatori aziendali, solo per la Cina continentale,
 * con revisione di ogni permesso. Non c'è codice di integrazione: questa
 * scheda serve solo a spiegarlo, e l'adattatore resta disattivato.
 */
object HonorHealthKit {
    val info = InfoFonte(
        id = "honor_health_kit",
        nome = "HONOR Health Kit (diretto)",
        stato = StatoFonte.NON_IMPLEMENTATA,
        dettaglio = "Il servizio per sviluppatori di HONOR è aperto solo ad aziende e solo per la Cina continentale, con approvazione di ogni permesso. Non è utilizzabile per un'app personale in Italia: i dati arrivano invece tramite Health Connect.",
        passi = listOf("Nessuna azione possibile: la fonte resta disattivata. Se HONOR aprirà il servizio in Europa, potrà essere aggiunto un adattatore."),
    )
}

/** Accesso a Health Connect, separato per poterlo sostituire nelle prove. */
interface ClientHc {
    suspend fun permessiConcessi(): Set<String>
    suspend fun funzioneDisponibile(funzione: Int): Boolean
    suspend fun <T : Record> leggi(tipo: KClass<T>, da: Instant, a: Instant, pagina: String?): Pair<List<T>, String?>
    suspend fun token(tipi: Set<KClass<out Record>>): String
    suspend fun cambiamenti(token: String): ChangesResponse
    suspend fun aggregatoAllenamento(a: Allenamento, permessi: Set<String>): Allenamento
}

class ClientHcReale(context: Context) : ClientHc {
    private val c = HealthConnectClient.getOrCreate(context)
    override suspend fun permessiConcessi() = c.permissionController.getGrantedPermissions()
    override suspend fun funzioneDisponibile(funzione: Int) = c.features.getFeatureStatus(funzione) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    override suspend fun <T : Record> leggi(tipo: KClass<T>, da: Instant, a: Instant, pagina: String?): Pair<List<T>, String?> {
        val r = c.readRecords(ReadRecordsRequest(tipo, TimeRangeFilter.between(da, a), emptySet(), true, 2000, pagina))
        return r.records to r.pageToken
    }
    override suspend fun token(tipi: Set<KClass<out Record>>) = c.getChangesToken(ChangesTokenRequest(tipi))
    override suspend fun cambiamenti(token: String) = c.getChanges(token)

    /** Distanza, calorie e dislivello della sessione calcolati da Health Connect sulla stessa app d'origine. */
    override suspend fun aggregatoAllenamento(a: Allenamento, permessi: Set<String>): Allenamento {
        val metriche = buildSet {
            if (HealthPermission.getReadPermission(DistanceRecord::class) in permessi) add(DistanceRecord.DISTANCE_TOTAL)
            if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in permessi) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (HealthPermission.getReadPermission(ElevationGainedRecord::class) in permessi) add(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)
        }
        if (metriche.isEmpty()) return a
        val r = c.aggregate(AggregateRequest(metriche, TimeRangeFilter.between(Instant.ofEpochMilli(a.inizio), Instant.ofEpochMilli(a.fine)), setOf(DataOrigin(a.origine))))
        return a.copy(
            distanzaM = a.distanzaM ?: r[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            calorieKcal = a.calorieKcal ?: r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            dislivelloM = a.dislivelloM ?: r[ElevationGainedRecord.ELEVATION_GAINED_TOTAL]?.inMeters,
        )
    }
}

data class StatoHc(
    val sdk: Int,
    val permessi: Set<String>,
    val cronologia: Boolean,
    val secondoPiano: Boolean,
) {
    val disponibile get() = sdk == HealthConnectClient.SDK_AVAILABLE
    val tipiConcessi get() = MappaHc.TIPI.filter { HealthPermission.getReadPermission(it.first) in permessi }
    val cronologiaConcessa get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in permessi
    val secondoPianoConcesso get() = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in permessi
}

data class EsitoSincronizzazione(val nuovi: Int, val aggiornati: Int, val eliminati: Int, val errori: List<String>, val da: Instant?, val a: Instant?) {
    val riuscita get() = errori.isEmpty()
}

/**
 * Sincronizzazione con Health Connect.
 * Prima volta per ogni tipo: si chiede il token delle modifiche e poi si
 * legge tutto lo storico consentito (con il permesso «cronologia» dal 2015,
 * altrimenti gli ultimi 30 giorni, limite imposto da Health Connect). Volte
 * successive: solo le modifiche dal token, comprese le cancellazioni. Token
 * scaduto (dopo 30 giorni di inattività): nuova lettura degli ultimi 30 giorni.
 */
class SincronizzatoreHc(private val client: ClientHc, private val archivio: Archivio, private val impostazioni: ArchivioImpostazioni) {
    companion object {
        val INIZIO_STORICO: Instant = LocalDate.of(2015, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val TUTTI_I_PERMESSI: Set<String> = MappaHc.TIPI.map { HealthPermission.getReadPermission(it.first) }.toSet() +
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY + HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
    }

    suspend fun sincronizza(adesso: Instant = Instant.now(), soloSecondoPiano: Boolean = false): EsitoSincronizzazione {
        val avvio = System.currentTimeMillis()
        val idLog = archivio.dao.nuovaImportazione(ImportazioneEntita(fonte = MappaHc.FONTE, descrizione = if (soloSecondoPiano) "Sincronizzazione automatica" else "Sincronizzazione", avviataIl = avvio, conclusaIl = null, da = null, a = null, nuovi = 0, aggiornati = 0, eliminati = 0, errori = "", esito = "in corso"))
        val errori = mutableListOf<String>()
        var nuovi = 0; var aggiornati = 0; var eliminati = 0
        var minimo: Instant? = null
        val permessi = try { client.permessiConcessi() } catch (e: Exception) { emptySet() }
        if (soloSecondoPiano && HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in permessi) errori += "Lettura in secondo piano non concessa: apri l'app per sincronizzare."
        val cronologia = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in permessi
        val tipi = MappaHc.TIPI.filter { HealthPermission.getReadPermission(it.first) in permessi }
        if (tipi.isEmpty() && errori.isEmpty()) errori += "Nessun permesso di lettura concesso (o permessi revocati) in Health Connect."
        val token = impostazioni.attuali().tokenHc.toMutableMap()
        if (errori.isEmpty()) for ((tipo, nome) in tipi) {
            val chiave = tipo.simpleName ?: continue
            try {
                val t = token[chiave]
                if (t == null) {
                    token[chiave] = client.token(setOf(tipo))
                    val da = if (cronologia) INIZIO_STORICO else adesso.minus(30, ChronoUnit.DAYS)
                    minimo = minOf(minimo ?: da, da)
                    val e = leggiTutto(tipo, da, adesso, permessi)
                    nuovi += e.first; aggiornati += e.second
                } else {
                    var corrente: String = t
                    var scaduto = false
                    do {
                        val r = client.cambiamenti(corrente)
                        if (r.changesTokenExpired) { scaduto = true; break }
                        val inseriti = r.changes.filterIsInstance<UpsertionChange>().map { it.record }
                        if (inseriti.isNotEmpty()) {
                            val esito = archivio.scrivi(completa(MappaHc.converti(inseriti), permessi), System.currentTimeMillis())
                            nuovi += esito.nuovi; aggiornati += esito.aggiornati
                        }
                        r.changes.filterIsInstance<DeletionChange>().forEach { eliminati += archivio.elimina(MappaHc.FONTE, it.recordId) }
                        corrente = r.nextChangesToken
                    } while (r.hasMore)
                    if (scaduto) {
                        token[chiave] = client.token(setOf(tipo))
                        val da = adesso.minus(30, ChronoUnit.DAYS)
                        minimo = minOf(minimo ?: da, da)
                        val e = leggiTutto(tipo, da, adesso, permessi)
                        nuovi += e.first; aggiornati += e.second
                    } else token[chiave] = corrente
                }
            } catch (e: SecurityException) {
                errori += "$nome: permesso revocato o non concesso."
                token.remove(chiave)
            } catch (e: Exception) {
                errori += "$nome: ${e.javaClass.simpleName}${e.message?.let { ": " + it.take(120) } ?: ""}"
            }
        }
        impostazioni.modifica { it.copy(tokenHc = token, primaSincronizzazioneHc = it.primaSincronizzazioneHc ?: avvio) }
        val esito = EsitoSincronizzazione(nuovi, aggiornati, eliminati, errori, minimo, adesso)
        archivio.dao.aggiornaImportazione(ImportazioneEntita(idLog, MappaHc.FONTE, if (soloSecondoPiano) "Sincronizzazione automatica" else "Sincronizzazione", avvio, System.currentTimeMillis(),
            minimo?.toEpochMilli(), adesso.toEpochMilli(), nuovi, aggiornati, eliminati, errori.joinToString("\n"),
            when { errori.isEmpty() -> "riuscita"; nuovi + aggiornati + eliminati > 0 -> "parziale"; else -> "non riuscita" }))
        return esito
    }

    private suspend fun leggiTutto(tipo: KClass<out Record>, da: Instant, a: Instant, permessi: Set<String>): Pair<Int, Int> {
        var pagina: String? = null
        var n = 0; var agg = 0
        do {
            val (record, prossima) = client.leggi(tipo, da, a, pagina)
            if (record.isNotEmpty()) {
                val e = archivio.scrivi(completa(MappaHc.converti(record), permessi), System.currentTimeMillis())
                n += e.nuovi; agg += e.aggiornati
            }
            pagina = prossima?.takeIf { it.isNotEmpty() }
        } while (pagina != null)
        return n to agg
    }

    private suspend fun completa(r: RisultatoImportazione, permessi: Set<String>): RisultatoImportazione =
        if (r.allenamenti.isEmpty()) r else r.copy(allenamenti = r.allenamenti.map { a -> runCatching { client.aggregatoAllenamento(a, permessi) }.getOrDefault(a) })
}
