package com.verbigem.app.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.R
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatKeyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ekran tożsamości E2E konta — patrz `docs/czat-e2e.md` par. 5 i 6.
 *
 * Tu użytkownik zakłada klucz i ustawia **hasło odzyskiwania**, albo odtwarza klucz
 * na nowym urządzeniu. Trzy stany konta są różne i tylko jeden z nich jest problemem:
 *
 *  * [State.NOT_CONFIGURED] — konto nie ma tożsamości. Czat działa jawnie, tak jak
 *    przed wprowadzeniem szyfrowania. To NIE błąd, to stan sprzed zmiany.
 *  * [State.READY] — klucz jest na tym urządzeniu, wiadomości są szyfrowane.
 *  * [State.NEEDS_PASSPHRASE] — konto ma klucz i kopię na serwerze, ale to urządzenie
 *    go nie ma (nowy telefon). Bez hasła historia pozostaje nieczytelna.
 *
 * ⚠️ Hasła nigdzie nie zapisujemy — ani tu, ani w `ChatKeyRepository`. Znika zaraz po
 * użyciu, a tablica znaków jest zerowana. Zapomniane hasło = brak dostępu do historii
 * na nowym urządzeniu i nie ma czym tego obejść, bo kopia na serwerze jest zaszyfrowana
 * właśnie nim. To świadoma cena modelu „klucz konta na serwerze".
 */
class E2eKeysViewModel(application: Application) : AndroidViewModel(application) {

    /** Co pokazać na ekranie. */
    enum class State { LOADING, NOT_CONFIGURED, READY, NEEDS_PASSPHRASE }

    companion object {
        /**
         * Minimalna długość hasła. 8 znaków to nie jest „silne hasło", ale to jest
         * minimum, poniżej którego PBKDF2 z 310 000 iteracji przestaje cokolwiek
         * znaczyć — a użytkownik i tak wybierze to, co zapamięta.
         */
        const val MIN_PASSPHRASE = 8
    }

    private val authRepository = AuthRepository()
    private val chatKeyRepository = ChatKeyRepository(application)

    private val _state = MutableStateFlow(State.LOADING)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Komunikat do pokazania pod formularzem; `null` = nic nie ma. */
    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** Czy pokazać okno potwierdzenia usunięcia klucza z tego urządzenia. */
    private val _confirmForget = MutableStateFlow(false)
    val confirmForget: StateFlow<Boolean> = _confirmForget.asStateFlow()

    private val uid: String get() = authRepository.currentUser?.uid ?: ""

    init {
        refresh()
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun askForget() {
        _confirmForget.value = true
    }

    fun dismissForget() {
        _confirmForget.value = false
    }

    fun refresh() {
        val me = uid
        if (me.isBlank()) {
            _state.value = State.NOT_CONFIGURED
            return
        }
        viewModelScope.launch {
            _state.value = State.LOADING
            // Keystore + Firestore — nigdy na wątku głównym.
            val known = withContext(Dispatchers.IO) { chatKeyRepository.state(me) }
            _state.value = when (known) {
                is ChatKeyRepository.IdentityState.Ready -> State.READY
                ChatKeyRepository.IdentityState.NeedsPassphrase -> State.NEEDS_PASSPHRASE
                ChatKeyRepository.IdentityState.NotConfigured -> State.NOT_CONFIGURED
            }
        }
    }

    /** Zakłada tożsamość. Hasło trafia tylko do kopii zapasowej na serwerze. */
    fun create(passphrase: CharArray) {
        val me = uid
        if (me.isBlank() || !checkLength(passphrase)) return
        run(passphrase, R.string.e2e_keys_created) { chatKeyRepository.createIdentity(me, it) }
    }

    /**
     * Odtwarza klucz z kopii na serwerze. **Nieudane hasło NIE tworzy nowej
     * tożsamości** — użytkownik straciłby historię, myśląc, że ją odzyskał.
     */
    fun restore(passphrase: CharArray) {
        val me = uid
        if (me.isBlank() || !checkLength(passphrase)) return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) { chatKeyRepository.restoreIdentity(me, passphrase) }
            } catch (e: Exception) {
                ChatKeyRepository.RestoreResult.Error(e.message ?: "")
            } finally {
                passphrase.fill('\u0000')
            }
            _message.value = when (result) {
                is ChatKeyRepository.RestoreResult.Ok -> R.string.e2e_keys_restored
                ChatKeyRepository.RestoreResult.WrongPassphrase -> R.string.e2e_keys_wrong_passphrase
                ChatKeyRepository.RestoreResult.NoBackup -> R.string.e2e_keys_no_backup
                is ChatKeyRepository.RestoreResult.Error -> R.string.e2e_keys_error
            }
            _busy.value = false
            refresh()
        }
    }

    /**
     * Zmienia hasło odzyskiwania dla istniejącego klucza. Stara kopia jest
     * nadpisywana, więc poprzednie hasło przestaje działać — i to jest zamierzone.
     */
    fun changePassphrase(passphrase: CharArray) {
        val me = uid
        if (me.isBlank() || !checkLength(passphrase)) return
        run(passphrase, R.string.e2e_keys_changed) { chatKeyRepository.setPassphrase(me, it) }
    }

    /**
     * Usuwa klucz z TEGO urządzenia. Kopii na serwerze nie rusza — po ponownym
     * zalogowaniu i podaniu hasła historia wraca.
     */
    fun forgetLocal() {
        val me = uid
        _confirmForget.value = false
        if (me.isBlank()) return
        chatKeyRepository.forgetLocal(me)
        _message.value = R.string.e2e_keys_forgotten
        refresh()
    }

    // ------------------------------------------------------------------ wnętrze

    private fun checkLength(passphrase: CharArray): Boolean {
        if (passphrase.size < MIN_PASSPHRASE) {
            _message.value = R.string.e2e_keys_too_short
            passphrase.fill('\u0000')
            return false
        }
        return true
    }

    /**
     * Wspólna ścieżka operacji, które publikują coś do Firestore.
     *
     * Komunikat sukcesu leci DOPIERO po zakończeniu operacji — wcześniej `create`
     * pokazywał „gotowe" natychmiast, jeszcze przed publikacją klucza, więc brak
     * sieci kończył się komunikatem sukcesu i brakiem tożsamości na serwerze.
     */
    private fun run(passphrase: CharArray, successMessage: Int, block: suspend (CharArray) -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            var ok = false
            try {
                withContext(Dispatchers.IO) { block(passphrase) }
                ok = true
            } catch (e: Exception) {
                // Publikacja klucza idzie do Firestore, więc brak sieci jest tu
                // najczęstszą przyczyną — i musi być widoczny, nie przemilczany.
                _message.value = R.string.e2e_keys_error
            } finally {
                passphrase.fill('\u0000')
                _busy.value = false
            }
            if (ok) {
                _message.value = successMessage
                refresh()
            }
        }
    }
}
