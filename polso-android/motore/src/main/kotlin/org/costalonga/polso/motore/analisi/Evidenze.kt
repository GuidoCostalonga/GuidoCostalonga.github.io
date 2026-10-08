package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Stat

/** Una cosa che emerge dai dati, scritta da regole fisse (non dall'IA). */
data class Evidenza(val titolo: String, val testo: String, val analisiId: String, val peso: Int)

/**
 * «Cosa emerge dai tuoi dati»: solo fatti che superano criteri dichiarati
 * (tendenze con p < 0,05, differenze con intervallo che esclude lo zero,
 * relazioni con q < 0,1, serie e record), più gli avvisi di copertura.
 */
object Evidenze {
    const val CRITERI = "Si mostrano solo: tendenze con p < 0,05 su almeno 14 giorni; confronti in cui l'intervallo di confidenza al 95% esclude lo zero; relazioni con q < 0,1 (Benjamini–Hochberg); serie di almeno 3 giorni con obiettivo raggiunto; avvisi quando la copertura è sotto il 50%."

    fun calcola(c: Contesto): List<Evidenza> {
        val r = mutableListOf<Evidenza>()
        fun disp(id: String) = Catalogo.perId(id)?.esegui(c) as? Esito.Disponibile

        disp("att.tendenza")?.let { e ->
            val p = e.voci.firstOrNull { it.etichetta.startsWith("p-value") }?.valore
            if (p != null && p < 0.05) r += Evidenza("Passi ${e.voci[0].testo}", "Variazione stimata ${e.voci[1].testo} (p ${Formato.pValore(p)}).", "att.tendenza", 3)
        }
        disp("sonno.tendenza")?.let { e ->
            val p = e.voci.firstOrNull { it.etichetta == "p-value" }?.valore
            if (p != null && p < 0.05) r += Evidenza("Durata del sonno ${e.voci[0].testo}", "Variazione stimata ${e.voci[1].testo} (p ${Formato.pValore(p)}).", "sonno.tendenza", 3)
        }
        if (c.dati.presente(Metrica.PASSI)) {
            val ora = c.dati.osservati(Metrica.PASSI, c.giorni)
            val prima = c.dati.osservati(Metrica.PASSI, c.periodo.precedente().giorni)
            val ic = Stat.bootstrapDifferenzaMedie(ora, prima)
            if (ic != null && (ic.first > 0 || ic.second < 0)) {
                val d = ora.average() - prima.average()
                r += Evidenza(if (d > 0) "Più passi del periodo precedente" else "Meno passi del periodo precedente",
                    "In media ${Formato.conSegno(d)} passi al giorno (${Formato.conSegno(Stat.variazionePercentuale(ora.average(), prima.average()), 1)}%), intervallo 95% ${Formato.conSegno(ic.first)} … ${Formato.conSegno(ic.second)}.", "att.confronto", 2)
            }
        }
        Relazioni.tutte(c).filter { it.corr != null && (it.q ?: 1.0) < 0.1 }.forEach {
            r += Evidenza(it.coppia.titolo, Relazioni.descrivi(it) + " ρ = ${Formato.numero(it.corr!!.rho, 2)} su ${it.corr.n} giorni. È un'associazione, non una causa.", it.coppia.id, 2)
        }
        disp("att.obiettivo")?.let { e ->
            val serie = e.voci.firstOrNull { it.etichetta == "Serie attuale" }?.valore ?: 0.0
            if (serie >= 3) r += Evidenza("Serie in corso: ${serie.toInt()} giorni", "Da ${serie.toInt()} giorni consecutivi raggiungi l'obiettivo di passi.", "att.obiettivo", 1)
        }
        listOf(Metrica.PASSI, Metrica.FREQUENZA_CARDIACA).filter { c.dati.presente(it) }.forEach { m ->
            val cop = c.copertura(m)
            if (cop.giorniNelPeriodo >= 7 && cop.percentuale < 50) r += Evidenza("Copertura bassa: ${m.nome}",
                "Dati presenti in ${cop.giorniConDati} giorni su ${cop.giorniNelPeriodo}: le analisi del periodo sono poco rappresentative.", "qual.copertura", 0)
        }
        if (c.dati.sonni.isNotEmpty()) {
            val n = c.dati.nottiNelPeriodo(c.periodo).size
            if (c.giorni.size >= 7 && n < c.giorni.size / 2) r += Evidenza("Poche notti registrate", "Sonno presente in $n notti su ${c.giorni.size}.", "qual.copertura", 0)
        }
        return r.sortedByDescending { it.peso }
    }
}
