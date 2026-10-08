package org.costalonga.polso.motore.esporta

import org.costalonga.polso.motore.Dati
import org.costalonga.polso.motore.FaseSonno
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Periodo
import org.costalonga.polso.motore.Sport
import org.costalonga.polso.motore.Tempo
import org.costalonga.polso.motore.TipoDiario
import org.costalonga.polso.motore.analisi.AnalisiAllenamenti
import org.costalonga.polso.motore.analisi.AnalisiCuore
import org.costalonga.polso.motore.analisi.Catalogo
import org.costalonga.polso.motore.analisi.Contesto
import org.costalonga.polso.motore.analisi.Esito
import org.costalonga.polso.motore.importa.Csv
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Contenuto(val nome: String) {
    MISURE("Misure originali"), GIORNALIERO("Valori giornalieri calcolati"), SONNO("Sonno"),
    ALLENAMENTI("Allenamenti"), DIARIO("Diario"), STATISTICHE("Statistiche"), DEFINIZIONI("Definizioni"),
}

/**
 * Esportazioni in Excel e CSV. Le misure originali e i valori calcolati
 * stanno in fogli distinti e ogni riga dichiara la provenienza: nessuna stima
 * viene presentata come misura.
 */
const val MASSIMO_RIGHE = 1_000_000

class Esportazione(private val d: Dati, private val periodo: Periodo, private val oggi: LocalDate, private val contenuti: Set<Contenuto> = Contenuto.entries.toSet()) {
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private fun ora(t: Long, scarto: Int?): String = Instant.ofEpochMilli(t).atZone(Tempo.zonaPer(scarto, d.zona)).toLocalDateTime().format(fmt)
    private fun scarto(t: Long, scarto: Int?): String = Instant.ofEpochMilli(t).atZone(Tempo.zonaPer(scarto, d.zona)).offset.id.replace("Z", "+00:00")
    private fun nelPeriodo(t: Long, s: Int?) = Tempo.giorno(t, d.zona, s) in periodo.da..periodo.a

    fun righeMisure(): List<List<String>> {
        val r = mutableListOf(listOf("metrica", "nome", "inizio_locale", "fine_locale", "scarto_utc", "valore", "unita", "fonte", "origine", "provenienza"))
        Metrica.entries.forEach { m ->
            d.misure(m).filter { nelPeriodo(it.inizio, it.scartoSec) }.sortedBy { it.inizio }.forEach {
                r += listOf(m.codice, m.nome, ora(it.inizio, it.scartoSec), ora(it.fine, it.scartoSec), scarto(it.inizio, it.scartoSec), it.valore.toString(), it.unita, it.fonte, it.origine,
                    if (it.fonte == "manuale") "inserimento manuale" else if (it.fonte == "demo") "dato dimostrativo sintetico" else "misura della fonte")
            }
        }
        return r
    }

    fun righeGiornaliere(): List<List<String>> {
        val r = mutableListOf(listOf("giorno", "metrica", "valore", "unita", "combinazione", "n_misure", "ore_coperte", "sospetti", "origine_usata", "provenienza"))
        Metrica.entries.filter { d.presente(it) }.forEach { m ->
            periodo.giorni.forEach { g ->
                val v = d.serie(m)[g] ?: return@forEach
                r += listOf(g.toString(), m.codice, v.valore?.toString() ?: "", m.unita, m.combinazione.name.lowercase(), v.n.toString(), v.oreCoperte.toString(), v.sospetti.toString(), v.origine ?: "",
                    "valore calcolato dall'app" + if (g >= oggi) " (giorno parziale)" else "")
            }
        }
        return r
    }

    fun righeSonno(): List<List<String>> {
        val r = mutableListOf(listOf("notte_del", "inizio_locale", "fine_locale", "minuti_a_letto", "minuti_sonno", "fasi_disponibili", "leggero_min", "profondo_min", "rem_min", "sveglio_min", "risvegli", "pisolini", "origine"))
        d.nottiNelPeriodo(periodo).forEach { n ->
            fun f(x: FaseSonno) = if (n.haFasi) (n.minutiPerFase[x] ?: 0.0).toString() else ""
            r += listOf(n.giorno.toString(), ora(n.inizio, n.scartoSec), ora(n.fine, n.scartoSec), n.minutiInLetto.toString(), n.minutiSonno.toString(), if (n.haFasi) "sì" else "no",
                f(FaseSonno.LEGGERO), f(FaseSonno.PROFONDO), f(FaseSonno.REM), f(FaseSonno.SVEGLIO), n.risvegli?.toString() ?: "", n.pisolini.toString(), n.origine)
        }
        return r
    }

    fun righeAllenamenti(): List<List<String>> {
        val r = mutableListOf(listOf("inizio_locale", "fine_locale", "sport", "durata_min", "distanza_m (fonte)", "passo_min_km (calcolato)", "velocita_kmh (calcolata)", "calorie_kcal (fonte)", "dislivello_m (fonte)", "fc_media (calcolata)", "fc_max (calcolata)", "punti_percorso", "titolo", "origine"))
        d.allenamentiNelPeriodo(periodo).forEach { a ->
            val fc = AnalisiCuore.fcSessione(d, a)
            r += listOf(ora(a.inizio, a.scartoSec), ora(a.fine, a.scartoSec), Sport.nome(a.sport), (a.durataMs / 60_000.0).toString(), a.distanzaM?.toString() ?: "",
                AnalisiAllenamenti.passoSecKm(a)?.let { (it / 60).toString() } ?: "", AnalisiAllenamenti.velocitaKmh(a)?.toString() ?: "",
                a.calorieKcal?.toString() ?: "", a.dislivelloM?.toString() ?: "", fc?.first?.toString() ?: "", fc?.second?.toString() ?: "", a.percorso.size.toString(), a.titolo, a.origine)
        }
        return r
    }

    fun righeDiario(): List<List<String>> {
        val r = mutableListOf(listOf("istante_locale", "tipo", "valore", "valore2", "unita", "testo", "provenienza"))
        d.diarioNelPeriodo(periodo).sortedBy { it.istante }.forEach { v ->
            val t = TipoDiario.daCodice(v.tipo)
            r += listOf(ora(v.istante, null), t?.nome ?: v.tipo, v.valore?.toString() ?: "", v.valore2?.toString() ?: "", t?.unita ?: "", v.testo, "inserimento manuale")
        }
        return r
    }

    fun righeStatistiche(): List<List<String>> {
        val c = Contesto(d, periodo, oggi)
        val r = mutableListOf(listOf("sezione", "analisi", "voce", "valore", "provenienza", "giorni_con_dati", "giorni_nel_periodo", "stato"))
        Catalogo.tutte.forEach { a ->
            when (val e = a.esegui(c)) {
                is Esito.Disponibile -> e.voci.forEach { v -> r += listOf(a.def.sezione.nome, a.def.titolo, v.etichetta, v.testo, v.provenienza.etichetta, e.copertura.giorniConDati.toString(), e.copertura.giorniNelPeriodo.toString(), "disponibile") }
                is Esito.NonDisponibile -> r += listOf(a.def.sezione.nome, a.def.titolo, "", "", "", "", "", "non disponibile: ${e.motivo}")
            }
        }
        return r
    }

    fun righeDefinizioni(): List<List<String>> {
        val r = mutableListOf(listOf("id", "titolo", "sezione", "dati_richiesti", "metodo", "unita", "minimo", "mancanti", "limiti"))
        Catalogo.tutte.forEach { a -> a.def.let { r += listOf(it.id, it.titolo, it.sezione.nome, it.datiRichiesti, it.metodo, it.unita, it.minimo, it.mancanti, it.limiti) } }
        Metrica.entries.forEach { m -> r += listOf("metrica." + m.codice, m.nome, "Grandezze", "", "Combinazione giornaliera: ${m.combinazione.name.lowercase()}; intervallo plausibile ${m.minimoPlausibile}–${m.massimoPlausibile}", m.unita, "", "", if (m.proprietaria) "punteggio proprietario della fonte" else "") }
        return r
    }

    fun info(): List<List<String>> = listOf(
        listOf("voce", "valore"),
        listOf("Periodo", "${periodo.da} – ${periodo.a}" + if (periodo.parziale(oggi)) " (in corso, ultimo giorno parziale)" else ""),
        listOf("Fuso orario", d.zona.id),
        listOf("Generato il", java.time.ZonedDateTime.now(d.zona).toLocalDateTime().format(fmt)),
        listOf("Unità", "sistema metrico; date in ora locale con scarto dal tempo universale indicato"),
        listOf("Provenienza", "«misura della fonte» = dato originale; «valore calcolato» = elaborato dall'app; il diario è inserito a mano"),
        listOf("Limiti", "I valori dello smartwatch sono stime di benessere, non misure diagnostiche. I giorni senza dati sono vuoti, mai zero."),
    )

    fun excel(out: OutputStream) {
        val x = Xlsx()
        val tagliati = mutableListOf<String>()
        fun aggiungi(nome: String, tutte: List<List<String>>, numeriche: Set<Int> = emptySet()) {
            val f = x.foglio(nome)
            // Limite di Excel: 1 048 576 righe per foglio. Oltre, si rimanda al CSV.
            val righe = if (tutte.size > MASSIMO_RIGHE) tutte.take(MASSIMO_RIGHE).also { tagliati += nome } else tutte
            righe.forEachIndexed { i, r ->
                if (i == 0) f.intestazione(*r.toTypedArray())
                else f.riga(*r.mapIndexed<String, Any?> { c, v -> if (c in numeriche) (v.toDoubleOrNull() ?: v.ifEmpty { null }) else v.ifEmpty { null } }.toTypedArray())
            }
        }
        aggiungi("Informazioni", info())
        if (Contenuto.MISURE in contenuti) aggiungi("Misure originali", righeMisure(), setOf(5))
        if (Contenuto.GIORNALIERO in contenuti) aggiungi("Giornaliero calcolato", righeGiornaliere(), setOf(2, 5, 6, 7))
        if (Contenuto.SONNO in contenuti) aggiungi("Sonno", righeSonno(), setOf(3, 4, 6, 7, 8, 9, 10, 11))
        if (Contenuto.ALLENAMENTI in contenuti) aggiungi("Allenamenti", righeAllenamenti(), setOf(3, 4, 5, 6, 7, 8, 9, 10, 11))
        if (Contenuto.DIARIO in contenuti) aggiungi("Diario", righeDiario(), setOf(2, 3))
        if (Contenuto.STATISTICHE in contenuti) aggiungi("Statistiche", righeStatistiche(), setOf(5, 6))
        if (Contenuto.DEFINIZIONI in contenuti) aggiungi("Definizioni", righeDefinizioni())
        if (tagliati.isNotEmpty()) x.fogli.first().riga("Attenzione", "Fogli troncati al limite di Excel (${tagliati.joinToString()}): per i dati completi usa l'esportazione CSV.")
        x.scrivi(out)
    }

    /** Un file CSV (separatore «;», UTF-8 con BOM per Excel) per ciascun contenuto scelto. */
    fun csv(): Map<String, String> {
        val r = linkedMapOf<String, String>()
        fun testo(righe: List<List<String>>) = "﻿" + righe.joinToString("\r\n") { Csv.riga(it) } + "\r\n"
        if (Contenuto.MISURE in contenuti) r["misure.csv"] = testo(righeMisure())
        if (Contenuto.GIORNALIERO in contenuti) r["giornaliero.csv"] = testo(righeGiornaliere())
        if (Contenuto.SONNO in contenuti) r["sonno.csv"] = testo(righeSonno())
        if (Contenuto.ALLENAMENTI in contenuti) r["allenamenti.csv"] = testo(righeAllenamenti())
        if (Contenuto.DIARIO in contenuti) r["diario.csv"] = testo(righeDiario())
        if (Contenuto.STATISTICHE in contenuti) r["statistiche.csv"] = testo(righeStatistiche())
        if (Contenuto.DEFINIZIONI in contenuti) r["definizioni.csv"] = testo(righeDefinizioni())
        return r
    }
}
