package org.costalonga.polso.motore.importa

import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.Metrica
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.PuntoPercorso
import org.w3c.dom.Element
import java.io.InputStream
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tracce GPX 1.1 (topografix.com/GPX/1/1) e TCX v2 (Garmin Training Center
 * Database). La frequenza cardiaca si legge dall'estensione Garmin
 * TrackPointExtension (gpxtpx:hr) e da HeartRateBpm nei TCX.
 */
object Tracce {
    private fun xml(input: InputStream) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Nessuna entità esterna: il file viene da fuori.
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(input)

    private fun Element.figli(nome: String): List<Element> {
        val nl = getElementsByTagNameNS("*", nome)
        return (0 until nl.length).map { nl.item(it) as Element }
    }

    private fun Element.testo(nome: String): String? = figli(nome).firstOrNull()?.textContent?.trim()

    /** Distanza in metri fra due punti (formula dell'emisenoverso, raggio 6 371 008,8 m). */
    fun distanza(a: PuntoPercorso, b: PuntoPercorso): Double {
        val r = 6_371_008.8
        val f1 = Math.toRadians(a.lat)
        val f2 = Math.toRadians(b.lat)
        val df = f2 - f1
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(df / 2) * sin(df / 2) + cos(f1) * cos(f2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun lunghezza(p: List<PuntoPercorso>): Double = p.zipWithNext().sumOf { (a, b) -> distanza(a, b) }

    /** Dislivello positivo con soglia di 3 m per non sommare il rumore dell'altimetro. */
    fun dislivello(p: List<PuntoPercorso>, soglia: Double = 3.0): Double? {
        val alt = p.mapNotNull { it.altitudine }
        if (alt.size < 2) return null
        var su = 0.0
        var rif = alt.first()
        for (a in alt.drop(1)) {
            if (a - rif >= soglia) { su += a - rif; rif = a } else if (rif - a >= soglia) rif = a
        }
        return su
    }

    private fun sportDa(s: String?): String = when (s?.lowercase()) {
        "running", "run", "corsa" -> "corsa"
        "biking", "cycling", "ciclismo" -> "ciclismo"
        "walking", "walk", "camminata" -> "camminata"
        "hiking", "escursionismo" -> "escursionismo"
        "swimming" -> "nuoto_libero"
        else -> "altro"
    }

    fun gpx(input: InputStream, nomeFile: String): RisultatoImportazione {
        val doc = try { xml(input) } catch (e: Exception) { return RisultatoImportazione(errori = listOf("File GPX non leggibile: ${e.javaClass.simpleName}.")) }
        val r = doc.documentElement
        val allenamenti = mutableListOf<Allenamento>()
        val misure = mutableListOf<Misura>()
        val errori = mutableListOf<String>()
        r.figli("trk").forEachIndexed { i, trk ->
            val punti = mutableListOf<PuntoPercorso>()
            trk.figli("trkpt").forEach { pt ->
                val t = pt.testo("time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@forEach
                val lat = pt.getAttribute("lat").toDoubleOrNull() ?: return@forEach
                val lon = pt.getAttribute("lon").toDoubleOrNull() ?: return@forEach
                punti += PuntoPercorso(t, lat, lon, pt.testo("ele")?.toDoubleOrNull())
                pt.testo("hr")?.toDoubleOrNull()?.let { misure += Misura(Metrica.FREQUENZA_CARDIACA.codice, t, t, it, "bpm", null, "file", nomeFile) }
            }
            if (punti.size < 2) { errori += "Traccia ${i + 1}: meno di due punti con orario, ignorata."; return@forEachIndexed }
            punti.sortBy { it.istante }
            allenamenti += Allenamento(
                inizio = punti.first().istante, fine = punti.last().istante, sport = sportDa(trk.testo("type")),
                titolo = (trk.testo("name") ?: nomeFile) + " · distanza e dislivello calcolati dal percorso",
                fonte = "file", origine = nomeFile, idEsterno = "$nomeFile#trk$i",
                distanzaM = lunghezza(punti), dislivelloM = dislivello(punti), percorso = punti,
            )
        }
        if (allenamenti.isEmpty() && errori.isEmpty()) errori += "Nessuna traccia (trk) nel file."
        return RisultatoImportazione(misure, emptyList(), allenamenti, allenamenti.size, errori)
    }

    fun tcx(input: InputStream, nomeFile: String): RisultatoImportazione {
        val doc = try { xml(input) } catch (e: Exception) { return RisultatoImportazione(errori = listOf("File TCX non leggibile: ${e.javaClass.simpleName}.")) }
        val allenamenti = mutableListOf<Allenamento>()
        val misure = mutableListOf<Misura>()
        doc.documentElement.figli("Activity").forEachIndexed { i, act ->
            val giri = act.figli("Lap")
            val punti = mutableListOf<PuntoPercorso>()
            val tempi = mutableListOf<Long>()
            act.figli("Trackpoint").forEach { tp ->
                val t = tp.testo("Time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@forEach
                tempi += t
                val lat = tp.testo("LatitudeDegrees")?.toDoubleOrNull()
                val lon = tp.testo("LongitudeDegrees")?.toDoubleOrNull()
                if (lat != null && lon != null) punti += PuntoPercorso(t, lat, lon, tp.testo("AltitudeMeters")?.toDoubleOrNull())
                tp.figli("HeartRateBpm").firstOrNull()?.testo("Value")?.toDoubleOrNull()?.let { misure += Misura(Metrica.FREQUENZA_CARDIACA.codice, t, t, it, "bpm", null, "file", nomeFile) }
            }
            val inizio = giri.firstOrNull()?.getAttribute("StartTime")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: tempi.minOrNull() ?: return@forEachIndexed
            val durataGiri = giri.sumOf { it.testo("TotalTimeSeconds")?.toDoubleOrNull() ?: 0.0 }
            val fine = maxOf(tempi.maxOrNull() ?: inizio, inizio + (durataGiri * 1000).toLong())
            val dist = giri.mapNotNull { g -> g.childNodes.let { nl -> (0 until nl.length).map { nl.item(it) }.firstOrNull { it.localName == "DistanceMeters" }?.textContent?.toDoubleOrNull() } }
            allenamenti += Allenamento(
                inizio = inizio, fine = fine, sport = sportDa(act.getAttribute("Sport")), titolo = nomeFile,
                fonte = "file", origine = nomeFile, idEsterno = act.testo("Id") ?: "$nomeFile#act$i",
                distanzaM = dist.takeIf { it.isNotEmpty() }?.sum(),
                calorieKcal = giri.mapNotNull { it.testo("Calories")?.toDoubleOrNull() }.takeIf { it.isNotEmpty() }?.sum(),
                dislivelloM = dislivello(punti), percorso = punti,
            )
        }
        return RisultatoImportazione(misure, emptyList(), allenamenti, allenamenti.size, if (allenamenti.isEmpty()) listOf("Nessuna attività (Activity) nel file.") else emptyList())
    }
}
