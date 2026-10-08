package org.costalonga.polso.motore

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Statistica descrittiva e inferenziale, scritta a mano per non dipendere da
 * librerie esterne e poterla provare riga per riga. I valori mancanti non
 * entrano mai nelle formule: chi chiama passa solo osservazioni reali.
 */
object Stat {
    fun media(x: List<Double>): Double? = if (x.isEmpty()) null else x.sum() / x.size

    /** Quantile con interpolazione lineare (tipo 7, quello di R ed Excel). */
    fun quantile(x: List<Double>, p: Double): Double? {
        if (x.isEmpty()) return null
        val s = x.sorted()
        val h = (s.size - 1) * p
        val lo = floor(h).toInt()
        val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (h - lo) * (s[hi] - s[lo])
    }

    fun mediana(x: List<Double>): Double? = quantile(x, 0.5)

    /** Deviazione standard campionaria (denominatore n−1). */
    fun devStd(x: List<Double>): Double? {
        if (x.size < 2) return null
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / (x.size - 1))
    }

    /** Deviazione assoluta mediana, non scalata. */
    fun mad(x: List<Double>): Double? {
        val m = mediana(x) ?: return null
        return mediana(x.map { abs(it - m) })
    }

    fun iqr(x: List<Double>): Double? {
        val q1 = quantile(x, 0.25) ?: return null
        return quantile(x, 0.75)!! - q1
    }

    /** Coefficiente di variazione in percentuale. */
    fun cv(x: List<Double>): Double? {
        val m = media(x) ?: return null
        val s = devStd(x) ?: return null
        return if (m == 0.0) null else 100.0 * s / m
    }

    /**
     * Media mobile centrata a sinistra (gli ultimi [finestra] giorni incluso il
     * corrente). Restituisce null se nella finestra ci sono meno di [minimo]
     * valori presenti: i giorni mancanti non vengono riempiti.
     */
    fun mediaMobile(serie: List<Double?>, finestra: Int, minimo: Int): List<Double?> =
        serie.indices.map { i ->
            val w = serie.subList(maxOf(0, i - finestra + 1), i + 1).filterNotNull()
            if (w.size >= minimo) w.average() else null
        }

    /** Autocorrelazione al ritardo 1 di una serie senza buchi. */
    fun autocorrelazione1(x: List<Double>): Double {
        if (x.size < 4) return 0.0
        val m = x.average()
        val den = x.sumOf { (it - m) * (it - m) }
        if (den == 0.0) return 0.0
        var num = 0.0
        for (i in 1 until x.size) num += (x[i] - m) * (x[i - 1] - m)
        return num / den
    }

    fun ranghi(x: List<Double>): List<Double> {
        val ordine = x.indices.sortedBy { x[it] }
        val r = DoubleArray(x.size)
        var i = 0
        while (i < ordine.size) {
            var j = i
            while (j + 1 < ordine.size && x[ordine[j + 1]] == x[ordine[i]]) j++
            val medio = (i + j) / 2.0 + 1
            for (k in i..j) r[ordine[k]] = medio
            i = j + 1
        }
        return r.toList()
    }

    fun pearson(x: List<Double>, y: List<Double>): Double? {
        require(x.size == y.size)
        if (x.size < 3) return null
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in x.indices) {
            sxy += (x[i] - mx) * (y[i] - my)
            sxx += (x[i] - mx) * (x[i] - mx)
            syy += (y[i] - my) * (y[i] - my)
        }
        if (sxx == 0.0 || syy == 0.0) return null
        return sxy / sqrt(sxx * syy)
    }

    fun spearman(x: List<Double>, y: List<Double>): Double? = pearson(ranghi(x), ranghi(y))

    data class Correlazione(
        val rho: Double,
        val n: Int,
        /** Numerosità efficace dopo la correzione per autocorrelazione. */
        val nEfficace: Double,
        val p: Double,
        /** Intervallo di confidenza al 95% (trasformata di Fisher su n efficace). */
        val ic95: Pair<Double, Double>,
    )

    /**
     * Correlazione di Spearman fra due serie appaiate nel tempo. Il p-value
     * usa una numerosità efficace ridotta per l'autocorrelazione delle due
     * serie (approssimazione di Bartlett: n·(1−r₁ˣr₁ʸ)/(1+r₁ˣr₁ʸ)), perché
     * giorni consecutivi non sono osservazioni indipendenti.
     */
    fun correlazione(x: List<Double>, y: List<Double>): Correlazione? {
        val rho = spearman(x, y) ?: return null
        val n = x.size
        val a = autocorrelazione1(x).coerceIn(0.0, 0.95) * autocorrelazione1(y).coerceIn(0.0, 0.95)
        val nEff = (n * (1 - a) / (1 + a)).coerceIn(3.0, n.toDouble())
        val df = nEff - 2
        val p = if (abs(rho) >= 1.0) 0.0 else {
            val t = rho * sqrt(df / (1 - rho * rho))
            pValoreT(t, df)
        }
        val z = 0.5 * ln((1 + rho.coerceIn(-0.999999, 0.999999)) / (1 - rho.coerceIn(-0.999999, 0.999999)))
        val se = 1 / sqrt(maxOf(nEff - 3, 1.0))
        val lo = kotlin.math.tanh(z - 1.96 * se)
        val hi = kotlin.math.tanh(z + 1.96 * se)
        return Correlazione(rho, n, nEff, p, lo to hi)
    }

    /** p-value bilaterale della t di Student con [df] gradi di libertà. */
    fun pValoreT(t: Double, df: Double): Double {
        if (df <= 0) return 1.0
        val xx = df / (df + t * t)
        return betaIncompletaRegolarizzata(xx, df / 2, 0.5).coerceIn(0.0, 1.0)
    }

    /** Funzione di ripartizione della normale standard (Abramowitz e Stegun 26.2.17). */
    fun normaleCdf(z: Double): Double {
        val t = 1 / (1 + 0.2316419 * abs(z))
        val d = 0.3989422804014327 * exp(-z * z / 2)
        val p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
        return if (z >= 0) 1 - p else p
    }

    private fun lnGamma(x: Double): Double {
        val c = doubleArrayOf(76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5)
        var y = x
        val tmp = x + 5.5 - (x + 0.5) * ln(x + 5.5)
        var ser = 1.000000000190015
        for (cj in c) { y += 1; ser += cj / y }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    fun betaIncompletaRegolarizzata(x: Double, a: Double, b: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val bt = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1 - x))
        return if (x < (a + 1) / (a + b + 2)) bt * frazioneContinua(x, a, b) / a
        else 1 - bt * frazioneContinua(1 - x, b, a) / b
    }

    private fun frazioneContinua(x: Double, a: Double, b: Double): Double {
        val eps = 3e-14
        val fpmin = 1e-300
        val qab = a + b
        val qap = a + 1
        val qam = a - 1
        var c = 1.0
        var d = 1 - qab * x / qap
        if (abs(d) < fpmin) d = fpmin
        d = 1 / d
        var h = d
        for (m in 1..300) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1 + aa * d; if (abs(d) < fpmin) d = fpmin
            c = 1 + aa / c; if (abs(c) < fpmin) c = fpmin
            d = 1 / d; h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1 + aa * d; if (abs(d) < fpmin) d = fpmin
            c = 1 + aa / c; if (abs(c) < fpmin) c = fpmin
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < eps) break
        }
        return h
    }

    /** Correzione di Benjamini–Hochberg: restituisce i q-value nello stesso ordine. */
    fun benjaminiHochberg(p: List<Double>): List<Double> {
        val n = p.size
        if (n == 0) return emptyList()
        val ordine = p.indices.sortedBy { p[it] }
        val q = DoubleArray(n)
        var minimo = 1.0
        for (k in n - 1 downTo 0) {
            val i = ordine[k]
            minimo = minOf(minimo, p[i] * n / (k + 1))
            q[i] = minimo.coerceAtMost(1.0)
        }
        return q.toList()
    }

    data class Tendenza(
        /** Pendenza di Theil–Sen per unità di x (qui: per giorno). */
        val pendenza: Double,
        val intercetta: Double,
        /** Statistica di Mann–Kendall e p-value corretto per autocorrelazione. */
        val s: Int,
        val p: Double,
        val n: Int,
    )

    /**
     * Tendenza robusta: pendenza di Theil–Sen (mediana delle pendenze fra
     * coppie) e test di Mann–Kendall. La varianza di S è moltiplicata per
     * 1 + 2·r₁·(n−1)/n quando i residui hanno autocorrelazione positiva al
     * ritardo 1 (versione semplificata della correzione di Hamed e Rao).
     */
    fun tendenza(x: List<Double>, y: List<Double>): Tendenza? {
        val n = x.size
        if (n < 3) return null
        val pendenze = ArrayList<Double>(n * (n - 1) / 2)
        var s = 0
        for (i in 0 until n - 1) for (j in i + 1 until n) {
            if (x[j] != x[i]) pendenze += (y[j] - y[i]) / (x[j] - x[i])
            s += segno(y[j] - y[i]) * segno(x[j] - x[i])
        }
        if (pendenze.isEmpty()) return null
        val b = mediana(pendenze)!!
        val a = mediana(x.indices.map { y[it] - b * x[it] })!!
        // Varianza di S con correzione per i pareggi.
        val pareggi = y.groupingBy { it }.eachCount().values.filter { it > 1 }
        var varS = (n * (n - 1.0) * (2 * n + 5) - pareggi.sumOf { it * (it - 1.0) * (2 * it + 5) }) / 18.0
        val residui = x.indices.map { y[it] - (a + b * x[it]) }
        val r1 = autocorrelazione1(residui)
        if (r1 > 0) varS *= 1 + 2 * r1 * (n - 1.0) / n
        val z = when {
            varS <= 0 -> 0.0
            s > 0 -> (s - 1) / sqrt(varS)
            s < 0 -> (s + 1) / sqrt(varS)
            else -> 0.0
        }
        val p = 2 * (1 - normaleCdf(abs(z)))
        return Tendenza(b, a, s, p.coerceIn(0.0, 1.0), n)
    }

    private fun segno(v: Double): Int = if (v > 0) 1 else if (v < 0) -1 else 0

    /**
     * Intervallo di confidenza al 95% della differenza fra due medie con
     * bootstrap a seme fisso (risultato riproducibile).
     */
    fun bootstrapDifferenzaMedie(a: List<Double>, b: List<Double>, ripetizioni: Int = 2000, seme: Int = 7): Pair<Double, Double>? {
        if (a.size < 3 || b.size < 3) return null
        val r = Random(seme)
        val d = DoubleArray(ripetizioni) {
            var sa = 0.0; repeat(a.size) { sa += a[r.nextInt(a.size)] }
            var sb = 0.0; repeat(b.size) { sb += b[r.nextInt(b.size)] }
            sa / a.size - sb / b.size
        }
        d.sort()
        return d[(0.025 * (ripetizioni - 1)).toInt()] to d[(0.975 * (ripetizioni - 1)).toInt()]
    }

    /** Media circolare di orari espressi in minuti dalla mezzanotte. */
    fun mediaOraria(minuti: List<Int>): Double? {
        if (minuti.isEmpty()) return null
        val ang = minuti.map { it / 1440.0 * 2 * PI }
        val m = atan2(ang.sumOf { sin(it) }, ang.sumOf { cos(it) })
        val r = (m / (2 * PI) * 1440).let { if (it < 0) it + 1440 else it }
        return r
    }

    /** Deviazione standard circolare in minuti (orari attorno alla mezzanotte). */
    fun devStdOraria(minuti: List<Int>): Double? {
        if (minuti.size < 2) return null
        val ang = minuti.map { it / 1440.0 * 2 * PI }
        val c = ang.sumOf { cos(it) } / ang.size
        val s = ang.sumOf { sin(it) } / ang.size
        val rr = sqrt(c * c + s * s).coerceIn(1e-12, 1.0)
        return sqrt(-2 * ln(rr)) / (2 * PI) * 1440
    }

    /** Variazione percentuale; null se la base è zero o assente. */
    fun variazionePercentuale(nuovo: Double?, vecchio: Double?): Double? =
        if (nuovo == null || vecchio == null || vecchio == 0.0) null else 100.0 * (nuovo - vecchio) / abs(vecchio)

    /** Istogramma a classi di uguale ampiezza fra minimo e massimo. */
    fun istogramma(x: List<Double>, classi: Int): List<Pair<ClosedFloatingPointRange<Double>, Int>> {
        if (x.isEmpty()) return emptyList()
        val lo = x.min()
        val hi = x.max()
        if (lo == hi) return listOf((lo..hi) to x.size)
        val w = (hi - lo) / classi
        val conte = IntArray(classi)
        x.forEach { conte[minOf(((it - lo) / w).toInt(), classi - 1)]++ }
        return (0 until classi).map { (lo + it * w)..(lo + (it + 1) * w) to conte[it] }
    }
}
