package org.costalonga.fattidelgiorno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Copia, condivisione e apertura dei collegamenti: solo funzioni di Android, niente accessi ai social. */
object Condivisione {

    fun intentCondividi(testo: String, titolo: String = "Condividi"): Intent {
        val invio = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, testo)
        }
        return Intent.createChooser(invio, titolo)
    }

    fun condividi(context: Context, testo: String, titolo: String = "Condividi") {
        context.startActivity(intentCondividi(testo, titolo))
    }

    fun copia(context: Context, etichetta: String, testo: String): Boolean {
        val appunti = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        appunti.setPrimaryClip(ClipData.newPlainText(etichetta, testo))
        // Da Android 13 il sistema avvisa da solo; prima no.
        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(context, "$etichetta copiato", Toast.LENGTH_SHORT).show()
        return true
    }

    fun intentApri(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    fun apri(context: Context, url: String): Boolean = runCatching {
        context.startActivity(intentApri(url))
        true
    }.getOrDefault(false)
}
