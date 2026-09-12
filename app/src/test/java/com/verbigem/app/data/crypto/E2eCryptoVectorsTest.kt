package com.verbigem.app.data.crypto

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Sprawdza, że format koperty E2E liczony w Kotlinie jest **bajt w bajt** taki sam,
 * jak referencja w `mini/scripts/e2e-vectors.mjs` (a przez to jak webapp).
 *
 * Po co taki test: rozjazd formatu NIE objawia się błędem kompilacji ani wyjątkiem
 * w logach — objawia się tym, że odbiorca widzi „nie można odszyfrować" i nikt nie
 * wie, które pole się nie zgadza. W tym projekcie dokładnie tak było już raz przy
 * `searchText` (`MessageSearch.normalize` ↔ `normalizeForSearch`) i wtedy tego
 * nikt nie złapał, bo nie było czym.
 *
 * Uruchomienie:
 *   java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
 *     :app:testStandaloneDebugUnitTest --tests "*E2eCryptoVectorsTest*"
 *
 * ⚠️ `e2e_vectors.json` jest KOPIĄ pliku z repo `mini`. Po regeneracji referencji
 * trzeba go skopiować ponownie, inaczej test sprawdza stary format i przechodzi
 * mimo rozjazdu.
 */
class E2eCryptoVectorsTest {

    private val vectors: JsonObject by lazy { loadVectors() }

    private val keys: JsonObject get() = vectors.getAsJsonObject("klucze")
    private val hkdf: JsonObject get() = vectors.getAsJsonObject("hkdf")
    private val message: JsonObject get() = vectors.getAsJsonObject("wiadomosc")

    private fun b64(json: JsonObject, field: String): ByteArray =
        Base64.getDecoder().decode(json.get(field).asString)

    private fun hex(json: JsonObject, field: String): String = json.get(field).asString

    // ------------------------------------------------------------ klucze

    @Test
    fun `klucz publiczny w wektorach odtwarza sie z postaci nieskompresowanej`() {
        listOf("a_pub_b64", "b_pub_b64", "eph_pub_b64").forEach { field ->
            val raw = b64(keys, field)
            assertEquals("Klucz $field musi miec 65 bajtow", 65, raw.size)
            assertEquals("Klucz $field musi miec prefiks 0x04", 0x04.toByte(), raw[0])
            assertArrayEquals(
                "Zdekodowanie i zakodowanie klucza $field musi dac te same bajty",
                raw,
                E2eCrypto.encodePublicKey(E2eCrypto.decodePublicKey(raw)),
            )
        }
    }

    @Test
    fun `para z surowego skalara ma klucz publiczny z wektorow`() {
        val pair = E2eCrypto.keyPairFromScalar(hex(keys, "eph_priv_hex"), b64(keys, "eph_pub_b64"))
        assertArrayEquals(
            "Punkt publiczny wyprowadzony ze skalara musi zgadzac sie z wektorem",
            b64(keys, "eph_pub_b64"),
            E2eCrypto.encodePublicKey(pair.public),
        )
    }

    // ------------------------------------------------------------- HKDF

    @Test
    fun `wspolny sekret ECDH zgadza sie z wektorem`() {
        val eph = E2eCrypto.keyPairFromScalar(hex(keys, "eph_priv_hex"), b64(keys, "eph_pub_b64"))
        val bPub = E2eCrypto.decodePublicKey(b64(keys, "b_pub_b64"))
        val shared = E2eCrypto.sharedSecret(eph.private, bPub)
        assertEquals(
            "Wspolny sekret ECDH musi byc identyczny jak w referencji",
            hex(hkdf, "shared_hex"),
            shared.toHex(),
        )
    }

    @Test
    fun `HKDF-SHA256 daje dokladnie ten sam klucz zawijajacy`() {
        val eph = E2eCrypto.keyPairFromScalar(hex(keys, "eph_priv_hex"), b64(keys, "eph_pub_b64"))
        val bPub = E2eCrypto.decodePublicKey(b64(keys, "b_pub_b64"))
        val shared = E2eCrypto.sharedSecret(eph.private, bPub)
        val okm = E2eCrypto.hkdfSha256(shared, b64(message, "wrap_iv_b64"), E2eCrypto.HKDF_INFO, E2eCrypto.KEY_BYTES)
        assertArrayEquals(
            "HKDF (extract + expand) musi dac klucz z wektora",
            b64(hkdf, "okm_b64"),
            okm,
        )
    }

    // ---------------------------------------------------------- koperta

    @Test
    fun `koperta i szyfrogram sa identyczne z wektorem`() {
        val eph = E2eCrypto.keyPairFromScalar(hex(keys, "eph_priv_hex"), b64(keys, "eph_pub_b64"))
        val bodyIv = b64(message, "body_iv_b64")
        val wrapIv = b64(message, "wrap_iv_b64")

        val sealed = E2eCrypto.seal(
            plaintext = message.get("plaintext_utf8").asString.toByteArray(Charsets.UTF_8),
            recipients = listOf("b" to b64(keys, "b_pub_b64")),
            ephemeral = eph,
            bodyIv = bodyIv,
            wrapIvs = listOf(wrapIv),
            messageKey = b64(message, "mk_b64"),
        )

        assertArrayEquals("epk w kopercie", b64(keys, "eph_pub_b64"), sealed.envelope.epk)
        assertArrayEquals("IV treści", bodyIv, sealed.envelope.bodyIv)
        assertEquals("Wersja formatu", 1, sealed.envelope.version)
        assertEquals("Etykieta algorytmu", E2eCrypto.ALG, sealed.envelope.alg)

        assertEquals("Liczba zawiniętych kluczy", 1, sealed.envelope.keys.size)
        assertArrayEquals("Zawiniety klucz wiadomosci", b64(message, "wrap_b64"), sealed.envelope.keys[0].wrap)
        assertArrayEquals("IV zawiniecia", wrapIv, sealed.envelope.keys[0].iv)
        assertArrayEquals("Szyfrogram tresci", b64(message, "body_b64"), sealed.body)
    }

    @Test
    fun `odbiorca odszyfrowuje wiadomosc swoim kluczem`() {
        val sealed = sealFromVectors()
        val bPriv = E2eCrypto.keyPairFromScalar(hex(keys, "b_priv_hex"), b64(keys, "b_pub_b64")).private
        val plaintext = E2eCrypto.open(sealed.envelope, "b", bPriv, sealed.body)
        assertNotNull("Odbiorca B musi odszyfrowac wiadomosc", plaintext)
        assertEquals(
            message.get("plaintext_utf8").asString,
            String(plaintext!!, Charsets.UTF_8),
        )
    }

    @Test
    fun `nieznane urzadzenie dostaje null a nie wyjatek`() {
        val sealed = sealFromVectors()
        val bPriv = E2eCrypto.keyPairFromScalar(hex(keys, "b_priv_hex"), b64(keys, "b_pub_b64")).private
        assertNull(
            "Brak wpisu dla urzadzenia to normalna sytuacja (dolaczylo pozniej), nie blad",
            E2eCrypto.open(sealed.envelope, "nie-ma-takiego", bPriv, sealed.body),
        )
    }

    // -------------------------------------------------------- ścieżka żywa

    @Test
    fun `swiezo wygenerowane klucze dzialaja dla dwoch odbiorcow`() {
        val alice = E2eCrypto.generateKeyPair()
        val bob = E2eCrypto.generateKeyPair()
        val aliceTablet = E2eCrypto.generateKeyPair()
        val eph = E2eCrypto.generateEphemeralKeyPair()

        val plaintext = "Zażółć gęślą jaźń — kot ma Alego 🐈".toByteArray(Charsets.UTF_8)
        val recipients = listOf(
            "bob" to E2eCrypto.encodePublicKey(bob.public),
            "alice-tablet" to E2eCrypto.encodePublicKey(aliceTablet.public),
        )

        val sealed = E2eCrypto.seal(
            plaintext = plaintext,
            recipients = recipients,
            ephemeral = eph,
            bodyIv = E2eCrypto.randomBytes(E2eCrypto.IV_BYTES),
            wrapIvs = recipients.map { E2eCrypto.randomBytes(E2eCrypto.IV_BYTES) },
        )

        assertArrayEquals(
            "Bob odszyfrowuje",
            plaintext,
            E2eCrypto.open(sealed.envelope, "bob", bob.private, sealed.body)!!,
        )
        assertArrayEquals(
            "Wlasne drugie urzadzenie tez odszyfrowuje (inaczej nie czytasz tego, co sam wyslales)",
            plaintext,
            E2eCrypto.open(sealed.envelope, "alice-tablet", aliceTablet.private, sealed.body)!!,
        )
        assertNull(
            "Klucz niebędący odbiorcą nie ma wpisu",
            E2eCrypto.open(sealed.envelope, "ktoś-inny", E2eCrypto.generateKeyPair().private, sealed.body),
        )
    }

    @Test
    fun `podmieniony szyfrogram jest wykrywany`() {
        val bob = E2eCrypto.generateKeyPair()
        val sealed = E2eCrypto.seal(
            plaintext = "tajne".toByteArray(Charsets.UTF_8),
            recipients = listOf("bob" to E2eCrypto.encodePublicKey(bob.public)),
            ephemeral = E2eCrypto.generateEphemeralKeyPair(),
            bodyIv = E2eCrypto.randomBytes(E2eCrypto.IV_BYTES),
            wrapIvs = listOf(E2eCrypto.randomBytes(E2eCrypto.IV_BYTES)),
        )
        val tampered = sealed.body.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        var threw = false
        try {
            E2eCrypto.open(sealed.envelope, "bob", bob.private, tampered)
        } catch (e: Exception) {
            // GCM odrzuca zmieniony szyfrogram (AEAD) — dokładnie o to chodzi.
            threw = true
        }
        assertTrue("Zmiana szyfrogramu musi zostac wykryta przez GCM", threw)
    }

    // ---------------------------------------------- kopia klucza na serwer

    @Test
    fun `kopia klucza odszyfrowuje sie haslem i tylko nim`() {
        val identity = E2eCrypto.generateKeyPair()
        val passphrase = "kotek pije mleko rano".toCharArray()

        val blob = E2eCrypto.encryptPrivateKeyForBackup(identity.private, passphrase)
        assertEquals(
            "Wersja kopii na pierwszym bajcie",
            E2eCrypto.BACKUP_VERSION.toLong(),
            blob[0].toLong(),
        )

        val restored = E2eCrypto.decryptPrivateKeyFromBackup(blob, passphrase)
        assertArrayEquals(
            "Odtworzony klucz musi byc identyczny",
            E2eCrypto.encodePrivateKey(identity.private),
            E2eCrypto.encodePrivateKey(restored),
        )

        var threw = false
        try {
            E2eCrypto.decryptPrivateKeyFromBackup(blob, "zle haslo".toCharArray())
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("Zle haslo musi zostac odrzucone, a nie dac smieci", threw)
    }

    // ------------------------------------------------------------- pomoc

    private fun sealFromVectors(): E2eCrypto.Sealed {
        val eph = E2eCrypto.keyPairFromScalar(hex(keys, "eph_priv_hex"), b64(keys, "eph_pub_b64"))
        return E2eCrypto.seal(
            plaintext = message.get("plaintext_utf8").asString.toByteArray(Charsets.UTF_8),
            recipients = listOf("b" to b64(keys, "b_pub_b64")),
            ephemeral = eph,
            bodyIv = b64(message, "body_iv_b64"),
            wrapIvs = listOf(b64(message, "wrap_iv_b64")),
            messageKey = b64(message, "mk_b64"),
        )
    }

    private fun loadVectors(): JsonObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("e2e_vectors.json")
            ?: error("Brak e2e_vectors.json w app/src/test/resources — skopiuj z mini/scripts/")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return JsonParser.parseString(text).asJsonObject
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
