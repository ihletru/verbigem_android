package com.verbigem.app.data.crypto

import com.verbigem.app.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.PrivateKey

/**
 * Druga koperta E2E — podgląd skrzynki (`chats/{chatId}.lastMessageEnc` +
 * `lastMessageBody`).
 *
 * **Po co osobny test:** podgląd jedzie inną drogą niż wiadomość (dokument
 * rozmowy, nie dokument wiadomości) i ma własną parę efemeryczną. Gdyby
 * `MessageCipher.decryptPreview` rozjechał się z `encrypt`, objaw byłby cichy:
 * skrzynka pokazywałaby sam opis („Wiadomość") i nikt nie wiedziałby dlaczego —
 * dokładnie ten rodzaj błędu, którego nie widać ani w kompilacji, ani w logach.
 *
 * Uruchomienie:
 *   java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
 *     :app:testStandaloneDebugUnitTest --tests "*MessageCipherPreviewTest*"
 */
class MessageCipherPreviewTest {

    private val me = E2eCrypto.generateKeyPair()
    private val other = E2eCrypto.generateKeyPair()

    private fun recipients() = listOf(
        "uid-me" to E2eCrypto.encodePublicKey(me.public),
        "uid-other" to E2eCrypto.encodePublicKey(other.public),
    )

    private fun sealPreview(text: String): MessageCipher.Encrypted =
        MessageCipher.encrypt(
            payload = MessageCipher.SecretPayload(text = text),
            recipients = recipients(),
            ephemeral = E2eCrypto.generateEphemeralKeyPair(),
        )

    private fun previewText(
        sealed: MessageCipher.Encrypted,
        keyId: String,
        key: PrivateKey,
    ): String? {
        val result = MessageCipher.decryptPreview(sealed.envelope, sealed.body, keyId, key)
        return (result as? MessageCipher.Decrypted.Ok)?.payload?.text
    }

    @Test
    fun `podglad odszyfrowuje sie kluczem nadawcy i odbiorcy`() {
        val sealed = sealPreview("kot ma Alego")
        assertEquals(
            "Nadawca MUSI odczytac wlasny podglad — inaczej jego skrzynka pokazuje opis",
            "kot ma Alego",
            previewText(sealed, "uid-me", me.private),
        )
        assertEquals(
            "Odbiorca MUSI odczytac podglad wiadomosci do niego zaadresowanej",
            "kot ma Alego",
            previewText(sealed, "uid-other", other.private),
        )
    }

    @Test
    fun `obcy klucz nie ma wpisu w kopercie podgladu`() {
        val sealed = sealPreview("tajne")
        val stranger = E2eCrypto.generateKeyPair()
        val result = MessageCipher.decryptPreview(
            sealed.envelope,
            sealed.body,
            "uid-stranger",
            stranger.private,
        )
        assertTrue(
            "Brak wpisu dla tego urzadzenia to NIE awaria — to normalny stan nowego telefonu",
            result is MessageCipher.Decrypted.NoKeyForThisDevice,
        )
    }

    @Test
    fun `podmieniony szyfrogram podgladu jest zglaszany jako blad`() {
        val sealed = sealPreview("tajne")
        val tampered = sealed.body.dropLast(4) + "AAAA"
        val result = MessageCipher.decryptPreview(sealed.envelope, tampered, "uid-me", me.private)
        assertTrue(
            "GCM musi odrzucic podmieniony szyfrogram, a nie zwrocic pusty tekst",
            result is MessageCipher.Decrypted.Failed,
        )
    }

    @Test
    fun `podglad nie pozycza koperty od wiadomosci`() {
        val preview = sealPreview("kot ma Alego")
        val message = MessageCipher.encrypt(
            payload = MessageCipher.SecretPayload(text = "kot ma Alego"),
            recipients = recipients(),
            ephemeral = E2eCrypto.generateEphemeralKeyPair(),
        )
        assertNotEquals(
            "Szyfrogramy musza sie roznic — wspolny klucz lub IV dla dwoch kopert to blad",
            message.body,
            preview.body,
        )
    }

    @Test
    fun `wiadomosc nadal odszyfrowuje sie po refaktorze decrypt`() {
        val sealed = MessageCipher.encrypt(
            payload = MessageCipher.SecretPayload(text = "kot", hintLang = "en", hintText = "cat"),
            recipients = recipients(),
            ephemeral = E2eCrypto.generateEphemeralKeyPair(),
        )
        val msg = ChatMessage(
            id = "m1",
            authorId = "uid-me",
            text = sealed.body,
            enc = sealed.envelope,
        )
        val payload = (MessageCipher.decrypt(msg, "uid-other", other.private) as? MessageCipher.Decrypted.Ok)?.payload
        assertEquals("Tresc wiadomosci musi przetrwac obieg przez koperty", "kot", payload?.text)
        assertEquals("Podpowiedz jezykowa musi przetrwac obieg", "cat", payload?.hintText)
    }
}
