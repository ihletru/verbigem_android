package com.verbigem.app.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.verbigem.app.data.crypto.E2eCrypto
import com.verbigem.app.data.crypto.E2eKeyStore
import kotlinx.coroutines.tasks.await
import java.security.KeyPair
import java.util.Base64

/**
 * Tożsamość E2E konta — patrz `docs/czat-e2e.md` §5 i §6.
 *
 * Trzy miejsca, w których ta tożsamość żyje:
 *  * `usersPublic/{uid}.chatKey` — klucz **publiczny**, czytany przez rozmówców.
 *  * lokalny sejf ([E2eKeyStore]) — klucz prywatny, zaszyfrowany kluczem z Keystore.
 *  * `users/{uid}/chatKeyBackup/main` — kopia klucza prywatnego zaszyfrowana
 *    hasłem odzyskiwania, **którego serwer nie zna**.
 *
 * ⚠️ Trzecia pozycja jest całym sensem tego, że „klucz konta na serwerze" nadal
 * jest E2E. Gdyby trafiał tam klucz jawny, punkt 4 byłby teatrem — dlatego
 * publikacja idzie wyłącznie przez [E2eCrypto.encryptPrivateKeyForBackup],
 * a ta funkcja nie ma wariantu „bez hasła".
 *
 * ⚠️ Hasła NIGDZIE nie zapisujemy — ani w preferencjach, ani w Room. Jest
 * używane w tej jednej chwili i zapominane. Zapomniane hasło = brak dostępu do
 * historii na nowym urządzeniu i nie ma mechanizmu odzyskania, bo nie ma czym
 * odszyfrować kopii. To jest świadoma cena wybranego modelu, nie usterka.
 */
class ChatKeyRepository(context: Context) {

    /** Co wiemy o tożsamości konta na TYM urządzeniu. */
    sealed interface IdentityState {
        /** Klucz jest lokalnie i gotowy do użycia. */
        data class Ready(val keyPair: KeyPair) : IdentityState

        /** Konto ma kopię na serwerze, ale to urządzenie nie ma klucza — potrzebne hasło. */
        data object NeedsPassphrase : IdentityState

        /** Konto nie ma jeszcze tożsamości. Czat działa jawnym tekstem (jak przed zmianą). */
        data object NotConfigured : IdentityState
    }

    /** Wynik próby odtworzenia klucza z kopii zapasowej. */
    sealed interface RestoreResult {
        data class Ok(val keyPair: KeyPair) : RestoreResult

        /** Hasło nie pasuje — GCM odrzucił szyfrogram (AEAD nie daje „prawie dobrze"). */
        data object WrongPassphrase : RestoreResult

        /** Brak kopii na koncie (konto sprzed E2E albo kopia nigdy nie powstała). */
        data object NoBackup : RestoreResult

        data class Error(val message: String) : RestoreResult
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val store = E2eKeyStore(context)

    // ------------------------------------------------------------------ stan

    suspend fun state(uid: String): IdentityState {
        if (uid.isBlank()) return IdentityState.NotConfigured
        store.identityKeyPair(uid)?.let { return IdentityState.Ready(it) }
        return if (hasBackup(uid)) IdentityState.NeedsPassphrase else IdentityState.NotConfigured
    }

    /** Klucz lokalny, jeśli jest — bez sieci. */
    fun localIdentity(uid: String): KeyPair? = store.identityKeyPair(uid)

    suspend fun hasBackup(uid: String): Boolean = try {
        backupDoc(uid).get().await().exists()
    } catch (e: Exception) {
        // Offline nie może udawać „brak kopii" — wtedy UI zaproponowałby utworzenie
        // nowej tożsamości i użytkownik straciłby historię, mając ją na serwerze.
        Log.w(TAG, "Nie mogę sprawdzić kopii klucza dla $uid", e)
        false
    }

    // ------------------------------------------------------------ tworzenie

    /**
     * Zakłada tożsamość: generuje parę, zapisuje ją lokalnie, publikuje klucz
     * publiczny i kopię zapasową pod [passphrase].
     *
     * Kolejność jest istotna: najpierw lokalnie, potem publicznie. Gdyby padło
     * w połowie, wolimy mieć klucz u siebie bez wpisu w `usersPublic` (rozmówcy
     * po prostu jeszcze nie mogą pisać szyfrowanie) niż klucz publiczny bez
     * klucza prywatnego (wiadomości nieczytelne dla nas samych).
     */
    suspend fun createIdentity(uid: String, passphrase: CharArray): KeyPair {
        require(uid.isNotBlank()) { "uid wymagane" }
        val pair = E2eCrypto.generateKeyPair()
        store.saveIdentityKeyPair(uid, pair)
        publishPublicKey(uid, E2eCrypto.encodePublicKey(pair.public))
        publishBackup(uid, E2eCrypto.encryptPrivateKeyForBackup(pair.private, passphrase))
        return pair
    }

    /**
     * Odtwarza tożsamość z kopii zapasowej na tym urządzeniu (nowy telefon).
     *
     * ⚠️ Nie generuje nowej pary, gdy hasło nie pasuje — zwraca [RestoreResult.WrongPassphrase].
     * Ciche wygenerowanie nowej tożsamości „na wypadek" byłoby najgorszym możliwym
     * zachowaniem: użytkownik straciłby historię, myśląc, że ją odzyskał.
     */
    suspend fun restoreIdentity(uid: String, passphrase: CharArray): RestoreResult {
        val snapshot = try {
            backupDoc(uid).get().await()
        } catch (e: Exception) {
            Log.w(TAG, "Nie mogę odczytać kopii klucza dla $uid", e)
            return RestoreResult.Error(e.message ?: "blad sieci")
        }
        if (!snapshot.exists()) return RestoreResult.NoBackup

        val blob = snapshot.getString("blob")?.let { Base64.getDecoder().decode(it) }
            ?: return RestoreResult.NoBackup

        val pair = try {
            val privateKey = E2eCrypto.decryptPrivateKeyFromBackup(blob, passphrase)
            // Klucz publiczny bierzemy z serwera, nie z kopii: to on jest tym,
            // który znają rozmówcy, więc tożsamość musi być z nim spójna.
            val publicBytes = publicKeyOf(uid)
                ?: return RestoreResult.Error("Konto nie ma opublikowanego klucza publicznego")
            KeyPair(E2eCrypto.decodePublicKey(publicBytes), privateKey)
        } catch (e: Exception) {
            // GCM nie odróżnia „złe hasło" od „uszkodzone dane" — jedno i drugie
            // kończy się tym samym wyjątkiem. Dla użytkownika to jedno: hasło nie pasuje.
            Log.w(TAG, "Nie udało się odszyfrować kopii klucza dla $uid", e)
            return RestoreResult.WrongPassphrase
        }

        store.saveIdentityKeyPair(uid, pair)
        return RestoreResult.Ok(pair)
    }

    /**
     * Ustawia (albo zmienia) hasło odzyskiwania dla istniejącej tożsamości.
     * Kopia jest nadpisywana — stare hasło przestaje działać, i to jest zamierzone.
     */
    suspend fun setPassphrase(uid: String, passphrase: CharArray) {
        val pair = store.identityKeyPair(uid)
            ?: throw IllegalStateException("Brak tozsamosci na tym urzadzeniu — nie ma czego kopiowac")
        publishBackup(uid, E2eCrypto.encryptPrivateKeyForBackup(pair.private, passphrase))
    }

    /**
     * Usuwa tożsamość z tego urządzenia. Kopii na serwerze **nie rusza** —
     * użytkownik może wrócić i odtworzyć klucz hasłem.
     */
    fun forgetLocal(uid: String) = store.forget(uid)

    // -------------------------------------------------------------- publikacja

    suspend fun publishPublicKey(uid: String, publicKeyBytes: ByteArray) {
        firestore.collection("usersPublic").document(uid).set(
            mapOf(
                "uid" to uid,
                "chatKey" to mapOf(
                    "v" to E2eCrypto.VERSION,
                    "pub" to Base64.getEncoder().encodeToString(publicKeyBytes),
                    "updatedAt" to System.currentTimeMillis()
                )
            ),
            SetOptions.merge()
        ).await()
    }

    private suspend fun publishBackup(uid: String, blob: ByteArray) {
        backupDoc(uid).set(
            mapOf(
                "v" to E2eCrypto.BACKUP_VERSION.toInt(),
                "blob" to Base64.getEncoder().encodeToString(blob),
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Klucz publiczny rozmówcy. `null` = konto nie ma jeszcze tożsamości E2E
     * (albo nie ma go w bazie) — wtedy wysyłamy jawnym tekstem, tak jak przed
     * wprowadzeniem szyfrowania. To jest cała migracja: brak klucza, brak szyfrowania.
     */
    suspend fun publicKeyOf(uid: String): ByteArray? {
        if (uid.isBlank()) return null
        return try {
            val snapshot = firestore.collection("usersPublic").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val chatKey = snapshot.get("chatKey") as? Map<String, Any?> ?: return null
            val pub = chatKey["pub"] as? String ?: return null
            Base64.getDecoder().decode(pub)
        } catch (e: Exception) {
            Log.w(TAG, "Nie mogę odczytać klucza publicznego $uid", e)
            null
        }
    }

    private fun backupDoc(uid: String) =
        firestore.collection("users").document(uid).collection("chatKeyBackup").document("main")

    companion object {
        private const val TAG = "ChatKeyRepository"
    }
}
