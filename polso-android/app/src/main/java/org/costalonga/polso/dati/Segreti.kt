package org.costalonga.polso.dati

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credenziali (chiave del servizio di IA online) cifrate con una chiave AES
 * custodita in Android Keystore: la chiave non lascia mai il telefono e il
 * file cifrato è inutile altrove. Le credenziali non entrano nel backup:
 * dopo un ripristino vanno reinserite.
 */
class Segreti(context: Context) {
    private val file = File(context.noBackupFilesDir, "segreti.bin")
    private val alias = "polso_segreti"

    private fun chiave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return g.generateKey()
    }

    fun salvaChiaveOnline(valore: String?) {
        if (valore.isNullOrBlank()) { file.delete(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, chiave()) }
        val cifrato = c.doFinal(valore.toByteArray(Charsets.UTF_8))
        file.writeText(Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cifrato, Base64.NO_WRAP))
    }

    fun chiaveOnline(): String? = runCatching {
        if (!file.exists()) return null
        val (iv, dati) = file.readText().split(":").map { Base64.decode(it, Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, chiave(), GCMParameterSpec(128, iv)) }
        String(c.doFinal(dati), Charsets.UTF_8)
    }.getOrNull()

    fun presente(): Boolean = file.exists()
    fun cancella() { file.delete() }
}
