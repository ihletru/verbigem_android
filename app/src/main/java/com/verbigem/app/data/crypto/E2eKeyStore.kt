package com.verbigem.app.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Lokalny sejf na klucz prywatny tożsamości E2E — **jedyne miejsce z API Androida**
 * w całym module kryptografii ([E2eCrypto] jest czystym JCA i dlatego da się
 * testować na JVM).
 *
 * Model: klucz tożsamości powstaje w oprogramowaniu (musi, bo robimy jego kopię
 * zapasową — patrz `docs/czat-e2e.md` §5), a w spoczynku leży zaszyfrowany kluczem
 * AES-256-GCM **z Android Keystore**. Ten klucz AES nigdy nie opuszcza urządzenia
 * i nie da się go wyeksportować, więc sam plik preferencji jest bezużyteczny dla
 * kogoś, kto go podejrzy (root, kopia zapasowa, forensic image bez odblokowania).
 *
 * ⚠️ **Wpisy są per konto (`uid` w kluczu), nie per urządzenie.** Aplikacja od
 * v1.0.68 trzyma osobną bazę Room na konto i dokładnie ten sam błąd popełniono
 * wtedy ze wspólnym znacznikiem synchronizacji: konto B widziało dane konta A.
 * Klucz AES w Keystore JEST wspólny dla urządzenia (to tylko sejf), ale
 * szyfrogram klucza prywatnego musi być osobny dla każdego `uid`.
 *
 * ⚠️ **Bez `allowBackup`-owej migracji:** Keystore nie przenosi się z kopią
 * zapasową, więc po przeniesieniu aplikacji na nowy telefon wpisy tu są
 * nieczytelne. To NIE jest błąd — nowe urządzenie i tak odtwarza tożsamość
 * z kopii zaszyfrowanej hasłem (`users/{uid}/chatKeyBackup`). Patrz pomoc w czacie.
 */
class E2eKeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ klucze

    /**
     * Wczytuje parę tożsamości dla [uid] albo `null`, gdy to urządzenie jej nie ma
     * (pierwsze uruchomienie konta, nowy telefon, wyczyszczone dane aplikacji).
     *
     * `null` NIE jest błędem — to normalny stan, który wołający obsługuje przez
     * odtworzenie z kopii zapasowej albo wygenerowanie nowej pary.
     */
    fun identityKeyPair(uid: String): KeyPair? {
        if (uid.isBlank()) return null
        val privateKey = loadPrivateKey(uid) ?: return null
        val publicBytes = prefs.getString(publicEntry(uid), null)?.let { decode(it) } ?: return null
        return try {
            KeyPair(E2eCrypto.decodePublicKey(publicBytes), privateKey)
        } catch (e: Exception) {
            // Uszkodzony wpis jest gorszy niż brak wpisu — czyścimy i pozwalamy
            // wołającemu wygenerować nową tożsamość zamiast się wywalać.
            Log.w(TAG, "Nieczytelny klucz publiczny dla $uid — czyszczę wpis", e)
            forget(uid)
            null
        }
    }

    /** Zapisuje parę tożsamości dla [uid]. Klucz publiczny jawnie, prywatny w szyfrogramie. */
    fun saveIdentityKeyPair(uid: String, pair: KeyPair) {
        if (uid.isBlank()) return
        val key = deviceKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val blob = cipher.iv + cipher.doFinal(E2eCrypto.encodePrivateKey(pair.private))
        prefs.edit()
            .putString(privateEntry(uid), encode(blob))
            .putString(publicEntry(uid), encode(E2eCrypto.encodePublicKey(pair.public)))
            .apply()
    }

    /** Czy to urządzenie ma tożsamość dla [uid] (bez odszyfrowywania). */
    fun hasIdentity(uid: String): Boolean =
        uid.isNotBlank() && prefs.contains(privateEntry(uid)) && prefs.contains(publicEntry(uid))

    /**
     * Usuwa lokalną tożsamość. **Nie** usuwa kopii na serwerze — użytkownik ma
     * prawo wrócić na to urządzenie i odtworzyć klucz hasłem.
     */
    fun forget(uid: String) {
        if (uid.isBlank()) return
        prefs.edit().remove(privateEntry(uid)).remove(publicEntry(uid)).apply()
    }

    // ------------------------------------------------------------- wnętrze

    private fun loadPrivateKey(uid: String): PrivateKey? {
        val raw = prefs.getString(privateEntry(uid), null) ?: return null
        return try {
            val blob = decode(raw)
            val iv = blob.copyOfRange(0, E2eCrypto.IV_BYTES)
            val body = blob.copyOfRange(E2eCrypto.IV_BYTES, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(E2eCrypto.GCM_TAG_BITS, iv))
            }
            E2eCrypto.decodePrivateKey(cipher.doFinal(body))
        } catch (e: Exception) {
            // Typowe przyczyny: nowy telefon (Keystore bez klucza), wyczyszczone dane
            // aplikacji, przywrócona kopia zapasowa. Wszystkie znaczą to samo:
            // tożsamość trzeba odtworzyć z kopii zaszyfrowanej hasłem.
            Log.w(TAG, "Nie mogę odszyfrować klucza tożsamości dla $uid", e)
            null
        }
    }

    /** Klucz AES w Keystore — tworzony przy pierwszym użyciu, potem tylko czytany. */
    private fun deviceKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // Losowy IV wymuszony przez system — nie chcemy mieć możliwości
                // przypadkowego powtórzenia IV, bo przy GCM to natychmiastowa katastrofa.
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun privateEntry(uid: String) = "priv_$uid"
    private fun publicEntry(uid: String) = "pub_$uid"

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        private const val TAG = "E2eKeyStore"
        private const val PREFS = "verbigem_e2e_keys"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "verbigem_e2e_device_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
