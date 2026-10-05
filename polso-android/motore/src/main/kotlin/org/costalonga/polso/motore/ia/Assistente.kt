package org.costalonga.polso.motore.ia

/** Un motore che trasforma fatti già calcolati in testo (locale o online). */
interface MotoreTesto {
    val nome: String
    suspend fun genera(sistema: String, richiesta: String): String
}

class IaNonDisponibile(msg: String) : Exception(msg)

/** Risposta mostrata all'utente, con l'indicazione di chi l'ha scritta. */
data class Risposta(
    val testo: String,
    /** "calcolo" se scritta dal motore deterministico, altrimenti il nome del modello. */
    val motore: String,
    val daIa: Boolean,
    val nota: String?,
    val fatti: Fatti,
)

/**
 * Regia delle risposte: il modello riceve solo fatti calcolati; la sua
 * uscita viene controllata (numeri e parole vietate) e, se non supera i
 * controlli o il modello non è disponibile, si mostra il testo calcolato
 * con una nota. Non c'è mai un passaggio automatico a un servizio online:
 * chi chiama sceglie il motore, e senza motore risponde il calcolo.
 */
object Assistente {
    suspend fun rispondi(fatti: Fatti, richiesta: String, motore: MotoreTesto?, ripiego: () -> String): Risposta {
        if (motore == null) return Risposta(ripiego(), "calcolo", false, null, fatti)
        return try {
            val t = motore.genera(Prompt.SISTEMA, richiesta)
            if (t.isBlank()) return Risposta(ripiego(), "calcolo", false, "Il modello non ha prodotto testo: mostro il riepilogo calcolato.", fatti)
            val e = Verifica.esamina(t, fatti)
            when {
                e.paroleVietate.isNotEmpty() -> Risposta(ripiego(), "calcolo", false, "La risposta dell'IA è stata scartata perché conteneva indicazioni di tipo medico. Mostro il riepilogo calcolato.", fatti)
                e.numeriNonTrovati.isNotEmpty() -> Risposta(ripiego(), "calcolo", false, "La risposta dell'IA è stata scartata perché conteneva numeri non presenti nei dati (${e.numeriNonTrovati.take(5).joinToString { org.costalonga.polso.motore.Formato.numero(it, 2) }}). Mostro il riepilogo calcolato.", fatti)
                else -> Risposta(t, motore.nome, true, null, fatti)
            }
        } catch (e: IaNonDisponibile) {
            Risposta(ripiego(), "calcolo", false, "${e.message} Mostro il riepilogo calcolato.", fatti)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Risposta(ripiego(), "calcolo", false, "IA non disponibile (${e.javaClass.simpleName}). Mostro il riepilogo calcolato.", fatti)
        }
    }
}
