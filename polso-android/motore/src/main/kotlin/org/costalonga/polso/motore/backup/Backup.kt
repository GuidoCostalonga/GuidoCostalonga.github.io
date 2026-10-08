package org.costalonga.polso.motore.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.costalonga.polso.motore.Allenamento
import org.costalonga.polso.motore.Misura
import org.costalonga.polso.motore.SessioneSonno
import org.costalonga.polso.motore.VoceDiario
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class RiepilogoSalvato(val tipo: String, val da: String, val a: String, val testo: String, val motore: String, val creatoIl: Long)

/** Tutto ciò che serve a ricostruire l'app su un altro telefono. */
@Serializable
data class ContenutoBackup(
    val formato: Int = Backup.VERSIONE_FORMATO,
    val versioneSchema: Int,
    val versioneApp: String,
    val creatoIl: Long,
    val misure: List<Misura>,
    val sonni: List<SessioneSonno>,
    val allenamenti: List<Allenamento>,
    val diario: List<VoceDiario>,
    val impostazioni: Map<String, String>,
    val riepiloghi: List<RiepilogoSalvato> = emptyList(),
) {
    fun conteggi(): Map<String, Int> = mapOf("misure" to misure.size, "sonni" to sonni.size, "allenamenti" to allenamenti.size, "diario" to diario.size, "riepiloghi" to riepiloghi.size)
}

class PassphraseErrata : Exception("Passphrase errata o file alterato.")
class FormatoNonValido(msg: String) : Exception(msg)

/**
 * File di backup «.polso»: intestazione in chiaro + contenuto JSON compresso e
 * cifrato con AES-256-GCM. La chiave deriva dalla passphrase con
 * PBKDF2-HMAC-SHA256 e sale casuale: il file si ripristina su qualsiasi
 * telefono conoscendo la passphrase, e senza passphrase non è leggibile.
 * L'intestazione è autenticata (dati associati di GCM): alterarla rende il
 * file illeggibile.
 *
 *   "POLSOBK" | versione (1 byte) | iterazioni (int) | sale (16) | iv (12) | cifrato+tag
 */
object Backup {
    const val VERSIONE_FORMATO = 1
    const val ITERAZIONI_PREDEFINITE = 310_000
    const val LUNGHEZZA_MINIMA = 10
    private val MAGIA = "POLSOBK".toByteArray(Charsets.US_ASCII)
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun passphraseValida(p: CharArray): String? = when {
        p.size < LUNGHEZZA_MINIMA -> "La passphrase deve avere almeno $LUNGHEZZA_MINIMA caratteri."
        p.toSet().size < 4 -> "La passphrase è troppo ripetitiva."
        else -> null
    }

    private fun chiave(p: CharArray, sale: ByteArray, iterazioni: Int): SecretKeySpec {
        val spec = PBEKeySpec(p, sale, iterazioni, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun cifra(c: ContenutoBackup, passphrase: CharArray, iterazioni: Int = ITERAZIONI_PREDEFINITE, caso: SecureRandom = SecureRandom()): ByteArray {
        passphraseValida(passphrase)?.let { throw IllegalArgumentException(it) }
        val chiaro = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(json.encodeToString(ContenutoBackup.serializer(), c).toByteArray(Charsets.UTF_8)) } }.toByteArray()
        val sale = ByteArray(16).also { caso.nextBytes(it) }
        val iv = ByteArray(12).also { caso.nextBytes(it) }
        val intestazione = ByteArrayOutputStream().also { b ->
            DataOutputStream(b).use { it.write(MAGIA); it.writeByte(VERSIONE_FORMATO); it.writeInt(iterazioni); it.write(sale); it.write(iv) }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, chiave(passphrase, sale, iterazioni), GCMParameterSpec(128, iv))
        cipher.updateAAD(intestazione)
        return intestazione + cipher.doFinal(chiaro)
    }

    fun decifra(dati: ByteArray, passphrase: CharArray): ContenutoBackup {
        val ing = DataInputStream(ByteArrayInputStream(dati))
        val magia = ByteArray(MAGIA.size)
        try { ing.readFully(magia) } catch (e: Exception) { throw FormatoNonValido("File troppo corto.") }
        if (!magia.contentEquals(MAGIA)) throw FormatoNonValido("Non è un backup di Polso.")
        val versione = ing.readUnsignedByte()
        if (versione > VERSIONE_FORMATO) throw FormatoNonValido("Backup creato da una versione più recente dell'app (formato $versione).")
        val iterazioni = ing.readInt()
        if (iterazioni !in 1_000..10_000_000) throw FormatoNonValido("Intestazione non valida.")
        val sale = ByteArray(16).also { ing.readFully(it) }
        val iv = ByteArray(12).also { ing.readFully(it) }
        val lungIntest = MAGIA.size + 1 + 4 + 16 + 12
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, chiave(passphrase, sale, iterazioni), GCMParameterSpec(128, iv))
        cipher.updateAAD(dati, 0, lungIntest)
        val chiaro = try { cipher.doFinal(dati, lungIntest, dati.size - lungIntest) } catch (e: AEADBadTagException) { throw PassphraseErrata() }
        val testo = GZIPInputStream(ByteArrayInputStream(chiaro)).use { it.readBytes() }.toString(Charsets.UTF_8)
        return json.decodeFromString(ContenutoBackup.serializer(), testo)
    }

    /** Impronta del contenuto, per verificare che il ripristino abbia riportato tutto. */
    fun impronta(c: ContenutoBackup): String {
        val md = MessageDigest.getInstance("SHA-256")
        c.misure.sortedBy { it.chiave() }.forEach { md.update(it.chiave().toByteArray()) }
        c.sonni.sortedBy { it.chiave() }.forEach { md.update(it.chiave().toByteArray()) }
        c.allenamenti.sortedBy { it.chiave() }.forEach { md.update(it.chiave().toByteArray()) }
        c.diario.sortedBy { it.creataIl }.forEach { md.update("${it.tipo}|${it.istante}|${it.valore}|${it.valore2}|${it.testo}".toByteArray()) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
