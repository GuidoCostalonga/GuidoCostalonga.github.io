package org.costalonga.meteofvg.data

import org.costalonga.meteofvg.R

/**
 * Le dieci condizioni disegnate, ognuna con i sei fotogrammi della propria
 * animazione. I disegni sono generati da strumenti/genera_icone.py.
 */
enum class Famiglia {
    SOLE,
    LUNA,
    SOLE_NUVOLE,
    LUNA_NUVOLE,
    NUVOLE,
    NEBBIA,
    PIOVIGGINE,
    PIOGGIA,
    NEVE,
    TEMPORALE;

    /** I sei disegni, nell'ordine in cui vanno mostrati. */
    val fotogrammi: IntArray
        get() = when (this) {
            SOLE -> intArrayOf(
                R.drawable.ic_meteo_sole_0, R.drawable.ic_meteo_sole_1,
                R.drawable.ic_meteo_sole_2, R.drawable.ic_meteo_sole_3,
                R.drawable.ic_meteo_sole_4, R.drawable.ic_meteo_sole_5,
            )
            LUNA -> intArrayOf(
                R.drawable.ic_meteo_luna_0, R.drawable.ic_meteo_luna_1,
                R.drawable.ic_meteo_luna_2, R.drawable.ic_meteo_luna_3,
                R.drawable.ic_meteo_luna_4, R.drawable.ic_meteo_luna_5,
            )
            SOLE_NUVOLE -> intArrayOf(
                R.drawable.ic_meteo_sole_nuvole_0, R.drawable.ic_meteo_sole_nuvole_1,
                R.drawable.ic_meteo_sole_nuvole_2, R.drawable.ic_meteo_sole_nuvole_3,
                R.drawable.ic_meteo_sole_nuvole_4, R.drawable.ic_meteo_sole_nuvole_5,
            )
            LUNA_NUVOLE -> intArrayOf(
                R.drawable.ic_meteo_luna_nuvole_0, R.drawable.ic_meteo_luna_nuvole_1,
                R.drawable.ic_meteo_luna_nuvole_2, R.drawable.ic_meteo_luna_nuvole_3,
                R.drawable.ic_meteo_luna_nuvole_4, R.drawable.ic_meteo_luna_nuvole_5,
            )
            NUVOLE -> intArrayOf(
                R.drawable.ic_meteo_nuvole_0, R.drawable.ic_meteo_nuvole_1,
                R.drawable.ic_meteo_nuvole_2, R.drawable.ic_meteo_nuvole_3,
                R.drawable.ic_meteo_nuvole_4, R.drawable.ic_meteo_nuvole_5,
            )
            NEBBIA -> intArrayOf(
                R.drawable.ic_meteo_nebbia_0, R.drawable.ic_meteo_nebbia_1,
                R.drawable.ic_meteo_nebbia_2, R.drawable.ic_meteo_nebbia_3,
                R.drawable.ic_meteo_nebbia_4, R.drawable.ic_meteo_nebbia_5,
            )
            PIOVIGGINE -> intArrayOf(
                R.drawable.ic_meteo_pioviggine_0, R.drawable.ic_meteo_pioviggine_1,
                R.drawable.ic_meteo_pioviggine_2, R.drawable.ic_meteo_pioviggine_3,
                R.drawable.ic_meteo_pioviggine_4, R.drawable.ic_meteo_pioviggine_5,
            )
            PIOGGIA -> intArrayOf(
                R.drawable.ic_meteo_pioggia_0, R.drawable.ic_meteo_pioggia_1,
                R.drawable.ic_meteo_pioggia_2, R.drawable.ic_meteo_pioggia_3,
                R.drawable.ic_meteo_pioggia_4, R.drawable.ic_meteo_pioggia_5,
            )
            NEVE -> intArrayOf(
                R.drawable.ic_meteo_neve_0, R.drawable.ic_meteo_neve_1,
                R.drawable.ic_meteo_neve_2, R.drawable.ic_meteo_neve_3,
                R.drawable.ic_meteo_neve_4, R.drawable.ic_meteo_neve_5,
            )
            TEMPORALE -> intArrayOf(
                R.drawable.ic_meteo_temporale_0, R.drawable.ic_meteo_temporale_1,
                R.drawable.ic_meteo_temporale_2, R.drawable.ic_meteo_temporale_3,
                R.drawable.ic_meteo_temporale_4, R.drawable.ic_meteo_temporale_5,
            )
        }

    companion object {
        /** Quanti fotogrammi ha ogni condizione: il widget conta su questo numero. */
        const val FOTOGRAMMI = 6
    }
}

data class Condizione(val descrizione: String, val famiglia: Famiglia)

/**
 * Codici meteo WMO cosi' come li distribuisce Open-Meteo.
 *
 * Le descrizioni dei codici presenti sulla pagina costalonga.org/meteo/ sono
 * ripetute parola per parola. I codici che la pagina non elenca (gelo,
 * granelli e rovesci di neve) seguono la definizione WMO, per non lasciarli
 * come "Variabile".
 */
fun condizioneDi(codice: Int, diGiorno: Boolean): Condizione {
    fun sereno() = if (diGiorno) Famiglia.SOLE else Famiglia.LUNA
    fun velato() = if (diGiorno) Famiglia.SOLE_NUVOLE else Famiglia.LUNA_NUVOLE
    return when (codice) {
        0 -> Condizione("Sereno", sereno())
        1 -> Condizione("Prevalentemente sereno", velato())
        2 -> Condizione("Parzialmente nuvoloso", velato())
        3 -> Condizione("Coperto", Famiglia.NUVOLE)
        45 -> Condizione("Nebbia", Famiglia.NEBBIA)
        48 -> Condizione("Nebbia con brina", Famiglia.NEBBIA)
        51 -> Condizione("Pioviggine debole", Famiglia.PIOVIGGINE)
        53 -> Condizione("Pioviggine", Famiglia.PIOVIGGINE)
        55 -> Condizione("Pioviggine intensa", Famiglia.PIOGGIA)
        56 -> Condizione("Pioviggine gelata debole", Famiglia.PIOVIGGINE)
        57 -> Condizione("Pioviggine gelata intensa", Famiglia.PIOGGIA)
        61 -> Condizione("Pioggia debole", Famiglia.PIOVIGGINE)
        63 -> Condizione("Pioggia", Famiglia.PIOGGIA)
        65 -> Condizione("Pioggia intensa", Famiglia.PIOGGIA)
        66 -> Condizione("Pioggia gelata debole", Famiglia.PIOGGIA)
        67 -> Condizione("Pioggia gelata intensa", Famiglia.PIOGGIA)
        71 -> Condizione("Neve debole", Famiglia.NEVE)
        73 -> Condizione("Neve", Famiglia.NEVE)
        75 -> Condizione("Neve intensa", Famiglia.NEVE)
        77 -> Condizione("Granelli di neve", Famiglia.NEVE)
        80 -> Condizione("Rovesci deboli", Famiglia.PIOGGIA)
        81 -> Condizione("Rovesci", Famiglia.PIOGGIA)
        82 -> Condizione("Rovesci forti", Famiglia.TEMPORALE)
        85 -> Condizione("Rovesci di neve deboli", Famiglia.NEVE)
        86 -> Condizione("Rovesci di neve intensi", Famiglia.NEVE)
        95 -> Condizione("Temporale", Famiglia.TEMPORALE)
        96 -> Condizione("Temporale con grandine", Famiglia.TEMPORALE)
        99 -> Condizione("Forte temporale", Famiglia.TEMPORALE)
        else -> Condizione("Variabile", Famiglia.NUVOLE)
    }
}
