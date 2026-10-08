package org.costalonga.polso.motore.importa

import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.FaseSonno
import org.costalonga.polso.motore.IntervalloSonno
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.Natura
import org.costalonga.polso.motore.SessioneSonno
import org.costalonga.polso.motore.Tempo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class RisultatoImportazione(
    val misure: List<Misura> = emptyList(),
    val sonni: List<SessioneSonno> = emptyList(),
    val allenamenti: List<Allenamento> = emptyList(),
    val righeLette: Int = 0,
    val errori: List<String> = emptyList(),
) {
    val totale: Int get() = misure.size + sonni.size + allenamenti.size
    operator fun plus(o: RisultatoImportazione) = RisultatoImportazione(misure + o.misure, sonni + o.sonni, allenamenti + o.allenamenti, righeLette + o.righeLette, errori + o.errori)
}

enum class TipoValore { INCREMENTO, CUMULATIVO, ISTANTANEO }

object Numeri {
    /** Accetta "1234.5", "1234,5", "1.234,5" e "1,234.5": il separatore decimale è l'ultimo dei due. */
    fun leggi(s: String): Double? {
        val t = s.trim().replace(" ", "").replace(" ", "")
        if (t.isEmpty()) return null
        val ip = t.lastIndexOf('.')
        val iv = t.lastIndexOf(',')
        val norm = when {
            ip >= 0 && iv >= 0 -> if (iv > ip) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            iv >= 0 -> t.replace(',', '.')
            else -> t
        }
        return norm.toDoubleOrNull()
    }
}

object Date {
    private val formatiLocali = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    )

    /**
     * Legge un istante. Con scarto esplicito (2026-10-01T08:00:00+02:00 o Z)
     * vale quello; senza, l'ora è locale nel fuso indicato. Nell'ora ripetuta
     * del cambio d'ora d'autunno si sceglie la prima occorrenza (regola di
     * java.time), e lo si segnala nella documentazione del formato.
     */
    fun istante(s: String, zona: ZoneId, formato: DateTimeFormatter? = null): Pair<Long, Int>? {
        val t = s.trim()
        if (t.isEmpty()) return null
        t.toLongOrNull()?.let { n -> return (if (n < 100_000_000_000L) n * 1000 else n).let { it to zona.rules.getOffset(Instant.ofEpochMilli(it)).totalSeconds } }
        try { val o = OffsetDateTime.parse(t); return o.toInstant().toEpochMilli() to o.offset.totalSeconds } catch (_: DateTimeParseException) {}
        try { val i = Instant.parse(t); return i.toEpochMilli() to 0 } catch (_: DateTimeParseException) {}
        val formati = if (formato != null) listOf(formato) else formatiLocali
        for (f in formati) {
            try {
                val z = LocalDateTime.parse(t, f).atZone(zona)
                return z.toInstant().toEpochMilli() to z.offset.totalSeconds
            } catch (_: DateTimeParseException) {}
        }
        try { val z = LocalDate.parse(t).atStartOfDay(zona); return z.toInstant().toEpochMilli() to z.offset.totalSeconds } catch (_: DateTimeParseException) {}
        return null
    }
}

/** Conversione delle unità verso quelle interne (sistema metrico). */
object Unita {
    fun converti(m: Metrica, valore: Double, unita: String): Double? {
        val u = unita.trim().lowercase()
        if (u.isEmpty() || u == m.unita.lowercase()) return valore
        return when (m) {
            Metrica.DISTANZA, Metrica.DISLIVELLO -> when (u) { "km" -> valore * 1000; "m", "metri" -> valore; else -> null }
            Metrica.CALORIE_ATTIVE, Metrica.CALORIE_TOTALI -> when (u) { "kj" -> valore / 4.184; "cal" -> valore / 1000; "kcal" -> valore; else -> null }
            Metrica.PESO -> when (u) { "g" -> valore / 1000; "kg" -> valore; else -> null }
            Metrica.MINUTI_ATTIVI -> when (u) { "s", "sec" -> valore / 60; "h", "ore" -> valore * 60; "min", "minuti" -> valore; else -> null }
            Metrica.SPO2, Metrica.GRASSO -> when (u) { "%" -> valore; "frazione" -> valore * 100; else -> null }
            Metrica.PASSI -> if (u in setOf("passi", "steps", "count")) valore else null
            Metrica.FREQUENZA_CARDIACA, Metrica.FC_RIPOSO -> if (u in setOf("bpm", "battiti/min")) valore else null
            else -> null
        }
    }
}

/**
 * Converte letture di un contatore cumulativo (per esempio «passi da
 * mezzanotte») in incrementi su intervalli. Un valore minore del precedente
 * indica un azzeramento: l'incremento è il valore stesso. Con
 * [azzeramentoGiornaliero] la prima lettura di ogni giorno vale dalla
 * mezzanotte. Non si sommano mai le letture cumulative fra loro.
 */
object Cumulativi {
    fun aIncrementi(letture: List<Misura>, zona: ZoneId, azzeramentoGiornaliero: Boolean = true): List<Misura> {
        val ordinate = letture.sortedBy { it.inizio }
        val r = mutableListOf<Misura>()
        var prec: Misura? = null
        for (m in ordinate) {
            val p = prec
            val nuovoGiorno = p == null || Tempo.giorno(p.inizio, zona, p.scartoSec) != Tempo.giorno(m.inizio, zona, m.scartoSec)
            if (p == null && !azzeramentoGiornaliero) { prec = m; continue } // prima lettura: solo riferimento
            val (da, inc) = when {
                p == null || (azzeramentoGiornaliero && nuovoGiorno) -> {
                    val inizio = if (azzeramentoGiornaliero) Tempo.inizioGiorno(Tempo.giorno(m.inizio, zona, m.scartoSec), Tempo.zonaPer(m.scartoSec, zona)) else m.inizio
                    inizio to m.valore
                }
                m.valore < p.valore -> p.inizio to m.valore
                else -> p.inizio to (m.valore - p.valore)
            }
            if (inc > 0) r += m.copy(inizio = da, fine = m.inizio, valore = inc, idEsterno = m.idEsterno?.let { "$it#inc" })
            prec = m
        }
        return r
    }
}

/**
 * Formato CSV di Polso (documentato in IMPORTAZIONE.md). Colonne:
 * metrica; inizio; fine; valore; unita; tipo_valore; origine; id; fase; sport; calorie; dislivello.
 * Solo metrica, inizio e valore sono obbligatorie (per il sonno «valore» è la fase).
 */
object ImportaPolsoCsv {
    val COLONNE = listOf("metrica", "inizio", "fine", "valore", "unita", "tipo_valore", "origine", "id", "sport", "calorie", "dislivello")

    fun importa(testo: String, nomeFile: String, zona: ZoneId = Tempo.ROMA): RisultatoImportazione {
        val righe = Csv.leggi(testo)
        if (righe.isEmpty()) return RisultatoImportazione(errori = listOf("Il file è vuoto."))
        val intest = righe.first().map { it.trim().lowercase() }
        val idx = COLONNE.associateWith { intest.indexOf(it) }
        if (idx["metrica"]!! < 0 || idx["inizio"]!! < 0 || idx["valore"]!! < 0) {
            return RisultatoImportazione(errori = listOf("Intestazione non riconosciuta: servono almeno le colonne metrica, inizio, valore."))
        }
        fun cella(r: List<String>, n: String) = idx[n]!!.let { if (it >= 0 && it < r.size) r[it].trim() else "" }
        val fonte = "file"
        val misure = mutableListOf<Misura>()
        val cumulative = mutableListOf<Misura>()
        val fasi = mutableListOf<Pair<Pair<Long, Long>, Pair<String, Int>>>()
        val allenamenti = mutableListOf<Allenamento>()
        val errori = mutableListOf<String>()
        righe.drop(1).forEachIndexed { i, r ->
            val nr = i + 2
            val codice = cella(r, "metrica").lowercase()
            val ini = Date.istante(cella(r, "inizio"), zona) ?: run { errori += "Riga $nr: data di inizio non valida «${cella(r, "inizio")}»."; return@forEachIndexed }
            val fine = cella(r, "fine").takeIf { it.isNotEmpty() }?.let { Date.istante(it, zona) ?: run { errori += "Riga $nr: data di fine non valida."; return@forEachIndexed } }
            val origine = cella(r, "origine").ifEmpty { nomeFile }
            val id = cella(r, "id").ifEmpty { null }
            when (codice) {
                "sonno" -> {
                    val fase = FaseSonno.entries.firstOrNull { it.codice == cella(r, "valore").lowercase() }
                    if (fase == null || fine == null) { errori += "Riga $nr: per il sonno servono fine e una fase valida (sveglio, leggero, profondo, rem, sonno)."; return@forEachIndexed }
                    fasi += (ini.first to fine.first) to (fase.codice to ini.second)
                }
                "allenamento" -> {
                    if (fine == null) { errori += "Riga $nr: un allenamento richiede la fine."; return@forEachIndexed }
                    allenamenti += Allenamento(
                        inizio = ini.first, fine = fine.first, scartoSec = ini.second, sport = cella(r, "sport").ifEmpty { "altro" },
                        fonte = fonte, origine = origine, idEsterno = id,
                        distanzaM = Numeri.leggi(cella(r, "valore")), calorieKcal = Numeri.leggi(cella(r, "calorie")), dislivelloM = Numeri.leggi(cella(r, "dislivello")),
                    )
                }
                else -> {
                    val m = Metrica.daCodice(codice) ?: run { errori += "Riga $nr: metrica sconosciuta «$codice»."; return@forEachIndexed }
                    val grezzo = Numeri.leggi(cella(r, "valore")) ?: run { errori += "Riga $nr: valore non numerico."; return@forEachIndexed }
                    val v = Unita.converti(m, grezzo, cella(r, "unita")) ?: run { errori += "Riga $nr: unità «${cella(r, "unita")}» non valida per ${m.nome} (attesa ${m.unita})."; return@forEachIndexed }
                    val tipo = when (cella(r, "tipo_valore").lowercase()) {
                        "cumulativo" -> TipoValore.CUMULATIVO
                        "istantaneo" -> TipoValore.ISTANTANEO
                        "incremento" -> TipoValore.INCREMENTO
                        "" -> if (m.natura == Natura.INTERVALLO) TipoValore.INCREMENTO else TipoValore.ISTANTANEO
                        else -> { errori += "Riga $nr: tipo_valore deve essere incremento, cumulativo o istantaneo."; return@forEachIndexed }
                    }
                    if (tipo == TipoValore.INCREMENTO && m.natura == Natura.INTERVALLO && fine == null) { errori += "Riga $nr: un incremento di ${m.nome} richiede la fine dell'intervallo."; return@forEachIndexed }
                    val mis = Misura(m.codice, ini.first, fine?.first ?: ini.first, v, m.unita, ini.second, fonte, origine, "", id)
                    if (tipo == TipoValore.CUMULATIVO) cumulative += mis else misure += mis
                }
            }
        }
        cumulative.groupBy { it.metrica to it.origine }.values.forEach { misure += Cumulativi.aIncrementi(it, zona) }
        return RisultatoImportazione(misure, raggruppaFasi(fasi, fonte, nomeFile), allenamenti, righe.size - 1, errori)
    }

    /** Fasi consecutive (pausa ≤ 30 minuti) formano una sessione di sonno. */
    fun raggruppaFasi(fasi: List<Pair<Pair<Long, Long>, Pair<String, Int>>>, fonte: String, origine: String): List<SessioneSonno> {
        val ordinate = fasi.sortedBy { it.first.first }
        val sessioni = mutableListOf<SessioneSonno>()
        var corrente = mutableListOf<Pair<Pair<Long, Long>, Pair<String, Int>>>()
        fun chiudi() {
            if (corrente.isEmpty()) return
            sessioni += SessioneSonno(corrente.first().first.first, corrente.maxOf { it.first.second }, corrente.first().second.second, fonte, origine,
                fasi = corrente.map { IntervalloSonno(it.first.first, it.first.second, it.second.first) })
            corrente = mutableListOf()
        }
        for (f in ordinate) {
            if (corrente.isNotEmpty() && f.first.first - corrente.maxOf { it.first.second } > 30 * 60_000L) chiudi()
            corrente += f
        }
        chiudi()
        return sessioni
    }
}

/** Mappatura scelta dall'utente per un CSV di formato qualsiasi. */
data class Mappatura(
    val metrica: Metrica,
    val colonnaInizio: Int,
    val colonnaFine: Int? = null,
    val colonnaValore: Int,
    val unita: String = "",
    val formatoData: String? = null,
    val tipo: TipoValore = TipoValore.ISTANTANEO,
    val salta: Int = 1,
)

object ImportaCsvGenerico {
    fun anteprima(testo: String, righe: Int = 6): List<List<String>> = Csv.leggi(testo).take(righe)

    fun importa(testo: String, nomeFile: String, map: Mappatura, zona: ZoneId = Tempo.ROMA): RisultatoImportazione {
        val righe = Csv.leggi(testo).drop(map.salta)
        val f = map.formatoData?.takeIf { it.isNotBlank() }?.let { DateTimeFormatter.ofPattern(it) }
        val misure = mutableListOf<Misura>()
        val errori = mutableListOf<String>()
        righe.forEachIndexed { i, r ->
            val nr = i + 1 + map.salta
            val ini = r.getOrNull(map.colonnaInizio)?.let { Date.istante(it, zona, f) } ?: run { errori += "Riga $nr: data non valida."; return@forEachIndexed }
            val fine = map.colonnaFine?.let { c -> r.getOrNull(c)?.let { Date.istante(it, zona, f) } ?: run { errori += "Riga $nr: data di fine non valida."; return@forEachIndexed } }
            val grezzo = r.getOrNull(map.colonnaValore)?.let { Numeri.leggi(it) } ?: run { errori += "Riga $nr: valore non numerico."; return@forEachIndexed }
            val v = Unita.converti(map.metrica, grezzo, map.unita) ?: run { errori += "Riga $nr: unità non valida."; return@forEachIndexed }
            if (map.tipo == TipoValore.INCREMENTO && fine == null) { errori += "Riga $nr: un incremento richiede la colonna di fine."; return@forEachIndexed }
            misure += Misura(map.metrica.codice, ini.first, fine?.first ?: ini.first, v, map.metrica.unita, ini.second, "file", nomeFile)
        }
        val finali = if (map.tipo == TipoValore.CUMULATIVO) Cumulativi.aIncrementi(misure, zona) else misure
        return RisultatoImportazione(misure = finali, righeLette = righe.size, errori = errori)
    }
}
