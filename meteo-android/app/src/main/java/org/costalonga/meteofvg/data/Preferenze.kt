package org.costalonga.meteofvg.data

import android.content.Context
import org.costalonga.meteofvg.allerte.StatoAllerte

/**
 * Quel poco che va ricordato sul telefono: il Comune scelto e l'ultimo dato
 * mostrato, che serve al widget per non restare vuoto mentre scarica.
 */
object Preferenze {

    private const val ARCHIVIO = "meteo-fvg"
    private const val COMUNE = "comune"

    private const val I_COMUNE = "istantanea_comune"
    private const val I_TEMPERATURA = "istantanea_temperatura"
    private const val I_CODICE = "istantanea_codice"
    private const val I_GIORNO = "istantanea_giorno"
    private const val I_MINIMA = "istantanea_minima"
    private const val I_MASSIMA = "istantanea_massima"
    private const val I_PIOGGIA = "istantanea_pioggia"
    private const val I_QUANDO = "istantanea_quando"

    private const val AVVISI = "avvisi_attivi"
    private const val A_COMUNE = "avviso_comune"
    private const val A_STATO = "avviso_stato"
    private const val A_IMPRONTA = "avviso_impronta"

    private fun archivio(contesto: Context) =
        contesto.getSharedPreferences(ARCHIVIO, Context.MODE_PRIVATE)

    /** Il Comune scelto; al primo avvio Roveredo in Piano, come sulla pagina. */
    fun comune(contesto: Context): Comune =
        comunePerNome(archivio(contesto).getString(COMUNE, null)) ?: COMUNE_PREDEFINITO

    fun salvaComune(contesto: Context, comune: Comune) {
        // Cambiare Comune azzera la memoria degli avvisi: quello che sapevamo
        // riguardava un altro paese.
        val cambiato = comune(contesto).nome != comune.nome
        archivio(contesto).edit().putString(COMUNE, comune.nome).apply()
        if (cambiato) dimenticaAvviso(contesto)
    }

    /** Se il telefono deve avvisare quando compare un'allerta. */
    fun avvisiAttivi(contesto: Context): Boolean =
        archivio(contesto).getBoolean(AVVISI, false)

    fun salvaAvvisiAttivi(contesto: Context, attivi: Boolean) {
        archivio(contesto).edit().putBoolean(AVVISI, attivi).apply()
        if (!attivi) dimenticaAvviso(contesto)
    }

    /** L'ultima allerta vista dal controllo in sottofondo. */
    fun memoriaAvviso(contesto: Context): MemoriaAvviso? {
        val a = archivio(contesto)
        val comune = a.getString(A_COMUNE, null) ?: return null
        val stato = a.getString(A_STATO, null) ?: return null
        return MemoriaAvviso(
            comune = comune,
            stato = runCatching { StatoAllerte.valueOf(stato) }.getOrNull() ?: return null,
            impronta = a.getString(A_IMPRONTA, "").orEmpty(),
        )
    }

    fun salvaMemoriaAvviso(
        contesto: Context,
        comune: Comune,
        stato: StatoAllerte,
        impronta: String,
    ) {
        archivio(contesto).edit()
            .putString(A_COMUNE, comune.nome)
            .putString(A_STATO, stato.name)
            .putString(A_IMPRONTA, impronta)
            .apply()
    }

    fun dimenticaAvviso(contesto: Context) {
        archivio(contesto).edit()
            .remove(A_COMUNE)
            .remove(A_STATO)
            .remove(A_IMPRONTA)
            .apply()
    }

    fun salvaIstantanea(contesto: Context, istantanea: Istantanea) {
        archivio(contesto).edit()
            .putString(I_COMUNE, istantanea.comune.nome)
            .putFloat(I_TEMPERATURA, istantanea.temperatura.toFloat())
            .putInt(I_CODICE, istantanea.codice)
            .putBoolean(I_GIORNO, istantanea.diGiorno)
            .putFloat(I_MINIMA, istantanea.minima.toFloat())
            .putFloat(I_MASSIMA, istantanea.massima.toFloat())
            .putInt(I_PIOGGIA, istantanea.pioggia)
            .putLong(I_QUANDO, istantanea.quando)
            .apply()
    }

    /** L'ultimo dato salvato, se riguarda ancora il Comune scelto. */
    fun istantanea(contesto: Context): Istantanea? {
        val a = archivio(contesto)
        val salvato = comunePerNome(a.getString(I_COMUNE, null)) ?: return null
        if (salvato.nome != comune(contesto).nome) return null
        val quando = a.getLong(I_QUANDO, 0L)
        if (quando == 0L) return null
        return Istantanea(
            comune = salvato,
            temperatura = a.getFloat(I_TEMPERATURA, 0f).toDouble(),
            codice = a.getInt(I_CODICE, -1),
            diGiorno = a.getBoolean(I_GIORNO, true),
            minima = a.getFloat(I_MINIMA, 0f).toDouble(),
            massima = a.getFloat(I_MASSIMA, 0f).toDouble(),
            pioggia = a.getInt(I_PIOGGIA, 0),
            quando = quando,
        )
    }
}

/** Che cosa sapeva il controllo in sottofondo l'ultima volta che ha guardato. */
data class MemoriaAvviso(
    val comune: String,
    val stato: StatoAllerte,
    val impronta: String,
)

/** Il minimo indispensabile per scrivere nel widget. */
data class Istantanea(
    val comune: Comune,
    val temperatura: Double,
    val codice: Int,
    val diGiorno: Boolean,
    val minima: Double,
    val massima: Double,
    val pioggia: Int,
    val quando: Long,
) {
    val condizione: Condizione get() = condizioneDi(codice, diGiorno)
}
