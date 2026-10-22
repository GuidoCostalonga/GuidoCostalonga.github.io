package org.costalonga.fattidelgiorno

import android.content.Context
import org.costalonga.fattidelgiorno.ai.Gemini
import org.costalonga.fattidelgiorno.generazione.Piattaforma

/** Preferenze non segrete: finiscono nel backup. */
class Impostazioni(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("impostazioni", Context.MODE_PRIVATE)

    var wikipediaInglese: Boolean
        get() = p.getBoolean("wikipedia_inglese", true)
        set(v) = p.edit().putBoolean("wikipedia_inglese", v).apply()

    var wikidataItalia: Boolean
        get() = p.getBoolean("wikidata_italia", true)
        set(v) = p.edit().putBoolean("wikidata_italia", v).apply()

    var modelloGemini: String
        get() = p.getString("modello_gemini", Gemini.MODELLO_PREDEFINITO) ?: Gemini.MODELLO_PREDEFINITO
        set(v) = p.edit().putString("modello_gemini", v.trim().ifEmpty { Gemini.MODELLO_PREDEFINITO }).apply()

    var piattaformaPreferita: Piattaforma
        get() = runCatching { Piattaforma.valueOf(p.getString("piattaforma", null) ?: "") }.getOrDefault(Piattaforma.INSTAGRAM)
        set(v) = p.edit().putString("piattaforma", v.name).apply()
}
