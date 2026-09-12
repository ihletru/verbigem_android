package com.verbigem.app.data.crypto

import android.content.Context
import android.util.Base64

/**
 * Lokalny sejf na klucze publiczne ROZMÓWCÓW (TOFU) — odpowiednik obiektu
 * sklepu `peerKeys` (IndexedDB) z webappki (`e2eKeys.ts`). Patrz `czat-e2e.md` §6.
 *
 * ⚠️ Brak weryfikacji tożsamości (Milosz, §4): przy PIERWSZYM kontakcie
 * zapisujemy klucz publiczny rozmówcy, a przy każdym kolejnym sprawdzamy, czy
 * się nie zmienił. Zmiana = ostrzeżenie w wątku (nie blokuje czatu, jak w
 * WhatsAppie). Wykrywamy ją dopiero od drugiej rozmowy — przy pierwszym
 * kontakcie podmiana przez serwer jest niewykrywalna.
 *
 * Klucze są per URZĄDZENIE (a nie per konto), dokładnie jak w przeglądarce:
 * tożsamość rozmówcy jest ta sama niezależnie od tego, które z moich kont z
 * nim pisze, więc wspólny magazyn na urządzeniu jest w porządku.
 */
class E2ePeerKeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Rejestruje pierwszy zaobserwowany klucz rozmówcy i przy kolejnych
     * kontaktach wykrywa jego zmianę. Zwraca [PeerKeyStatus.NEW] (pierwszy raz),
     * [PeerKeyStatus.SAME] (zgodny) albo [PeerKeyStatus.CHANGED] (klucz się
     * zmienił — serwer mógł go podstawić, albo rozmówca zmienił urządzenie i
     * utworzył nową tożsamość).
     */
    fun observePeerKey(uid: String, pubB64: String): PeerKeyStatus {
        if (uid.isBlank() || pubB64.isBlank()) return PeerKeyStatus.NEW
        val fingerprint = E2eCrypto.fingerprint(Base64.decode(pubB64, Base64.NO_WRAP))
        val existing = readPeerRow(uid)
        if (existing == null) {
            writePeerRow(StoredPeerKey(uid, pubB64, fingerprint, System.currentTimeMillis()))
            return PeerKeyStatus.NEW
        }
        if (existing.pub == pubB64) return PeerKeyStatus.SAME
        // Zmiana: nadpisujemy zachowany klucz, ale zachowujemy czas pierwszego
        // kontaktu, żeby UI mogło napisać „od (daty)".
        writePeerRow(StoredPeerKey(uid, pubB64, fingerprint, existing.firstSeenAt))
        return PeerKeyStatus.CHANGED
    }

    /** Odcisk aktualnie zapisanego klucza rozmówcy — do pokazania w ostrzeżeniu. */
    fun peerFingerprint(uid: String): String? = readPeerRow(uid)?.fingerprint

    private fun readPeerRow(uid: String): StoredPeerKey? {
        val pub = prefs.getString(privPub(uid), null) ?: return null
        val fp = prefs.getString(privFp(uid), null) ?: return null
        val first = prefs.getLong(privFirst(uid), 0L)
        return StoredPeerKey(uid, pub, fp, first)
    }

    private fun writePeerRow(row: StoredPeerKey) {
        prefs.edit()
            .putString(privPub(row.uid), row.pub)
            .putString(privFp(row.uid), row.fingerprint)
            .putLong(privFirst(row.uid), row.firstSeenAt)
            .apply()
    }

    private fun privPub(uid: String) = "pub_$uid"
    private fun privFp(uid: String) = "fp_$uid"
    private fun privFirst(uid: String) = "first_$uid"

    /** Wynik obserwacji klucza rozmówcy — patrz `docs/czat-e2e.md` §6. */
    enum class PeerKeyStatus { NEW, SAME, CHANGED }

    private data class StoredPeerKey(
        val uid: String,
        val pub: String,
        val fingerprint: String,
        val firstSeenAt: Long,
    )

    companion object {
        private const val PREFS = "verbigem_e2e_peer_keys"
    }
}
