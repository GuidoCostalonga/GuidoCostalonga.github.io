package org.costalonga.polso.motore

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dati SINTETICI per la modalità dimostrativa e per le prove automatiche.
 * Non rappresentano nessuna persona reale: sono generati con un seme fisso.
 * Fonte "demo", origine "dati dimostrativi".
 */
object Demo {
    const val FONTE = "demo"
    const val ORIGINE = "dati dimostrativi"

    data class Archivio(val misure: List<Misura>, val sonni: List<SessioneSonno>, val allenamenti: List<Allenamento>, val diario: List<VoceDiario>)

    fun genera(fine: LocalDate, giorni: Int = 120, zona: ZoneId = Tempo.ROMA, seme: Int = 42): Archivio {
        val r = Random(seme)
        val misure = ArrayList<Misura>()
        val sonni = ArrayList<SessioneSonno>()
        val allenamenti = ArrayList<Allenamento>()
        val diario = ArrayList<VoceDiario>()
        val inizio = fine.minusDays(giorni - 1L)
        var peso = 82.0
        for ((i, g) in Tempo.giorni(inizio, fine).withIndex()) {
            val g0 = Tempo.inizioGiorno(g, zona)
            // Qualche giorno senza orologio, per mostrare i mancanti.
            if (r.nextDouble() < 0.04) continue
            val weekend = Tempo.weekend(g)
            val livello = 0.8 + 0.25 * sin(2 * PI * i / 60.0) + if (weekend) 0.15 else 0.0
            // Sonno della notte che termina oggi, con fasi a cicli di 90 minuti.
            val addorm = g0 - ((if (weekend) 60 else 90) + r.nextInt(-40, 40)) * 60_000L
            val durata = ((if (weekend) 470 else 420) + r.nextInt(-60, 50)) * 60_000L
            val fasi = ArrayList<IntervalloSonno>()
            var t = addorm
            while (t < addorm + durata) {
                val sequenza = listOf("leggero" to 35, "profondo" to (25 - fasi.size), "leggero" to 15, "rem" to (15 + fasi.size), "sveglio" to (if (r.nextDouble() < 0.3) 4 else 0))
                for ((fase, min) in sequenza) {
                    if (min <= 0) continue
                    val f = minOf(t + max(5, min + r.nextInt(-5, 6)) * 60_000L, addorm + durata)
                    if (f > t) fasi += IntervalloSonno(t, f, fase)
                    t = f
                }
            }
            sonni += SessioneSonno(addorm, addorm + durata, null, FONTE, ORIGINE, "demo-sonno-$g", fasi)
            // Passi per ora tra le 7 e le 22.
            for (h in 7..22) {
                val base = when (h) { 8, 13, 18 -> 900.0; in 9..17 -> 450.0; else -> 250.0 } * livello
                val v = max(0.0, base + r.nextDouble(-200.0, 200.0)).toInt()
                if (v > 0) {
                    val a = g0 + h * 3_600_000L
                    misure += Misura(Metrica.PASSI.codice, a, a + 3_600_000L, v.toDouble(), "passi", null, FONTE, ORIGINE, idEsterno = "demo-passi-$g-$h")
                    misure += Misura(Metrica.DISTANZA.codice, a, a + 3_600_000L, v * 0.74, "m", null, FONTE, ORIGINE, idEsterno = "demo-dist-$g-$h")
                    misure += Misura(Metrica.CALORIE_ATTIVE.codice, a, a + 3_600_000L, v * 0.04, "kcal", null, FONTE, ORIGINE, idEsterno = "demo-kcal-$g-$h")
                }
            }
            // Frequenza cardiaca ogni 10 minuti (più fitta di notte non serve).
            val riposo = 58 + 3 * sin(2 * PI * i / 45.0) + r.nextDouble(-1.5, 1.5)
            var s = addorm
            while (s < g0 + 86_400_000L) {
                val dormendo = s < addorm + durata
                val v = if (dormendo) riposo - 4 + r.nextDouble(-3.0, 3.0) else riposo + 18 + r.nextDouble(-8.0, 15.0)
                misure += Misura(Metrica.FREQUENZA_CARDIACA.codice, s, s, v, "bpm", null, FONTE, ORIGINE, idEsterno = "demo-fc-$s")
                s += 10 * 60_000L
            }
            misure += Misura(Metrica.FC_RIPOSO.codice, g0 + 12 * 3_600_000L, g0 + 12 * 3_600_000L, riposo.toInt().toDouble(), "bpm", null, FONTE, ORIGINE, idEsterno = "demo-riposo-$g")
            if (r.nextDouble() < 0.7) misure += Misura(Metrica.SPO2.codice, g0 + 3 * 3_600_000L, g0 + 3 * 3_600_000L, 96.0 + r.nextInt(-2, 3), "%", null, FONTE, ORIGINE, idEsterno = "demo-spo2-$g")
            misure += Misura(Metrica.STRESS.codice, g0 + 15 * 3_600_000L, g0 + 15 * 3_600_000L, (30 + r.nextInt(-12, 20)).toDouble(), "punti", null, FONTE, ORIGINE, idEsterno = "demo-stress-$g")
            // Corsa due o tre volte a settimana, con campioni cardiaci fitti.
            if (g.dayOfWeek.value in setOf(2, 4, 7) && r.nextDouble() < 0.85) {
                val a = g0 + (if (weekend) 9 else 18) * 3_600_000L
                val min = 30 + r.nextInt(0, 25)
                val dist = min * 60.0 / (360 - i * 0.2 + r.nextDouble(-15.0, 15.0)) * 1000
                allenamenti += Allenamento(a, a + min * 60_000L, null, "corsa", "Corsa", FONTE, ORIGINE, "demo-corsa-$g", distanzaM = dist, calorieKcal = min * 10.5)
                var tt = a
                while (tt <= a + min * 60_000L + 120_000L) {
                    val dopo = tt > a + min * 60_000L
                    val v = if (dopo) 150.0 - (tt - a - min * 60_000L) / 1000.0 * 0.35 else 125 + 25 * minOf(1.0, (tt - a) / 600_000.0) + r.nextDouble(-4.0, 4.0)
                    misure += Misura(Metrica.FREQUENZA_CARDIACA.codice, tt, tt, v, "bpm", null, FONTE, ORIGINE, idEsterno = "demo-fcall-$tt")
                    tt += 10_000L
                }
            }
            if (i % 7 == 0) {
                peso -= 0.15 + r.nextDouble(-0.2, 0.2)
                diario += VoceDiario(0, TipoDiario.PESO.codice, g0 + 7 * 3_600_000L, (peso * 10).toInt() / 10.0, null, "", g0, g0)
            }
            if (r.nextDouble() < 0.5) diario += VoceDiario(0, TipoDiario.CAFFEINA.codice, g0 + 9 * 3_600_000L, r.nextInt(1, 4).toDouble(), null, "", g0, g0)
            if (r.nextDouble() < 0.6) diario += VoceDiario(0, TipoDiario.UMORE.codice, g0 + 21 * 3_600_000L, r.nextInt(2, 6).toDouble(), null, "", g0, g0)
        }
        return Archivio(misure, sonni, allenamenti, diario)
    }
}
