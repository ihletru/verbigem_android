package com.verbigem.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Wynik próby zajęcia nicku.
 *
 * [TAKEN] to NIE błąd techniczny — to normalna odpowiedź serwera i UI musi ją
 * pokazać jako podpowiedź („wybierz inny"), a nie jako awarię sieci.
 */
enum class NicknameClaim { CLAIMED, TAKEN, ERROR }

/**
 * Rezerwacja nicku — jeden dokument na nick w kolekcji `nicknames`
 * (patrz `firestore.rules`, blok `match /nicknames/{key}`).
 *
 * Po co osobna kolekcja, skoro nick i tak leży w `usersPublic`:
 * samo zapytanie „czy nick jest wolny" przed zapisem NIE jest atomowe — dwie
 * osoby mogłyby przejść sprawdzenie w tej samej milisekundzie i obie zapisać
 * ten sam nick. Firestore nie ma unikalnych indeksów, ale ma transakcje na
 * dokumencie, więc dokument o ID wyprowadzonym z nicku JEST tym indeksem:
 * `create` w regułach znaczy dokładnie „dokumentu wcześniej nie było".
 *
 * ⚠️ ID dokumentu to SHA-256 znormalizowanego nicku, nie sam nick. Powody:
 *  * nick może zawierać `/`, którego Firestore w ID nie przyjmuje,
 *  * ID nie może zaczynać się od `__` ani przekraczać 1500 bajtów,
 *  * kolekcja jest czytelna dla zalogowanych, a hasz nie zdradza niczyjego nicku
 *    osobie, która zna tylko ID.
 *
 * ⚠️ Normalizacja (`trim` + `lowercase`) MUSI być identyczna z `PublicProfile.searchNick`.
 * Gdyby się rozjechały, dwa konta byłyby nierozróżnialne w wyszukiwaniu, a mimo to
 * miałyby różne rezerwacje — czyli dokładnie ten bałagan, który ta klasa usuwa.
 */
class NicknameRepository {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Znormalizowana postać nicku — klucz unikalności i to samo, co `searchNick`. */
    fun normalize(nickname: String): String = nickname.trim().lowercase()

    /** ID dokumentu rezerwacji: SHA-256 (hex) znormalizowanego nicku. */
    fun keyFor(nickname: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalize(nickname).toByteArray(Charsets.UTF_8))
        val out = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            out.append(HEX[value ushr 4]).append(HEX[value and 0x0F])
        }
        return out.toString()
    }

    private fun refFor(nickname: String) =
        firestore.collection(NICKNAMES).document(keyFor(nickname))

    /**
     * Zajmuje [nickname] dla [uid]. Wolny albo już mój → [NicknameClaim.CLAIMED],
     * należy do kogoś innego → [NicknameClaim.TAKEN].
     *
     * `txn.set` (a nie `update`) jest celowo: gdy dokumentu nie ma, `update` kończy
     * się błędem, a my chcemy w tym samym wywołaniu obsłużyć oba przypadki.
     *
     * Gdy rezerwacja już jest moja i zapisany nick różni się tylko wielkością liter,
     * dopisujemy go — ale gdy jest identyczny, transakcja kończy się BEZ zapisu.
     * Ma to znaczenie, bo ta metoda leci przy każdym logowaniu (doszczelnianie kont
     * sprzed unikalności) i nie chcemy płacić za zapis dokumentu za każdym razem.
     */
    suspend fun claim(nickname: String, uid: String): NicknameClaim {
        val norm = normalize(nickname)
        if (norm.isBlank() || uid.isBlank()) return NicknameClaim.ERROR
        val trimmed = nickname.trim()
        val ref = refFor(nickname)
        return try {
            val claimed = firestore.runTransaction { txn: Transaction ->
                val snap = txn.get(ref)
                val owner = snap.getString("uid")
                when {
                    !snap.exists() -> {
                        txn.set(ref, reservation(uid, trimmed, norm))
                        true
                    }
                    owner == uid -> {
                        // Już moje. Zapis tylko wtedy, gdy zmienił się sam zapis
                        // (np. wielkość liter) — inaczej byłby to zapis na darmo.
                        if (snap.getString("nickname") != trimmed) {
                            txn.set(ref, reservation(uid, trimmed, norm))
                        }
                        true
                    }
                    else -> false
                }
            }.await()
            if (claimed) NicknameClaim.CLAIMED else NicknameClaim.TAKEN
        } catch (e: Exception) {
            // Offline, brak sieci, wygasły token — nie udajemy sukcesu, ale też nie
            // mówimy użytkownikowi „nick zajęty", bo to nieprawda.
            android.util.Log.w(TAG, "Could not claim nickname", e)
            NicknameClaim.ERROR
        }
    }

    /** Treść dokumentu rezerwacji — jedno miejsce, żeby `claim` nie rozjechał się z regułami. */
    private fun reservation(uid: String, nickname: String, norm: String): Map<String, Any> = mapOf(
        "uid" to uid,
        "nickname" to nickname,
        "norm" to norm,
        "updatedAt" to System.currentTimeMillis()
    )

    /**
     * Zwalnia rezerwację [nickname] — ale tylko jeśli naprawdę należy do [uid].
     *
     * Warunek `uid` jest w transakcji, a nie po stronie wywołującego, żeby wyścig
     * „zwalniam stary nick, a w międzyczasie ktoś go zajął" nie mógł skasować
     * cudzej rezerwacji. Gdy nick należy już do kogoś innego, nic nie robimy.
     */
    suspend fun release(nickname: String, uid: String) {
        if (normalize(nickname).isBlank() || uid.isBlank()) return
        val ref = refFor(nickname)
        try {
            firestore.runTransaction { txn: Transaction ->
                val snap = txn.get(ref)
                if (snap.exists() && snap.getString("uid") == uid) txn.delete(ref)
            }.await()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not release nickname", e)
        }
    }

    /**
     * Zajmuje nick, jeśli wolny, i NIE przeszkadza, gdy zajęty.
     *
     * Używane przy logowaniu: konta założone przed wprowadzeniem rezerwacji nie mają
     * jej w ogóle, więc pierwsze logowanie je doszczelnia. Gdy nick jest już zajęty
     * przez kogoś innego (kolizja z czasów sprzed unikalności), świadomie NIE
     * przemianowujemy użytkownika za jego plecami — zamiast tego zostaje ślad
     * w logach, a konflikt rozwiązuje skrypt migracyjny.
     */
    suspend fun claimQuietly(nickname: String, uid: String) {
        when (claim(nickname, uid)) {
            NicknameClaim.CLAIMED -> Unit
            NicknameClaim.TAKEN ->
                android.util.Log.w(TAG, "Nickname already reserved by someone else: $nickname")
            NicknameClaim.ERROR ->
                android.util.Log.w(TAG, "Nickname reservation skipped (offline?)")
        }
    }

    companion object {
        private const val TAG = "NicknameRepository"
        private const val NICKNAMES = "nicknames"
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
