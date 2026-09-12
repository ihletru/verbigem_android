package com.verbigem.app.data.crypto

import com.google.gson.Gson
import com.verbigem.app.data.model.ChatMessage
import com.verbigem.app.data.model.EncEnvelope
import com.verbigem.app.data.model.EncWrappedKey
import java.security.KeyPair
import java.security.PrivateKey
import java.util.Base64

/**
 * Zamienia treść wiadomości na kopertę E2E i z powrotem.
 *
 * **Dlaczego jedno pole z JSON-em, a nie osobny szyfrogram na każde pole:**
 * wiadomość niesie cztery rzeczy do ukrycia (`text`, `hintText`, `ocrText`,
 * `transcript`). Szyfrowanie każdej osobno znaczyłoby cztery IV-y, cztery
 * wywołania GCM i — co gorsza — cztery miejsca, w których można zapomnieć
 * o szyfrowaniu przy dodaniu nowego pola. Jedna koperta na całą wrażliwą treść
 * ma jedno miejsce, w którym trzeba o tym pamiętać, i jedno, w którym można
 * to sprawdzić.
 *
 * Metadane (`type`, `sourceLang`, `attachmentUrl`, `authorId`, `createdAt`)
 * zostają JAWNE i celowo: routing, sortowanie i ikona wiersza w skrzynce
 * potrzebują ich bez klucza, a nie zdradzają treści. Kto i kiedy pisał, zostaje
 * widoczne — tak samo jak w Telegramie i WhatsAppie (patrz `docs/czat-e2e.md` §3).
 *
 * ⚠️ **Załączniki w Storage NIE są zaszyfrowane** — zdjęcie i nagranie leżą
 * jawne pod `chat_attachments/{chatId}/{msgId}`. Szyfrowany jest tylko opis
 * (OCR, transkrypcja). To jest znana luka i jest zapisana w dokumencie; kto ma
 * URL, ten widzi plik.
 */
object MessageCipher {

    /**
     * Wrażliwe pola wiadomości. Serializowane do JSON i szyfrowane razem.
     *
     * ⚠️ Pola są `String?`, a nie `String`, mimo że zawsze zapisujemy wszystkie:
     * Gson omija konstruktor i przy brakującym polu w JSON-ie zostawiłby `null`
     * w polu zadeklarowanym jako nie-nullowalne. To jest różnica między
     * „kompilator pilnuje" a „kompilator udaje, że pilnuje" — dlatego typ mówi
     * prawdę, a [normalized] zamienia to na bezpieczne puste stringi.
     */
    data class SecretPayload(
        val text: String? = null,
        val hintLang: String? = null,
        val hintText: String? = null,
        val ocrText: String? = null,
        val transcript: String? = null,
    ) {
        fun normalized(): SecretPayload = SecretPayload(
            text = text.orEmpty(),
            hintLang = hintLang.orEmpty(),
            hintText = hintText.orEmpty(),
            ocrText = ocrText.orEmpty(),
            transcript = transcript.orEmpty(),
        )

        /** Czy jest cokolwiek do zaszyfrowania (np. sama treść albo sam OCR). */
        fun hasContent(): Boolean =
            !text.isNullOrEmpty() || !hintText.isNullOrEmpty() ||
                !ocrText.isNullOrEmpty() || !transcript.isNullOrEmpty()
    }

    /** Gotowa do zapisu wiadomość: szyfrogram w `text` + koperta. */
    data class Encrypted(val body: String, val envelope: EncEnvelope)

    /** Wynik odszyfrowania — trzy różne sytuacje, trzy różne komunikaty w UI. */
    sealed interface Decrypted {
        data class Ok(val payload: SecretPayload) : Decrypted

        /**
         * Koperta nie ma wpisu dla TEGO urządzenia. Normalne, gdy wiadomość
         * powstała, zanim to urządzenie dołączyło do konta. Nie jest błędem
         * i nie wolno tego pokazywać jako awarii.
         */
        data object NoKeyForThisDevice : Decrypted

        /**
         * Wpis jest, ale się nie odszyfrował: ktoś podmienił klucz, dane są
         * uszkodzone, albo format się rozjechał. O tym trzeba powiedzieć wprost —
         * ciche pokazanie pustego dymka ukryłoby najgroźniejszy scenariusz.
         */
        data object Failed : Decrypted
    }

    private val gson = Gson()

    /**
     * Szyfruje [payload] dla [recipients] (`keyId -> klucz publiczny 65 B`).
     *
     * ⚠️ Do listy odbiorców MUSZĄ wejść także własne pozostałe urządzenia.
     * Inaczej po zmianie telefonu nie odczytasz tego, co sam wysłałeś —
     * a to najczęstsze zgłoszenie użytkowników w każdym komunikatorze z E2E.
     * Przy modelu „jeden klucz na konto" wystarczy jedno wpisanie własnego `uid`,
     * ale interfejs celowo przyjmuje listę, żeby przejście na klucze per
     * urządzenie nie wymagało zmiany formatu koperty.
     */
    fun encrypt(
        payload: SecretPayload,
        recipients: List<Pair<String, ByteArray>>,
        ephemeral: KeyPair,
    ): Encrypted {
        val plaintext = gson.toJson(payload.normalized()).toByteArray(Charsets.UTF_8)
        val sealed = E2eCrypto.seal(
            plaintext = plaintext,
            recipients = recipients,
            ephemeral = ephemeral,
            bodyIv = E2eCrypto.randomBytes(E2eCrypto.IV_BYTES),
            wrapIvs = recipients.map { E2eCrypto.randomBytes(E2eCrypto.IV_BYTES) },
        )
        return Encrypted(
            body = encode(sealed.body),
            envelope = sealed.envelope.toDto(),
        )
    }

    /**
     * Odszyfrowuje wiadomość kluczem tożsamości tego konta.
     *
     * [keyId] to identyfikator odbiorcy użyty przy szyfrowaniu (obecnie `uid`).
     */
    fun decrypt(message: ChatMessage, keyId: String, identity: PrivateKey): Decrypted {
        val envelope = message.enc ?: return Decrypted.Failed
        val body = try {
            decode(message.text)
        } catch (e: Exception) {
            return Decrypted.Failed
        }
        return open(envelope, body, keyId, identity, message.id)
    }

    /**
     * Odszyfrowuje podgląd skrzynki — kopertę, która nie należy do żadnej wiadomości,
     * tylko do dokumentu `chats/{chatId}` (pola `lastMessageEnc` + `lastMessageBody`).
     *
     * **Dlaczego druga koperta, a nie odczyt wiadomości:** skrzynka nie ma prawa
     * czytać dokumentów wiadomości tylko po to, by pokazać podgląd — to jedno
     * dodatkowe czytanie na rozmowę przy każdym odświeżeniu listy. Nadawca
     * dokłada więc malutką kopertę z samym podglądem do dokumentu rozmowy.
     */
    fun decryptPreview(
        envelope: EncEnvelope,
        body: String,
        keyId: String,
        identity: PrivateKey,
    ): Decrypted {
        val bytes = try {
            decode(body)
        } catch (e: Exception) {
            return Decrypted.Failed
        }
        return open(envelope, bytes, keyId, identity, "podglad")
    }

    /** Wspólna ścieżka: koperta + szyfrogram -> payload. */
    private fun open(
        envelope: EncEnvelope,
        body: ByteArray,
        keyId: String,
        identity: PrivateKey,
        label: String,
    ): Decrypted {
        val plaintext = try {
            E2eCrypto.open(envelope.toCrypto(), keyId, identity, body)
        } catch (e: Exception) {
            // GCM nie odszyfrował — podmieniony klucz albo uszkodzone dane.
            android.util.Log.w(TAG, "Nie moge odszyfrowac koperty ($label)", e)
            return Decrypted.Failed
        } ?: return Decrypted.NoKeyForThisDevice

        return try {
            Decrypted.Ok(
                gson.fromJson(String(plaintext, Charsets.UTF_8), SecretPayload::class.java).normalized()
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Odszyfrowana tresc nie jest poprawnym payloadem ($label)", e)
            Decrypted.Failed
        }
    }

    // ------------------------------------------------- konwersja DTO <-> JCA

    private fun EncEnvelope.toCrypto(): E2eCrypto.Envelope = E2eCrypto.Envelope(
        version = v,
        alg = alg,
        epk = decode(epk),
        bodyIv = decode(bodyIv),
        keys = keys.map { (id, key) ->
            E2eCrypto.WrappedKey(id, decode(key.iv), decode(key.wrap))
        },
    )

    private fun E2eCrypto.Envelope.toDto(): EncEnvelope = EncEnvelope(
        v = version,
        alg = alg,
        epk = encode(epk),
        bodyIv = encode(bodyIv),
        keys = keys.associate { wrapped ->
            wrapped.keyId to EncWrappedKey(encode(wrapped.iv), encode(wrapped.wrap))
        },
    )

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    private const val TAG = "MessageCipher"
}
