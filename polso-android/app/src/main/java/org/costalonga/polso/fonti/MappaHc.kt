package org.costalonga.polso.fonti

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.FaseSonno
import org.costalonga.polso.motore.IntervalloSonno
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.PuntoPercorso
import org.costalonga.polso.motore.SessioneSonno
import org.costalonga.polso.motore.importa.RisultatoImportazione
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * Traduzione dei record di Health Connect nei modelli di Polso. Nessuna
 * conversione inventa dati: un campo assente resta assente.
 */
object MappaHc {
    const val FONTE = "health_connect"

    /** Tipi letti, con il permesso e l'etichetta mostrata all'utente. */
    val TIPI: List<Pair<KClass<out Record>, String>> = listOf(
        StepsRecord::class to "Passi", DistanceRecord::class to "Distanza", ActiveCaloriesBurnedRecord::class to "Calorie attive",
        TotalCaloriesBurnedRecord::class to "Calorie totali", FloorsClimbedRecord::class to "Piani", ElevationGainedRecord::class to "Dislivello",
        HeartRateRecord::class to "Frequenza cardiaca", RestingHeartRateRecord::class to "Frequenza a riposo",
        HeartRateVariabilityRmssdRecord::class to "HRV (RMSSD)", OxygenSaturationRecord::class to "SpO₂",
        RespiratoryRateRecord::class to "Respirazione", SkinTemperatureRecord::class to "Temperatura cutanea",
        BodyTemperatureRecord::class to "Temperatura corporea", SleepSessionRecord::class to "Sonno",
        ExerciseSessionRecord::class to "Allenamenti", WeightRecord::class to "Peso", BodyFatRecord::class to "Massa grassa",
        BloodPressureRecord::class to "Pressione", Vo2MaxRecord::class to "VO₂max",
    )

    fun nomeTipo(k: KClass<out Record>): String = TIPI.firstOrNull { it.first == k }?.second ?: (k.simpleName ?: "?")

    private fun Metadata.origine() = dataOrigin.packageName
    private fun Metadata.disp() = device?.let { listOfNotNull(it.manufacturer, it.model).joinToString(" ") }.orEmpty()

    private fun intervallo(m: Metrica, r: Record, ini: Instant, fine: Instant, off: ZoneOffset?, v: Double, md: Metadata) =
        Misura(m.codice, ini.toEpochMilli(), fine.toEpochMilli(), v, m.unita, off?.totalSeconds, FONTE, md.origine(), md.disp(), md.id, md.lastModifiedTime.toEpochMilli())

    private fun istante(m: Metrica, t: Instant, off: ZoneOffset?, v: Double, md: Metadata, id: String = md.id) =
        Misura(m.codice, t.toEpochMilli(), t.toEpochMilli(), v, m.unita, off?.totalSeconds, FONTE, md.origine(), md.disp(), id, md.lastModifiedTime.toEpochMilli())

    fun fase(stage: Int): FaseSonno = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> FaseSonno.SVEGLIO
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> FaseSonno.FUORI_LETTO
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> FaseSonno.SONNO
        SleepSessionRecord.STAGE_TYPE_LIGHT -> FaseSonno.LEGGERO
        SleepSessionRecord.STAGE_TYPE_DEEP -> FaseSonno.PROFONDO
        SleepSessionRecord.STAGE_TYPE_REM -> FaseSonno.REM
        else -> FaseSonno.SCONOSCIUTO
    }

    fun sport(tipo: Int): String = when (tipo) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "camminata"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "corsa"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "corsa_tapis"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "ciclismo"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "cyclette"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "nuoto_piscina"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "nuoto_libero"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "escursionismo"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "ellittica"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "vogatore"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "forza"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "pilates"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "calcio"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "tennis"
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "sci"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "ballo"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "hiit"
        else -> "altro"
    }

    /** Converte un elenco di record (di qualsiasi tipo) in un risultato importabile. */
    fun converti(record: List<Record>): RisultatoImportazione {
        val misure = ArrayList<Misura>()
        val sonni = ArrayList<SessioneSonno>()
        val allenamenti = ArrayList<Allenamento>()
        for (r in record) when (r) {
            is StepsRecord -> misure += intervallo(Metrica.PASSI, r, r.startTime, r.endTime, r.startZoneOffset, r.count.toDouble(), r.metadata)
            is DistanceRecord -> misure += intervallo(Metrica.DISTANZA, r, r.startTime, r.endTime, r.startZoneOffset, r.distance.inMeters, r.metadata)
            is ActiveCaloriesBurnedRecord -> misure += intervallo(Metrica.CALORIE_ATTIVE, r, r.startTime, r.endTime, r.startZoneOffset, r.energy.inKilocalories, r.metadata)
            is TotalCaloriesBurnedRecord -> misure += intervallo(Metrica.CALORIE_TOTALI, r, r.startTime, r.endTime, r.startZoneOffset, r.energy.inKilocalories, r.metadata)
            is FloorsClimbedRecord -> misure += intervallo(Metrica.PIANI, r, r.startTime, r.endTime, r.startZoneOffset, r.floors, r.metadata)
            is ElevationGainedRecord -> misure += intervallo(Metrica.DISLIVELLO, r, r.startTime, r.endTime, r.startZoneOffset, r.elevation.inMeters, r.metadata)
            is HeartRateRecord -> r.samples.forEach { s ->
                misure += istante(Metrica.FREQUENZA_CARDIACA, s.time, r.startZoneOffset, s.beatsPerMinute.toDouble(), r.metadata, "${r.metadata.id}#${s.time.toEpochMilli()}")
            }
            is RestingHeartRateRecord -> misure += istante(Metrica.FC_RIPOSO, r.time, r.zoneOffset, r.beatsPerMinute.toDouble(), r.metadata)
            is HeartRateVariabilityRmssdRecord -> misure += istante(Metrica.HRV_RMSSD, r.time, r.zoneOffset, r.heartRateVariabilityMillis, r.metadata)
            is OxygenSaturationRecord -> misure += istante(Metrica.SPO2, r.time, r.zoneOffset, r.percentage.value, r.metadata)
            is RespiratoryRateRecord -> misure += istante(Metrica.RESPIRAZIONE, r.time, r.zoneOffset, r.rate, r.metadata)
            is SkinTemperatureRecord -> r.deltas.forEach { d ->
                misure += istante(Metrica.TEMPERATURA_CUTANEA, d.time, r.startZoneOffset, d.delta.inCelsius, r.metadata, "${r.metadata.id}#${d.time.toEpochMilli()}")
            }
            is BodyTemperatureRecord -> misure += istante(Metrica.TEMPERATURA_CORPOREA, r.time, r.zoneOffset, r.temperature.inCelsius, r.metadata)
            is WeightRecord -> misure += istante(Metrica.PESO, r.time, r.zoneOffset, r.weight.inKilograms, r.metadata)
            is BodyFatRecord -> misure += istante(Metrica.GRASSO, r.time, r.zoneOffset, r.percentage.value, r.metadata)
            is Vo2MaxRecord -> misure += istante(Metrica.VO2MAX, r.time, r.zoneOffset, r.vo2MillilitersPerMinuteKilogram, r.metadata)
            is BloodPressureRecord -> {
                misure += istante(Metrica.PRESSIONE_SISTOLICA, r.time, r.zoneOffset, r.systolic.inMillimetersOfMercury, r.metadata, "${r.metadata.id}#s")
                misure += istante(Metrica.PRESSIONE_DIASTOLICA, r.time, r.zoneOffset, r.diastolic.inMillimetersOfMercury, r.metadata, "${r.metadata.id}#d")
            }
            is SleepSessionRecord -> sonni += SessioneSonno(
                r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.endZoneOffset?.totalSeconds ?: r.startZoneOffset?.totalSeconds,
                FONTE, r.metadata.origine(), r.metadata.id,
                r.stages.map { IntervalloSonno(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), fase(it.stage).codice) },
                r.title.orEmpty(),
            )
            is ExerciseSessionRecord -> {
                val percorso = (r.exerciseRouteResult as? ExerciseRouteResult.Data)?.exerciseRoute?.route?.map {
                    PuntoPercorso(it.time.toEpochMilli(), it.latitude, it.longitude, it.altitude?.inMeters)
                }.orEmpty()
                allenamenti += Allenamento(
                    r.startTime.toEpochMilli(), r.endTime.toEpochMilli(), r.startZoneOffset?.totalSeconds, sport(r.exerciseType),
                    r.title.orEmpty(), FONTE, r.metadata.origine(), r.metadata.id, percorso = percorso,
                )
            }
            else -> {}
        }
        return RisultatoImportazione(misure, sonni, allenamenti, record.size)
    }
}
