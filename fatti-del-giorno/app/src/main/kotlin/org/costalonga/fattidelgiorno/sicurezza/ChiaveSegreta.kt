package org.costalonga.fattidelgiorno.sicurezza

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Custodisce la chiave personale dell'utente: la cifra con una chiave AES
 * generata nel Keystore di Android (che non lascia mai il dispositivo) e salva
 * solo il testo cifrato nelle preferenze private dell'app, escluse dal backup.
 */
class ChiaveSegreta(context: Context) {

    private val preferenze = context.applicationContext.getSharedPreferences("segreti", Context.MODE_PRIVATE)

    fun leggi(nome: String): String? {
        val salvato = preferenze.getString(nome, null) ?: return null
        return runCatching {
            val dati = Base64.decode(salvato, Base64.NO_WRAP)
            val iv = dati.copyOfRange(0, 12)
            val cifrato = dati.copyOfRange(12, dati.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, chiaveKeystore(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cifrato), Charsets.UTF_8)
        }.getOrNull()
    }

    fun salva(nome: String, valore: String) {
        if (valore.isBlank()) { cancella(nome); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, chiaveKeystore())
        val cifrato = cipher.doFinal(valore.trim().toByteArray(Charsets.UTF_8))
        val dati = cipher.iv + cifrato
        preferenze.edit().putString(nome, Base64.encodeToString(dati, Base64.NO_WRAP)).apply()
    }

    fun cancella(nome: String) {
        preferenze.edit().remove(nome).apply()
    }

    fun presente(nome: String): Boolean = preferenze.contains(nome)

    private fun chiaveKeystore(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generatore = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generatore.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generatore.generateKey()
    }

    companion object {
        private const val ALIAS = "fatti_del_giorno_segreti"
        const val GEMINI = "chiave_gemini"
    }
}
