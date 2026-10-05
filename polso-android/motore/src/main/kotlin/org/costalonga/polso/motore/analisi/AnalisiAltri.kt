package org.costalonga.polso.motore.analisi

import org.costalonga.polso.motore.Formato
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Stat
import org.costalonga.polso.motore.TipoDiario
import java.time.temporal.ChronoUnit

object AnalisiAltri {
    private fun def(id: String, titolo: String, dati: String, metodo: String, unita: String, minimo: String, limiti: String, prova: String) =
        Definizione(id, titolo, Sezione.ALTRI, dati, metodo, unita, minimo, MANCANTI_STD, limiti, prova)

    val spo2 = analisi(def("altri.spo2", "Saturazione (SpO₂)", "Misure di SpO₂ della fonte",
        "Media giornaliera, minimo e massimo; numero di letture sotto il 90% (solo conteggio descrittivo).",
        "%", "1 giorno", "La saturazione da polso è sensibile a movimento, freddo e posizione: una lettura bassa isolata è spesso un artefatto. Non è uno strumento diagnostico.", "AnalisiAltriTest.spo2")) { c ->
        val base = riepilogoMetrica(c, Metrica.SPO2, 1)
        if (base !is Esito.Disponibile) return@analisi base
        val da = org.costalonga.polso.motore.Tempo.inizioGiorno(c.periodo.da, c.dati.zona)
        val a = org.costalonga.polso.motore.Tempo.inizioGiorno(c.periodo.a.plusDays(1), c.dati.zona)
        val camp = c.dati.campioni(Metrica.SPO2, da, a)
        base.copy(voci = base.voci + listOf(
            Voce("Letture nel periodo", "${camp.size}"),
            Voce("Letture sotto il 90%", "${camp.count { it.valore < 90 }}"),
        ), note = base.note + "Se le letture basse si ripetono o si accompagnano a sintomi, parlane con il medico: l'app non interpreta questi valori.")
    }

    val respirazione = analisi(def("altri.respirazione", "Frequenza respiratoria", "Frequenza respiratoria della fonte", "Riepilogo dei valori giornalieri e tendenza.",
        "atti/min", "1 giorno", "HONOR Health non dichiara di condividere questo dato con Health Connect: in genere sarà assente.", "AnalisiAltriTest.respirazione")) { c ->
        riepilogoMetrica(c, Metrica.RESPIRAZIONE, 1)
    }

    val temperatura = analisi(def("altri.temperatura", "Temperatura cutanea (variazione)", "Variazioni di temperatura cutanea della fonte",
        "Riepilogo delle variazioni notturne rispetto al riferimento della fonte.", "°C", "1 giorno",
        "È una variazione relativa, non la temperatura corporea. L'A58 non dichiara questo sensore.", "AnalisiAltriTest.temperatura")) { c ->
        riepilogoMetrica(c, Metrica.TEMPERATURA_CUTANEA, 1)
    }

    val stress = analisi(def("altri.stress", "Stress (punteggio della fonte)", "Punteggio di stress importato da file",
        "Media giornaliera e distribuzione del punteggio fornito dalla fonte.", "punti", "1 giorno",
        "Punteggio proprietario stimato dalla variabilità cardiaca con un metodo non pubblico: non è una misura di stress psicologico né clinica. Health Connect non ha questo tipo di dato.", "AnalisiAltriTest.stress")) { c ->
        riepilogoMetrica(c, Metrica.STRESS, 1)
    }

    val vo2 = analisi(def("altri.vo2max", "VO₂max (stima della fonte)", "VO₂max fornito dalla fonte",
        "Ultimo valore e andamento. L'app non stima il VO₂max da sola.", "ml/kg/min", "1 valore",
        "Stima della fonte da corsa e frequenza cardiaca, con errore tipico di alcuni ml/kg/min.", "AnalisiAltriTest.vo2max")) { c ->
        riepilogoMetrica(c, Metrica.VO2MAX, 1)
    }

    val peso = analisi(def("altri.peso", "Peso", "Peso da bilancia collegata (fonte) e diario manuale",
        "Ultimo valore, variazione dall'inizio del periodo, tendenza in kg a settimana (Theil–Sen) con almeno 4 pesate.",
        "kg", "2 pesate", "Il peso oscilla di 1-2 kg in un giorno per acqua e pasti: conta la tendenza, non la singola pesata.", "AnalisiAltriTest.peso")) { c ->
        val s = c.dati.seriePeso().filterKeys { it in c.periodo.da..minOf(c.periodo.a, c.oggi) }
        if (s.size < 2) return@analisi nd("Servono almeno 2 pesate nel periodo (diario o bilancia); ce ne sono ${s.size}.", "Peso")
        val voci = mutableListOf(
            Voce("Ultima pesata", "${Formato.conUnita(s.values.last(), "kg", 1)} (${org.costalonga.polso.motore.Tempo.etichetta(s.keys.last())})", s.values.last(), "kg"),
            Voce("Variazione nel periodo", "${Formato.conSegno(s.values.last() - s.values.first(), 1)} kg", s.values.last() - s.values.first(), "kg"),
        )
        if (s.size >= 4) Stat.tendenza(s.keys.map { ChronoUnit.DAYS.between(c.periodo.da, it).toDouble() }, s.values.toList())?.let {
            voci += Voce("Tendenza", "${Formato.conSegno(it.pendenza * 7, 2)} kg a settimana (p ${Formato.pValore(it.p)})", it.pendenza * 7, "kg/settimana")
        }
        Esito.Disponibile(voci, Copertura(s.size, c.giorni.size, s.keys.first(), s.keys.last()),
            listOf(Grafico.Linea("Peso", "kg", c.giorni.map { it to s[it] })))
    }

    val pressione = analisi(def("altri.pressione", "Pressione arteriosa (dispositivo esterno)", "Misure di pressione dal diario o dalla fonte",
        "Media, minimo e massimo di sistolica e diastolica nel periodo; numero di misure.", "mmHg", "1 misura",
        "Dato inserito da te o letto da un misuratore collegato: l'A58 non misura la pressione. Nessuna classificazione clinica.", "AnalisiAltriTest.pressione")) { c ->
        val man = c.dati.diarioNelPeriodo(c.periodo, TipoDiario.PRESSIONE).filter { it.valore != null && it.valore2 != null }
        val sis = man.map { it.valore!! } + c.dati.osservati(Metrica.PRESSIONE_SISTOLICA, c.giorni)
        val dia = man.map { it.valore2!! } + c.dati.osservati(Metrica.PRESSIONE_DIASTOLICA, c.giorni)
        if (sis.isEmpty() || dia.isEmpty()) return@analisi nd("Nessuna misura di pressione nel periodo.", "Pressione")
        Esito.Disponibile(
            listOf(
                Voce("Media", "${Formato.numero(sis.average())}/${Formato.numero(dia.average())} mmHg"),
                Voce("Sistolica min–max", "${Formato.numero(sis.min())}–${Formato.numero(sis.max())} mmHg"),
                Voce("Diastolica min–max", "${Formato.numero(dia.min())}–${Formato.numero(dia.max())} mmHg"),
                Voce("Misure", "${maxOf(sis.size, dia.size)}"),
            ),
            Copertura(maxOf(sis.size, dia.size), c.giorni.size, null, null),
        )
    }

    val elenco = listOf(spo2, respirazione, temperatura, stress, vo2, peso, pressione)
}
