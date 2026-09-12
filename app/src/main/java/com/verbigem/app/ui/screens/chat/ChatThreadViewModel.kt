package com.verbigem.app.ui.screens.chat

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.verbigem.app.R
import com.verbigem.app.data.ConnectivityObserver
import com.verbigem.app.data.local.AppDatabase
import com.verbigem.app.data.local.ChatDeletedEntity
import com.verbigem.app.data.local.ChatOutboxEntity
import com.verbigem.app.data.local.ChatReadEntity
import com.verbigem.app.data.local.ChatTranslationEntity
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.ContactSettings
import com.verbigem.app.data.model.LangCode
import com.verbigem.app.data.crypto.E2eCrypto
import com.verbigem.app.data.crypto.E2ePeerKeyStore
import com.verbigem.app.data.crypto.MessageCipher
import com.verbigem.app.data.model.EncEnvelope
import com.verbigem.app.data.model.PublicProfile
import com.verbigem.app.data.repository.AuthRepository
import com.verbigem.app.data.repository.ChatKeyRepository
import com.verbigem.app.data.repository.ChatRepository
import com.verbigem.app.data.repository.ProTtsRepository
import com.verbigem.app.data.repository.StorageRepository
import com.verbigem.app.engine.HyMt2NativeEngine
import com.verbigem.app.engine.OcrManager
import com.verbigem.app.engine.ProTtsEngine
import com.verbigem.app.engine.SpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.util.UUID
import com.verbigem.app.util.uiString

enum class BubbleStatus { SENT, SENDING, FAILED }

/**
 * Jak ten dymek ma sie do szyfrowania E2E — patrz `docs/czat-e2e.md`.
 *
 * Trzy stany sa celowo rozne, bo znacza rozne rzeczy i tylko jeden jest awaria:
 *  * [PLAIN] — wiadomosc sprzed E2E albo od nadawcy, ktory nie ma jeszcze klucza.
 *  * [ENCRYPTED] — odszyfrowana, pokazujemy tresc.
 *  * [NO_KEY] — koperta nie ma wpisu dla TEGO urzadzenia. Normalne na nowym telefonie.
 *  * [FAILED] — wpis jest, ale sie nie odszyfrowal. **To sygnal ostrzegawczy**:
 *    podmieniony klucz albo uszkodzone dane. Nie wolno go pokazac jako pustego dymka.
 */
enum class EncState { PLAIN, ENCRYPTED, NO_KEY, FAILED }

/**
 * One message as the thread renders it.
 *
 * Remote messages and not-yet-sent outbox rows are flattened into the same shape so
 * the UI has a single list to lay out: a message you just sent shows up instantly
 * (status SENDING) and simply changes status when the flush succeeds.
 */
data class ChatBubble(
    val id: String,
    val text: String,
    val sourceLang: String,
    val isMine: Boolean,
    val createdAt: Long,
    val status: BubbleStatus,
    val hintText: String = "",
    val hintLang: String = "",
    // Faza 5 — załącznik (zdjęcie/audio). Dla zdjęcia `text` niesie OCR, by odbiorca
    // mógł go przetłumaczyć tak samo jak zwykły tekst.
    val attachmentUrl: String = "",
    val ocrText: String = "",
    val type: String = "text",
    /** Faza 3 (E2E): czy i jak ten dymek jest zaszyfrowany. */
    val encryption: EncState = EncState.PLAIN
)

/**
 * Wynik TOFU dla klucza rozmówcy — patrz `docs/czat-e2e.md` §6 i webappowe
 * `watchPeerKey`. `status` = NEW / SAME / CHANGED; `fingerprint` to odcisk
 * klucza (4×4 hex) do pokazania w ostrzeżeniu o zmianie.
 */
data class PeerKeyInfo(
    val status: E2ePeerKeyStore.PeerKeyStatus,
    val fingerprint: String,
)

class ChatThreadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatThreadViewModel"
        /** Minimum gap between two "I am typing" writes to Firestore. */
        private const val TYPING_REFRESH_MS = 4_000L

        /**
         * Dlugosc podgladu w skrzynce. MUSI być zgodna z `preview.take(80)`
         * w `ChatRepository.sendMessage` — inaczej podgląd szyfrowany i jawny
         * miałyby różne długości i skrzynka zmieniałaby wygląd po zaszyfrowaniu.
         */
        private const val PREVIEW_MAX_CHARS = 80
    }

    private val authRepository = AuthRepository()
    private val chatRepository = ChatRepository()
    private val hyMt2Engine = HyMt2NativeEngine(application)
    private val speechManager = SpeechManager(application)
    private val proTtsEngine = ProTtsEngine(application)
    private val proTtsRepository = ProTtsRepository(application)
    private val ocrManager = OcrManager(application)
    private val storageRepository = StorageRepository(application)
    private val connectivity = ConnectivityObserver(application)
    private val chatKeyRepository = ChatKeyRepository(application)

    private val db = AppDatabase.getInstance(application)
    private val translationDao = db.chatTranslationDao()
    private val outboxDao = db.chatOutboxDao()
    private val readDao = db.chatReadDao()
    private val deletedDao = db.chatDeletedDao()

    /** The engine owns one native model handle; two concurrent loads would double the RAM. */
    private val translationMutex = Mutex()

    private var chatId: String? = null
    private val requested = mutableSetOf<String>()
    private var lastReadWritten = 0L
    private var lastTypingWrite = 0L
    private var typingStopJob: Job? = null
    private var flushing = false
    /**
     * Ktoś dodał wiersz w trakcie opróżniania kolejki. Bez tego znacznika taki
     * wiersz (drugie zdjęcie wysłane, gdy pierwsze się jeszcze uploaduje) zostaje
     * na „🕓 wysyłanie" do następnego wyzwalacza — powrotu sieci albo kolejnej
     * wysyłki. Z nim: opróżnianie dopina kolejkę raz jeszcze, od razu.
     */
    private var reflush = false
    private var olderExhausted = false

    // -------------------------------------------------------------------- E2E
    /** Wlasny klucz tozsamosci z sejfu Keystore — `null`, gdy konto go nie ma. */
    private var myIdentity: KeyPair? = null
    /** Klucz publiczny rozmowcy — `null`, gdy rozmowca nie ma jeszcze tozsamosci E2E. */
    private var otherPublicKey: ByteArray? = null
    /** Czy probowalismy juz wczytac klucze w tym watku (zeby nie siegac do sieci co wiersz). */
    private var keysLoaded = false
    /**
     * Wyniki odszyfrowania, klucz = id wiadomosci. Bez tego kazde [recompute]
     * (a wola je kazda zmiana w kolejce i kazda nowa wiadomosc) liczyloby ECDH od nowa.
     */
    private val decryptedCache = mutableMapOf<String, MessageCipher.Decrypted>()

    /**
     * Auto-translation is switched off by the FIRST failure. In practice a failure
     * means the model is not downloaded, and re-attempting it for every message in a
     * long thread would just drain the battery — the per-bubble retry button stays
     * available either way.
     */
    private var autoTranslate = true

    private val _otherUid = MutableStateFlow<String?>(null)
    val otherUid: StateFlow<String?> = _otherUid.asStateFlow()

    private val _otherProfile = MutableStateFlow<PublicProfile?>(null)
    val otherProfile: StateFlow<PublicProfile?> = _otherProfile.asStateFlow()

    /**
     * Czy ten watek leci szyfrowany: mamy wlasna tozsamosc I znamy klucz rozmowcy.
     * Do plakietki w naglowku (faza 7 doda teksty pomocy).
     */
    private val _e2eActive = MutableStateFlow(false)
    val e2eActive: StateFlow<Boolean> = _e2eActive.asStateFlow()

    private val _myLang = MutableStateFlow(LangCode.PL)
    val myLang: StateFlow<LangCode> = _myLang.asStateFlow()

    /** Language of the other side — the target for our outgoing sender-hint. */
    private val _otherLang = MutableStateFlow(LangCode.EN)
    val otherLang: StateFlow<LangCode> = _otherLang.asStateFlow()

    /**
     * Per-contact settings, including the alias shown in the header and the
     * language override. See [translationLang] for how the override is applied.
     */
    private val _contactSettings = MutableStateFlow(ContactSettings.EMPTY)
    val contactSettings: StateFlow<ContactSettings> = _contactSettings.asStateFlow()

    /**
     * The language incoming messages are translated INTO: the per-contact override
     * if one is set, otherwise my profile language.
     *
     * This is deliberately NOT the language my outgoing messages are tagged with.
     * That one has to describe what I actually typed, or the receiver would
     * translate from the wrong source language.
     *
     * Started EAGERLY, not WhileSubscribed: [enqueueTranslations] and [retranslate]
     * read `.value` from inside the ViewModel. A lazily-started StateFlow that has
     * no subscriber yet silently hands back its initial value, which would make the
     * thread translate into Polish regardless of the profile or the override.
     */
    val translationLang: StateFlow<LangCode> =
        combine(_myLang, _contactSettings) { mine, settings ->
            if (settings.langOverride.isBlank()) mine else LangCode.fromCode(settings.langOverride)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, LangCode.PL)

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _translating = MutableStateFlow<Set<String>>(emptySet())
    val translating: StateFlow<Set<String>> = _translating.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    private val _showOriginal = MutableStateFlow<Set<String>>(emptySet())
    val showOriginal: StateFlow<Set<String>> = _showOriginal.asStateFlow()

    private val _readReceipts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val readReceipts: StateFlow<Map<String, Long>> = _readReceipts.asStateFlow()

    private val _bubbles = MutableStateFlow<List<ChatBubble>>(emptyList())
    val bubbles: StateFlow<List<ChatBubble>> = _bubbles.asStateFlow()

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    // ---------------------------------------------------------- Faza 6: szukanie
    /** Tekst szukania w wątku — filtruje [visibleBubbles] (podciąg, bez wielkości liter). */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Wynik TOFU dla klucza rozmówcy — `null` przed pierwszą obserwacją. */
    private val _peerKeyStatus = MutableStateFlow<PeerKeyInfo?>(null)
    val peerKeyStatus: StateFlow<PeerKeyInfo?> = _peerKeyStatus.asStateFlow()

    /**
     * Dymki do wyświetlenia: [bubbles] przefiltrowane przez [searchQuery].
     * Szukamy w treści źródłowej I w tłumaczeniu (jak webappka:
     * `translatedText || text`), oraz w podpowiedzi nadawcy. Pomijamy dymki
     * nieczytelne (NO_KEY / FAILED) — nie mają tekstu do znalezienia.
     */
    val visibleBubbles: StateFlow<List<ChatBubble>> =
        combine(_bubbles, _searchQuery, _translations) { bubbles, q, translations ->
            val term = q.trim().lowercase()
            if (term.isBlank()) return@combine bubbles
            bubbles.filter { bubble ->
                if (bubble.encryption == EncState.NO_KEY || bubble.encryption == EncState.FAILED) {
                    return@filter false
                }
                val haystack = listOfNotNull(
                    bubble.text.takeIf { it.isNotBlank() },
                    translations[bubble.id]?.takeIf { it.isNotBlank() },
                    bubble.hintText.takeIf { it.isNotBlank() },
                )
                haystack.any { it.lowercase().contains(term) }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Anuluje bieżący nasłuch klucza rozmówcy (TOFU). */
    private var peerWatchUnsub: (() -> Unit)? = null

    /** Faza 5.3: czy trwa nagrywanie/rozpoznawanie głosu (SpeechRecognizer na żywo). */
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    /** Faza 5.3: bieżący (częściowy) wynik rozpoznawania — pokazywany w UI. */
    private val _voiceInterim = MutableStateFlow("")
    val voiceInterim: StateFlow<String> = _voiceInterim.asStateFlow()

    /**
     * Komunikat do pokazania w Snackbarze; `null` = nie ma nic do pokazania.
     *
     * Do v1.0.55 brak STT na urządzeniu i błąd rozpoznawania kończyły się tylko na
     * `Log.w` — z punktu widzenia użytkownika mikrofon „po prostu nie działał".
     * Osobny stan (zamiast wyjątku z funkcji) dlatego, że oba zdarzenia przychodzą z
     * callbacków `SpeechRecognizer`, czyli spoza miejsca, które można wyłapać.
     */
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun showMessage(text: String) {
        _uiMessage.value = text
    }

    /** Tekst idzie z zasobów — żaden komunikat nie jest wpisany na sztywno (6 języków). */
    fun showMessage(resId: Int) {
        showMessage(uiString(resId))
    }

    /** Ekran woła po pokazaniu Snackbara; bez wyzerowania ten sam tekst nie wróci. */
    fun consumeMessage() {
        _uiMessage.value = null
    }

    private val _tick = MutableStateFlow(0L)
    private val _typingUntil = MutableStateFlow<Map<String, Long>>(emptyMap())

    // Raw inputs, merged by recompute() into the single [bubbles] list the UI reads.
    private val _remoteMsg = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _olderMsg = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _pendingRows = MutableStateFlow<List<ChatOutboxEntity>>(emptyList())
    private var _deletedIds: Set<String> = emptySet()

    /** True while the other person's typing flag has not expired yet. */
    val otherTyping: StateFlow<Boolean> =
        combine(_typingUntil, _otherUid, _tick) { typing, other, now ->
            other != null && (typing[other] ?: 0L) > now
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentUid: String
        get() = authRepository.currentUser?.uid ?: ""

    init {
        val uid = currentUid
        if (uid.isNotBlank()) {
            viewModelScope.launch {
                authRepository.watchProfile(uid).collect { profile ->
                    profile?.let {
                        val lang = LangCode.fromCode(it.speakLangSource)
                        if (lang != _myLang.value) {
                            _myLang.value = lang
                            // Different target language: the in-memory map is no longer
                            // valid (Room rows are keyed by language, so they survive).
                            _translations.value = emptyMap()
                            autoTranslate = true
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            deletedDao.watchAll().collect { rows ->
                _deletedIds = rows.map { it.msgId }.toSet()
                recompute()
            }
        }
        // The network coming back is the moment to drain the outbox — this is what
        // makes a message written offline actually leave the phone.
        viewModelScope.launch {
            connectivity.isOnline.collect { if (it) flushOutbox() }
        }
        // Heartbeat so an expired "typing" flag disappears without a Firestore write.
        viewModelScope.launch {
            while (true) {
                _tick.value = System.currentTimeMillis()
                delay(1_500)
            }
        }
    }

    fun setPro(isPro: Boolean) {
        _isPro.value = isPro
    }

    /** Faza 6: zmiana tekstu szukania w wątku. */
    fun onSearchChanged(text: String) {
        _searchQuery.value = text
    }

    /** Called once per thread; repeated calls with the same uid are ignored. */
    fun openThread(otherUid: String) {
        if (otherUid.isBlank() || _otherUid.value == otherUid) return
        val me = currentUid
        if (me.isBlank()) return

        _otherUid.value = otherUid
        _otherProfile.value = null
        _contactSettings.value = ContactSettings.EMPTY
        _showOriginal.value = emptySet()
        _remoteMsg.value = emptyList()
        _olderMsg.value = emptyList()
        _bubbles.value = emptyList()
        olderExhausted = false
        lastReadWritten = 0L
        myIdentity = null
        otherPublicKey = null
        keysLoaded = false
        decryptedCache.clear()
        _e2eActive.value = false
        // Faza 6: czyścimy szukanie i nanosłuch klucza z poprzedniego wątku.
        _searchQuery.value = ""
        peerWatchUnsub?.invoke()
        peerWatchUnsub = null
        _peerKeyStatus.value = null

        val id = chatRepository.getChatId(me, otherUid)
        chatId = id

        viewModelScope.launch {
            chatRepository.watchLatestMessages(id).collect { _remoteMsg.value = it; recompute() }
        }
        viewModelScope.launch {
            outboxDao.watch(id).collect { _pendingRows.value = it; recompute() }
        }
        viewModelScope.launch {
            chatRepository.watchReadReceipts(id).collect { _readReceipts.value = it }
        }
        viewModelScope.launch {
            chatRepository.watchTyping(id).collect { _typingUntil.value = it }
        }
        viewModelScope.launch {
            authRepository.getPublicProfile(otherUid)?.let { public ->
                _otherProfile.value = public
                // Outgoing hints are translated into THEIR language (decision D1).
                _otherLang.value = LangCode.fromCode(public.speakLangSource)
            }
        }
        // Klucze E2E: wlasna tozsamosc z sejfu + klucz publiczny rozmowcy. Osobno od
        // profilu publicznego, bo brak klucza NIE jest bledem — to stan "konto sprzed
        // E2E" i wtedy piszemy jawnie, tak jak przed wprowadzeniem szyfrowania.
        viewModelScope.launch {
            loadKeys(me, otherUid)
            recompute()
        }
        // TOFU (faza 6): żywa obserwacja klucza rozmówcy. Każda zmiana na serwerze
        // porównywana jest z zapisaną lokalnie — zmiana = ostrzeżenie w wątku.
        peerWatchUnsub = chatKeyRepository.watchPeerKey(otherUid) { status, fp ->
            _peerKeyStatus.value = PeerKeyInfo(status, fp)
        }
        // Alias + per-contact translation language. Changing the language invalidates
        // the in-memory map exactly like a profile language change does (the Room
        // cache is keyed by language, so old rows survive untouched).
        viewModelScope.launch {
            chatRepository.watchContactSettings(me).collect { map ->
                val settings = map[otherUid] ?: ContactSettings.EMPTY
                val previousLang = _contactSettings.value.langOverride
                _contactSettings.value = settings
                if (settings.langOverride != previousLang) {
                    _translations.value = emptyMap()
                    requested.clear()
                    autoTranslate = true
                    recompute()
                }
            }
        }
        flushOutbox()
    }

    // -------------------------------------------------------------------- E2E

    /**
     * Wczytuje klucze raz na otwarcie watku. Siec i Keystore ida na IO —
     * `publicKeyOf` czyta Firestore, a `localIdentity` odszyfrowuje klucz prywatny
     * kluczem z Keystore, co na watku glownym potrafi zamulic liste wiadomosci.
     */
    private suspend fun loadKeys(me: String, other: String) {
        keysLoaded = true
        myIdentity = withContext(Dispatchers.IO) { chatKeyRepository.localIdentity(me) }
        otherPublicKey = withContext(Dispatchers.IO) { chatKeyRepository.publicKeyOf(other) }
        _e2eActive.value = myIdentity != null && otherPublicKey != null
        // Klucz wlasnie sie pojawil — poprzednie przebiegi mogly oznaczyc dymki
        // jako nieczytelne tylko dlatego, ze tozsamosc jeszcze sie nie wczytala.
        if (myIdentity != null) decryptedCache.clear()
    }

    /**
     * Klucze wczytane na zadanie — [openThread] startuje wysylke kolejki od razu,
     * a wczytanie kluczy jest asynchroniczne. Bez tego wiadomosc wpisana offline
     * i wyslana natychmiast po wejsciu w watek polecialaby jawnie, mimo ze obie
     * strony maja klucze — czyli cicho, dokladnie w tym momencie, w ktorym
     * uzytkownik najbardziej liczy na szyfrowanie.
     */
    private suspend fun ensureKeys() {
        if (keysLoaded) return
        val me = currentUid
        val other = _otherUid.value.orEmpty()
        if (me.isBlank() || other.isBlank()) return
        loadKeys(me, other)
    }

    private fun decryptCached(msg: ChatMessage): MessageCipher.Decrypted {
        decryptedCache[msg.id]?.let { return it }
        // Brak klucza lokalnego NIE znaczy "nie da sie odszyfrowac" — tozsamosc moze
        // sie jeszcze wczytywac. Dlatego tego wyniku NIE zapamietujemy: inaczej
        // pierwszy przebieg zamurowalby wszystkie dymki jako nieczytelne na stale.
        val identity = myIdentity ?: return MessageCipher.Decrypted.NoKeyForThisDevice
        val result = MessageCipher.decrypt(msg, currentUid, identity.private)
        decryptedCache[msg.id] = result
        return result
    }

    /**
     * Pola wiadomosci gotowe do zapisu. Dla wiadomosci szyfrowanej KAZDE wrazliwe
     * pole jest albo puste, albo szyfrogramem — inaczej szyfrowanie byloby teatrem,
     * bo jawny tekst wyladowalby obok koperty w tym samym dokumencie.
     */
    private data class Outgoing(
        val text: String,
        val hintLang: String,
        val hintText: String,
        val ocrText: String,
        val transcript: String,
        val enc: EncEnvelope?,
        /** Podglad do skrzynki; `null` = niech repozytorium wybierze samodzielnie. */
        val preview: String?,
        /** Podglad zaszyfrowany (druga, mala koperta) — patrz [outgoing]. */
        val previewEnc: EncEnvelope? = null,
        val previewBody: String = "",
    )

    /**
     * Buduje [Outgoing]. Szyfruje tylko wtedy, gdy OBIE strony maja klucze;
     * brak klucza = wysylka jawna (cala migracja z `docs/czat-e2e.md` par. 5).
     *
     * Gdy szyfrowanie jest mozliwe, ale sie nie powiedzie, leci wyjatek — wiersz
     * dostaje stan "nie wyslano / ponow". Ciche zejscie do jawnosci byloby dokladnie
     * tym, przed czym to szyfrowanie ma chronic.
     *
     * Podglad w skrzynce: `lastMessage` dostaje zlokalizowany OPIS (dla starszych
     * wersji aplikacji, ktore nie umieja odszyfrowac), a obok leci druga, malutka
     * koperta z prawdziwym podgladem. Dzieki temu skrzynka nie musi czytac
     * dokumentu wiadomosci, zeby cokolwiek pokazac.
     *
     * [previewText] to tresc podgladu w jezyku oryginalu (dla zdjecia OCR, dla
     * glosowki transkrypcja) — dokladnie to, co przed E2E trafialo do `lastMessage`.
     */
    private fun outgoing(
        payload: MessageCipher.SecretPayload,
        hint: String,
        previewText: String,
    ): Outgoing {
        val myKey = myIdentity
        val theirKey = otherPublicKey
        val other = _otherUid.value.orEmpty()
        if (myKey == null || theirKey == null || other.isBlank()) {
            return Outgoing(
                text = payload.text.orEmpty(),
                hintLang = payload.hintLang.orEmpty(),
                hintText = hint,
                ocrText = payload.ocrText.orEmpty(),
                transcript = payload.transcript.orEmpty(),
                enc = null,
                preview = null,
            )
        }
        val sealed = MessageCipher.encrypt(
            payload = payload,
            // Wlasny uid MUSI byc na liscie odbiorcow: inaczej po zmianie telefonu
            // nie odczytasz tego, co sam wyslales.
            recipients = listOf(
                currentUid to E2eCrypto.encodePublicKey(myKey.public),
                other to theirKey,
            ),
            ephemeral = E2eCrypto.generateEphemeralKeyPair(),
        )
        // Osobna para efemeryczna na podglad, a nie ta z koperty wiadomosci:
        // wspolny sekret ECDH użyty dwa razy to niepotrzebne powtorzenie,
        // a koszt drugiej pary jest na tyle maly, ze nie warto go oszczedzac.
        val previewSealed = if (previewText.isBlank()) {
            null
        } else {
            MessageCipher.encrypt(
                payload = MessageCipher.SecretPayload(text = previewText.take(PREVIEW_MAX_CHARS)),
                recipients = listOf(
                    currentUid to E2eCrypto.encodePublicKey(myKey.public),
                    other to theirKey,
                ),
                ephemeral = E2eCrypto.generateEphemeralKeyPair(),
            )
        }
        return Outgoing(
            text = sealed.body,
            hintLang = "",
            hintText = "",
            ocrText = "",
            transcript = "",
            enc = sealed.envelope,
            preview = uiString(R.string.chat_enc_preview),
            previewEnc = previewSealed?.envelope,
            previewBody = previewSealed?.body.orEmpty(),
        )
    }

    // ---------------------------------------------------------------- sending

    fun onInputChanged(text: String) {
        _inputText.value = text
        val id = chatId ?: return
        if (text.isBlank()) {
            viewModelScope.launch { stopTyping() }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTypingWrite > TYPING_REFRESH_MS) {
            lastTypingWrite = now
            viewModelScope.launch { chatRepository.setTyping(id, currentUid, true) }
        }
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(ChatRepository.TYPING_TTL_MS - 1_000)
            stopTyping()
        }
    }

    /**
     * Queues the message locally and returns immediately — the network call (and the
     * Hy-MT2 hint translation, which needs the model) happens in [flushOutbox].
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        val id = chatId ?: return
        if (text.isBlank() || currentUid.isBlank()) return

        _inputText.value = ""
        val clientMsgId = UUID.randomUUID().toString()
        viewModelScope.launch {
            outboxDao.insert(
                ChatOutboxEntity(
                    clientMsgId = clientMsgId,
                    chatId = id,
                    text = text,
                    sourceLang = _myLang.value.code
                )
            )
            stopTyping()
            flushOutbox()
        }
    }

    /**
     * Faza 5.2 (+ 5.4): kolejkuje zdjęcie — nie wysyła go tu.
     *
     * Do v1.0.54 upload szedł w gołym try/catch obok kolejki: jak sieć padła, jedynym
     * śladem była linia w logcat, a zdjęcie znikało z wątku. Teraz wstawiamy wiersz
     * z `type = "image"` i `localUri`, więc dymek pojawia się od razu z miniaturą z
     * pamięci telefonu, a [flushOutbox] robi upload → OCR → wysyłkę. Nieudany upload
     * dostaje czerwony dymek „nie wysłano / ponów" dokładnie jak zwykły tekst.
     */
    fun sendImage(uri: Uri) {
        val id = chatId ?: return
        if (currentUid.isBlank()) return
        val clientMsgId = UUID.randomUUID().toString()
        viewModelScope.launch {
            outboxDao.insert(
                ChatOutboxEntity(
                    clientMsgId = clientMsgId,
                    chatId = id,
                    sourceLang = _myLang.value.code,
                    type = "image",
                    localUri = uri.toString()
                )
            )
            flushOutbox()
        }
    }

    /**
     * Faza 5.3: głosówka. `SpeechRecognizer` (przez `SpeechManager`) rozpoznaje mowę
     * NA ŻYWO — nie przyjmuje nagranego pliku `.m4a` i nie dzieli mikrofonu z
     * `MediaRecorder`em, więc „nagraj plik → potem przetransponuj" nie jest możliwe
     * na urządzeniu. Dlatego głosówka to transkrypcja na żywo: wynik (`transcript`)
     * idzie do dokumentu wiadomości i jest tłumaczony u odbiorcy jak zwykły tekst.
     * Odtwarzanie oryginalnego audio (m4a) to osobny temat (serwerowe STT) — 5.4.
     */
    fun startVoice() {
        if (_isListening.value) return
        if (!speechManager.isSttAvailable()) {
            Log.w(TAG, "STT niedostępne na tym urządzeniu")
            showMessage(R.string.voice_not_available)
            return
        }
        _voiceInterim.value = ""
        _isListening.value = true
        speechManager.startListening(
            lang = _myLang.value,
            onInterim = { _voiceInterim.value = it },
            onFinal = { text ->
                _isListening.value = false
                _voiceInterim.value = ""
                if (text.isNotBlank()) sendVoice(text)
            },
            onError = { err ->
                _isListening.value = false
                _voiceInterim.value = ""
                Log.w(TAG, "STT failed: $err")
                // Rozpoznawanie pada często (szum, brak sieci, błąd usługi Google).
                // Bez komunikatu wygląda to na „mikrofon nie działa" — a to nie to samo.
                showMessage(R.string.voice_recognition_error)
            }
        )
    }

    /** Przerywa trwające rozpoznawanie głosu. */
    fun stopVoice() {
        speechManager.stopListening()
        _isListening.value = false
        _voiceInterim.value = ""
    }

    /**
     * Faza 5.3 (+ 5.4): kolejkuje głosówkę. `transcript` to przetranskrybowany tekst
     * (w języku nadawcy); podpowiedź w języku odbiorcy liczy [flushOutbox], by
     * odbiorca widział coś zanim jego model przetłumaczy `transcript`.
     */
    private fun sendVoice(transcript: String) {
        val id = chatId ?: return
        if (currentUid.isBlank()) return
        val clientMsgId = UUID.randomUUID().toString()
        viewModelScope.launch {
            outboxDao.insert(
                ChatOutboxEntity(
                    clientMsgId = clientMsgId,
                    chatId = id,
                    sourceLang = _myLang.value.code,
                    type = "audio",
                    transcript = transcript
                )
            )
            flushOutbox()
        }
    }

    /** Retries rows that failed before (attempts > 0). */
    fun retryFailed() {
        viewModelScope.launch {
            val id = chatId ?: return@launch
            outboxDao.all().filter { it.chatId == id && it.attempts > 0 }
                .forEach { outboxDao.updateStatus(it.clientMsgId, "pending", 0) }
            autoTranslate = true
            flushOutbox()
        }
    }

    /**
     * Drains the local outbox. Uses `set()` on a client-generated document id, so
     * running it twice (reconnect while the thread is open) cannot duplicate anything.
     *
     * Every kind of row ends in the same two outcomes: deleted on success, marked
     * `failed` on error. That is what gives a photo the same red "retry" bubble as a
     * text message instead of the silence of pre-1.0.55 builds (README §5.4).
     */
    fun flushOutbox() {
        val id = chatId ?: return
        // Trwa już opróżnianie: nie dokładamy się do niego (to by mogło wysłać dwa
        // razy to samo), tylko zaznaczamy, że po nim trzeba przejść kolejkę jeszcze raz.
        if (flushing) {
            reflush = true
            return
        }
        flushing = true
        viewModelScope.launch {
            try {
                val rows = outboxDao.all().filter { it.chatId == id }
                for (row in rows) {
                    try {
                        flushRow(id, row)
                        outboxDao.delete(row.clientMsgId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Send failed for ${row.clientMsgId} (${row.type})", e)
                        outboxDao.updateStatus(row.clientMsgId, "failed", row.attempts + 1)
                    }
                }
            } finally {
                flushing = false
                // Wiersze dodane w trakcie (np. drugie zdjęcie) — dopinamy od razu,
                // zamiast czekać na powrót sieci albo następną wysyłkę.
                if (reflush) {
                    reflush = false
                    flushOutbox()
                }
            }
        }
    }

    /** Wysyła jeden wiersz kolejki; rzuca, gdy się nie uda — patrz [flushOutbox]. */
    private suspend fun flushRow(chatId: String, row: ChatOutboxEntity) {
        // Klucze moga sie jeszcze wczytywac (patrz [ensureKeys]) — bez tego kroku
        // pierwsza wysylka po wejsciu w watek poszlaby jawnie.
        ensureKeys()
        val source = LangCode.fromCode(row.sourceLang)
        val target = _otherLang.value
        when (row.type) {
            "image" -> sendImageRow(chatId, row, source, target)
            "audio" -> sendAudioRow(chatId, row, source, target)
            else -> {
                val hint = translateHint(row.text, source, target)
                val out = outgoing(
                    MessageCipher.SecretPayload(
                        text = row.text,
                        hintLang = target.code,
                        hintText = hint,
                    ),
                    hint,
                    previewText = row.text,
                )
                chatRepository.sendMessage(
                    chatId = chatId,
                    authorId = currentUid,
                    text = out.text,
                    sourceLang = row.sourceLang,
                    hintLang = out.hintLang,
                    hintText = out.hintText,
                    clientMsgId = row.clientMsgId,
                    enc = out.enc,
                    previewOverride = out.preview,
                    previewEnc = out.previewEnc,
                    previewBody = out.previewBody
                )
            }
        }
    }

    /** Upload do Storage → OCR na urządzeniu → wysyłka dokumentu `type = "image"`. */
    private suspend fun sendImageRow(
        chatId: String,
        row: ChatOutboxEntity,
        source: LangCode,
        target: LangCode
    ) {
        val uri = Uri.parse(row.localUri)
        // clientMsgId jako nazwa pliku → ponowiona wysyłka nadpisuje tę samą ścieżkę,
        // więc retry nie mnoży zdjęć w Storage.
        val url = storageRepository.uploadAttachment(chatId, row.clientMsgId, uri, "image/*")
        // OCR jest opcjonalny — nieudany OCR nie blokuje wysyłki zdjęcia.
        val ocr = try {
            ocrManager.recognizeText(uri)
        } catch (e: Exception) {
            Log.w(TAG, "OCR rozpoznawania tekstu nie powiodło się", e)
            ""
        }
        val hint = translateHint(ocr, source, target)
        val out = outgoing(
            MessageCipher.SecretPayload(
                text = "",
                hintLang = target.code,
                hintText = hint,
                ocrText = ocr,
            ),
            hint,
            previewText = ocr,
        )
        chatRepository.sendMessage(
            chatId = chatId,
            authorId = currentUid,
            text = out.text,
            sourceLang = row.sourceLang,
            hintLang = out.hintLang,
            hintText = out.hintText,
            clientMsgId = row.clientMsgId,
            type = "image",
            attachmentUrl = url,
            ocrText = out.ocrText,
            transcript = out.transcript,
            enc = out.enc,
            previewOverride = out.preview,
            previewEnc = out.previewEnc,
            previewBody = out.previewBody
        )
    }

    /** Głosówka: pliku audio nie ma (STT biegnie na żywo), więc leci sam `transcript`. */
    private suspend fun sendAudioRow(
        chatId: String,
        row: ChatOutboxEntity,
        source: LangCode,
        target: LangCode
    ) {
        val hint = translateHint(row.transcript, source, target)
        val out = outgoing(
            MessageCipher.SecretPayload(
                text = "",
                hintLang = target.code,
                hintText = hint,
                transcript = row.transcript,
            ),
            hint,
            previewText = row.transcript,
        )
        chatRepository.sendMessage(
            chatId = chatId,
            authorId = currentUid,
            text = out.text,
            sourceLang = row.sourceLang,
            hintLang = out.hintLang,
            hintText = out.hintText,
            clientMsgId = row.clientMsgId,
            type = "audio",
            ocrText = out.ocrText,
            transcript = out.transcript,
            enc = out.enc,
            previewOverride = out.preview,
            previewEnc = out.previewEnc,
            previewBody = out.previewBody
        )
    }

    /**
     * Podpowiedź w języku odbiorcy, liczona na urządzeniu nadawcy (Hy-MT2).
     * Czystsza wersja „przetłumacz albo odpuść": brak modelu nie blokuje wysyłki,
     * bo odbiorca i tak tłumaczy u siebie.
     */
    private suspend fun translateHint(text: String, source: LangCode, target: LangCode): String {
        if (text.isBlank() || source == target) return text
        return try {
            translationMutex.withLock { hyMt2Engine.translateSegmented(text, source, target) }
        } catch (e: Exception) {
            Log.w(TAG, "Sender hint translation failed", e)
            ""
        }
    }

    // -------------------------------------------------------------- pagination

    fun loadOlder() {
        val id = chatId ?: return
        if (_loadingOlder.value || !_canLoadMore.value) return
        val oldest = (_olderMsg.value + _remoteMsg.value)
            .mapNotNull { it.createdAt }
            .minByOrNull { it.seconds } ?: return

        _loadingOlder.value = true
        viewModelScope.launch {
            try {
                val page = chatRepository.loadOlderMessages(id, oldest)
                if (page.size < chatRepository.pageSize) olderExhausted = true
                _olderMsg.value = (_olderMsg.value + page).distinctBy { it.id }
                recompute()
            } finally {
                _loadingOlder.value = false
            }
        }
    }

    // ------------------------------------------------- translation on the client

    /**
     * Decision D1: the RECEIVER translates, into their own language, on their own
     * device. The sender's `senderTranslation` is only the fallback shown while the
     * model runs (or when the receiver has no model at all).
     */
    private fun enqueueTranslations() {
        if (!autoTranslate) return
        val target = translationLang.value
        _bubbles.value.forEach { bubble ->
            if (bubble.isMine || bubble.status != BubbleStatus.SENT) return@forEach
            // Nieczytelny dymek to nie tekst do tlumaczenia — placeholder w obcym
            // jezyku albo base64 tylko zasmiecilby liste i wywolal tlumaczenie smieci.
            if (bubble.encryption == EncState.NO_KEY || bubble.encryption == EncState.FAILED) {
                return@forEach
            }
            if (LangCode.fromCode(bubble.sourceLang) == target) return@forEach
            if (_translations.value.containsKey(bubble.id) || bubble.id in requested) return@forEach
            translateInto(bubble, target)
        }
    }

    private fun translateInto(bubble: ChatBubble, target: LangCode) {
        if (!requested.add(bubble.id)) return
        _translating.value = _translating.value + bubble.id
        _failed.value = _failed.value - bubble.id
        viewModelScope.launch {
            try {
                val cached = translationDao.get(bubble.id, target.code)
                if (cached != null) {
                    _translations.value = _translations.value + (bubble.id to cached.translatedText)
                } else {
                    val out = translationMutex.withLock {
                        hyMt2Engine.translateSegmented(
                            bubble.text,
                            LangCode.fromCode(bubble.sourceLang),
                            target
                        )
                    }
                    if (out.isNotBlank()) {
                        translationDao.upsert(
                            ChatTranslationEntity(
                                msgId = bubble.id,
                                targetLang = target.code,
                                chatId = chatId.orEmpty(),
                                translatedText = out
                            )
                        )
                        _translations.value = _translations.value + (bubble.id to out)
                    } else {
                        _failed.value = _failed.value + bubble.id
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Translation of ${bubble.id} failed", e)
                _failed.value = _failed.value + bubble.id
                autoTranslate = false
            } finally {
                requested.remove(bubble.id)
                _translating.value = _translating.value - bubble.id
            }
        }
    }

    /** Re-translates one message (menu / button). Clears the cached row first. */
    fun retranslate(msgId: String) {
        val bubble = _bubbles.value.firstOrNull { it.id == msgId } ?: return
        _translations.value = _translations.value - msgId
        _failed.value = _failed.value - msgId
        requested.remove(msgId)
        autoTranslate = true
        viewModelScope.launch {
            translationDao.deleteForMessage(msgId)
            translateInto(bubble, translationLang.value)
        }
    }

    // ------------------------------------------------------------- bubble actions

    fun toggleOriginal(msgId: String) {
        _showOriginal.value = if (msgId in _showOriginal.value) {
            _showOriginal.value - msgId
        } else {
            _showOriginal.value + msgId
        }
    }

    /** "Delete for me": a local tombstone. Other devices keep the message. */
    fun deleteForMe(msgId: String) {
        viewModelScope.launch {
            deletedDao.insert(ChatDeletedEntity(msgId = msgId))
            _translations.value = _translations.value - msgId
            translationDao.deleteForMessage(msgId)
            _showOriginal.value = _showOriginal.value - msgId
        }
    }

    fun quote(msgId: String) {
        val bubble = _bubbles.value.firstOrNull { it.id == msgId } ?: return
        _inputText.value = "„${bubble.text}” "
    }

    /** Reads the message in the language it is currently DISPLAYED in. */
    fun speak(text: String, langCode: String) {
        speechManager.speak(text, LangCode.fromCode(langCode))
    }

    fun speakPro(text: String, langCode: String) {
        if (!_isPro.value || text.isBlank()) return
        viewModelScope.launch {
            try {
                val config = proTtsRepository.getConfig()
                proTtsEngine.speak(text, LangCode.fromCode(langCode), config)
            } catch (e: Exception) {
                Log.w(TAG, "Pro TTS failed", e)
                // Płatna funkcja, która po cichu nic nie robi, wygląda na oszustwo.
                showMessage(R.string.read_pro_failed)
            }
        }
    }

    // ------------------------------------------------------------------ internal

    private fun recompute() {
        val me = currentUid
        val merged = LinkedHashMap<String, ChatMessage>()
        (_olderMsg.value + _remoteMsg.value).forEach { msg ->
            if (msg.id !in _deletedIds) merged[msg.id] = msg
        }
        val remoteBubbles = merged.values.map { msg ->
            // Wiadomości szyfrowane przechodzą przez kopertę; jawne (sprzed E2E albo
            // od rozmówcy bez klucza) idą starą ścieżką bez żadnej zmiany.
            val secret = if (msg.enc == null) null else decryptCached(msg)
            val payload = (secret as? MessageCipher.Decrypted.Ok)?.payload
            val encryption = when {
                msg.enc == null -> EncState.PLAIN
                payload != null -> EncState.ENCRYPTED
                secret is MessageCipher.Decrypted.Failed -> EncState.FAILED
                else -> EncState.NO_KEY
            }
            ChatBubble(
                id = msg.id,
                // Dla zdjęcia `text` niesie OCR, a dla głosówki `transcript` — żeby
                // istniejąca logika tłumaczenia (enqueueTranslations / translateInto)
                // przetłumaczyła je jak zwykły tekst u odbiorcy.
                text = when {
                    encryption == EncState.NO_KEY -> uiString(R.string.chat_enc_no_key)
                    encryption == EncState.FAILED -> uiString(R.string.chat_enc_failed)
                    payload != null -> when {
                        msg.isAudio() -> payload.transcript.orEmpty()
                        msg.isImage() -> payload.ocrText.orEmpty()
                        else -> payload.text.orEmpty()
                    }
                    msg.isAudio() -> msg.transcript
                    msg.isImage() -> msg.ocrText
                    else -> msg.text
                },
                sourceLang = msg.sourceLang,
                isMine = msg.authorId == me,
                createdAt = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                status = BubbleStatus.SENT,
                // Podpowiedź i OCR dla wiadomości szyfrowanej siedzą w kopercie, nie
                // w dokumencie — dlatego sięgamy najpierw do payloadu.
                hintText = payload?.hintText?.takeIf { it.isNotBlank() } ?: msg.hintText(),
                hintLang = payload?.hintLang?.takeIf { it.isNotBlank() } ?: msg.hintLang(),
                attachmentUrl = msg.attachmentUrl,
                ocrText = payload?.ocrText?.takeIf { it.isNotBlank() } ?: msg.ocrText,
                type = msg.type,
                encryption = encryption
            )
        }
        val pendingBubbles = _pendingRows.value.map { row ->
            ChatBubble(
                id = row.clientMsgId,
                // Ten sam wybór co przy wiadomościach z Firestore: głosówka niesie
                // transkrypcję, zdjęcie — OCR. Dzięki temu dymek w kolejce wygląda
                // i tłumaczy się identycznie jak ten już wysłany.
                text = when {
                    row.type == "audio" -> row.transcript
                    row.type == "image" -> row.ocrText
                    else -> row.text
                },
                sourceLang = row.sourceLang,
                isMine = true,
                createdAt = row.createdAt,
                status = if (row.attempts > 0) BubbleStatus.FAILED else BubbleStatus.SENDING,
                // Zdjęcie w kolejce nie ma jeszcze URL-a ze Storage — pokazujemy
                // miniaturę prosto z pamięci telefonu (Coil ładuje content://).
                attachmentUrl = row.localUri.ifBlank { row.attachmentUrl },
                ocrText = row.ocrText,
                type = row.type
            )
        }
        _bubbles.value = (remoteBubbles + pendingBubbles).sortedBy { it.createdAt }
        _canLoadMore.value = _remoteMsg.value.size >= chatRepository.pageSize && !olderExhausted
        enqueueTranslations()
        maybeMarkRead()
    }

    /**
     * Writes the read watermark twice: into Room (drives the inbox unread dot, costs
     * nothing) and into Firestore only when it actually moved forward, so the other
     * side can show the second tick.
     */
    private fun maybeMarkRead() {
        val id = chatId ?: return
        val me = currentUid
        if (me.isBlank()) return
        val newestIncoming = _bubbles.value
            .filter { !it.isMine && it.status == BubbleStatus.SENT }
            .maxOfOrNull { it.createdAt } ?: return
        if (newestIncoming <= lastReadWritten) return
        lastReadWritten = newestIncoming
        viewModelScope.launch {
            readDao.upsert(ChatReadEntity(chatId = id, lastReadAt = newestIncoming))
            if (newestIncoming > (_readReceipts.value[me] ?: 0L)) {
                chatRepository.markRead(id, me, newestIncoming)
            }
        }
    }

    private suspend fun stopTyping() {
        typingStopJob?.cancel()
        typingStopJob = null
        lastTypingWrite = 0L
        val id = chatId ?: return
        chatRepository.setTyping(id, currentUid, false)
    }

    override fun onCleared() {
        super.onCleared()
        peerWatchUnsub?.invoke()
        peerWatchUnsub = null
        viewModelScope.launch { stopTyping() }
        hyMt2Engine.release()
        speechManager.release()
        proTtsEngine.release()
    }
}
