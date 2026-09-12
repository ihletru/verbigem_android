package com.verbigem.app.data.crypto

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Rdzeń kryptografii czatu E2E — patrz [`docs/czat-e2e.md`](../../../../../../../../docs/czat-e2e.md).
 *
 * **Czyste JCA, ZERO API Androida.** To nie jest przypadkiem: dzięki temu całość
 * da się uruchomić w teście jednostkowym na JVM (`app/src/test`), bez telefonu
 * i bez emulatora. Kryptografia, której nie da się przetestować, jest
 * kryptografią, o której nie wiadomo, czy działa. Trzymanie klucza w Android
 * Keystore należy do [`E2eKeyStore`] i celowo jest osobnym plikiem.
 *
 * ⚠️ **Format koperty jest przypięty wektorami** w `mini/scripts/e2e-vectors.mjs`
 * → `mini/scripts/e2e_vectors.json`. Ten sam plik leży w `app/src/test/resources/`
 * i test `E2eCryptoVectorsTest` sprawdza, że ta implementacja liczy identycznie.
 * Zmiana czegokolwiek poniżej (nazwa krzywej, `info` w HKDF, kolejność
 * `ciphertext||tag`) bez regeneracji wektorów = odbiorca zobaczy „nie można
 * odszyfrować" i nikt nie będzie wiedział, które pole się nie zgadza.
 */
object E2eCrypto {

    /** Wersja formatu zapisywana w kopercie. Podniesienie = starzy klienci nie odczytają nowych. */
    const val VERSION = 1

    /** Etykieta algorytmu — do diagnostyki w bazie, nie do logiki. */
    const val ALG = "AES-256-GCM+HKDF-SHA256+ECDH-P256"

    /** `info` w HKDF. Zmiana tego stringa unieważnia WSZYSTKIE istniejące koperty. */
    const val HKDF_INFO = "verbigem-chat-v1-wrap"

    /** Nazwa krzywej w JCA. To samo co `prime256v1` w Node i `P-256` w WebCrypto. */
    private const val CURVE = "secp256r1"

    const val IV_BYTES = 12
    const val KEY_BYTES = 32
    const val GCM_TAG_BITS = 128
    const val PUBLIC_KEY_BYTES = 65

    /** Iteracje PBKDF2 dla kopii zapasowej klucza (OWASP 2023 dla PBKDF2-HMAC-SHA256). */
    const val PBKDF2_ITERATIONS = 310_000
    const val PBKDF2_SALT_BYTES = 16

    private val random = SecureRandom()

    private val params: ECParameterSpec by lazy {
        val ap = AlgorithmParameters.getInstance("EC")
        ap.init(ECGenParameterSpec(CURVE))
        ap.getParameterSpec(ECParameterSpec::class.java)
    }

    private val keyFactory: KeyFactory by lazy { KeyFactory.getInstance("EC") }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /**
     * Odcisk klucza publicznego do porównania poza kanałem (TOFU) — patrz
     * `docs/czat-e2e.md` §6 i `e2eKeys.ts` w webappce. 16 hexów z SHA-256,
     * w 4 grupach, wielkie litery: „ABCD EFGH IJKL MNOP". Format MUSI być
     * zgodny z `fingerprintOf` z webappki (tam: `hex.slice(0,16)` + `.match(/.{4}/g)`).
     */
    fun fingerprint(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes)
        return digest.take(8)
            .joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString(" ")
    }

    // ------------------------------------------------------------------ klucze

    /** Nowa para tożsamości (P-256). W produkcji wołane raz na konto. */
    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(params) }.generateKeyPair()

    /** Para efemeryczna — jedna na wiadomość, wyrzucana po zaszyfrowaniu. */
    fun generateEphemeralKeyPair(): KeyPair = generateKeyPair()

    /**
     * Klucz publiczny w postaci **nieskompresowanej**: `0x04 || X(32) || Y(32)`.
     *
     * ⚠️ JCA domyślnie oddaje klucz w DER (SPKI), a Node i WebCrypto operują na
     * surowym punkcie. Bez tej konwersji wektory by się nie zgadzały, mimo że
     * matematyka byłaby poprawna — i debugowanie tego to godziny.
     */
    fun encodePublicKey(key: PublicKey): ByteArray {
        val point = (key as java.security.interfaces.ECPublicKey).w
        return byteArrayOf(0x04) + point.affineX.toFixed(32) + point.affineY.toFixed(32)
    }

    fun decodePublicKey(bytes: ByteArray): PublicKey {
        require(bytes.size == PUBLIC_KEY_BYTES && bytes[0] == 0x04.toByte()) {
            "Klucz publiczny musi miec 65 bajtow i prefiks 0x04 (dostalem ${bytes.size})"
        }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        return keyFactory.generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
    }

    /** Klucz prywatny jako PKCS#8 — format używany w kopii zapasowej. */
    fun encodePrivateKey(key: PrivateKey): ByteArray = key.encoded

    fun decodePrivateKey(bytes: ByteArray): PrivateKey =
        keyFactory.generatePrivate(PKCS8EncodedKeySpec(bytes))

    /** Klucz publiczny z DER (SPKI) — dla danych, które przyszły z innego źródła. */
    fun decodePublicKeyDer(bytes: ByteArray): PublicKey =
        keyFactory.generatePublic(X509EncodedKeySpec(bytes))

    /**
     * Para z surowego skalara + punktu. Potrzebne tylko testom wektorowym, które
     * mają stałe klucze — w produkcji para zawsze pochodzi z [generateKeyPair].
     */
    internal fun keyPairFromScalar(scalarHex: String, publicKeyBytes: ByteArray): KeyPair {
        val d = BigInteger(scalarHex, 16)
        val priv = keyFactory.generatePrivate(ECPrivateKeySpec(d, params))
        return KeyPair(decodePublicKey(publicKeyBytes), priv)
    }

    /** BigInteger → dokładnie [size] bajtów, z zerami z przodu (i bez znaku). */
    private fun BigInteger.toFixed(size: Int): ByteArray {
        val raw = toByteArray()
        // BigInteger dokłada bajt 0x00, gdy najstarszy bit jest jedynką (znak).
        val stripped = if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(stripped.size <= size) { "Liczba nie miesci sie w $size bajtach" }
        return ByteArray(size - stripped.size) + stripped
    }

    // -------------------------------------------------------------------- HKDF

    /**
     * HKDF-SHA256 (RFC 5869), ręcznie.
     *
     * ⚠️ JCA **nie ma** HKDF w API publicznym (jest dopiero od JDK 24), więc liczymy
     * extract + expand sami. Referencja w `mini/scripts/e2e-vectors.mjs` robi to
     * identycznie — celowo nie używa wygodnego `hkdfSync`, żeby wektory sprawdzały
     * tę samą ścieżkę, która pójdzie na produkcji.
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray {
        // `ByteArray.ifEmpty` nie istnieje w stdlib (jest tylko dla Array/Collection),
        // a RFC 5869 mówi wprost: brak soli = sól złożona z zer o długości hasha.
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmac(effectiveSalt, ikm)
        val infoBytes = info.toByteArray(Charsets.UTF_8)
        val out = ByteArray(length)
        var prev = ByteArray(0)
        var counter = 1
        var filled = 0
        while (filled < length) {
            val mac = hmacInstance(prk)
            mac.update(prev)
            mac.update(infoBytes)
            mac.update(byteArrayOf(counter.toByte()))
            val block = mac.doFinal()
            val take = minOf(block.size, length - filled)
            System.arraycopy(block, 0, out, filled, take)
            filled += take
            prev = block
            counter += 1
        }
        return out
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        hmacInstance(key).doFinal(data)

    private fun hmacInstance(key: ByteArray): Mac =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }

    // --------------------------------------------------------------- AES-GCM

    /** Zwraca `ciphertext || tag` — dokładnie tak, jak Node i WebCrypto. */
    fun aesGcmSeal(key: ByteArray, plaintext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(plaintext)
    }

    fun aesGcmOpen(key: ByteArray, blob: ByteArray, iv: ByteArray): ByteArray {
        require(blob.size > GCM_TAG_BITS / 8) { "Szyfrogram krotszy niz sam tag GCM" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(blob)
    }

    // ---------------------------------------------------------------- koperta

    /** Klucz wiadomości zawinięty dla jednego odbiorcy (urządzenia). */
    data class WrappedKey(val keyId: String, val iv: ByteArray, val wrap: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is WrappedKey && keyId == other.keyId && iv.contentEquals(other.iv) && wrap.contentEquals(other.wrap)

        override fun hashCode(): Int = keyId.hashCode() * 31 + wrap.contentHashCode()
    }

    /** Koperta zapisywana w dokumencie wiadomości. Wszystko, czego trzeba do odszyfrowania. */
    data class Envelope(
        val version: Int,
        val alg: String,
        val epk: ByteArray,
        val bodyIv: ByteArray,
        val keys: List<WrappedKey>,
    )

    data class Sealed(val envelope: Envelope, val body: ByteArray, val messageKey: ByteArray)

    /**
     * Buduje kopertę: losuje klucz wiadomości (albo bierze podany — testy), szyfruje
     * treść, a klucz wiadomości zawija osobno dla każdego odbiorcy.
     *
     * [recipients] to pary `keyId -> klucz publiczny (65 B)`. `keyId` identyfikuje
     * urządzenie odbiorcy; dla własnych innych urządzeń też trzeba podać — inaczej
     * po zmianie telefonu nie odczytasz tego, co sam wysłałeś.
     *
     * ⚠️ Para efemeryczna jest **jedna na wiadomość**, nie na odbiorcę. Dzięki temu
     * ujawnienie później klucza tożsamości nie odsłania starych wiadomości
     * (forward secrecy) — o ile klucz prywatny efemeryczny zostanie wyrzucony.
     */
    fun seal(
        plaintext: ByteArray,
        recipients: List<Pair<String, ByteArray>>,
        ephemeral: KeyPair,
        bodyIv: ByteArray,
        wrapIvs: List<ByteArray>,
        messageKey: ByteArray = randomBytes(KEY_BYTES),
    ): Sealed {
        require(recipients.isNotEmpty()) { "Koperta bez odbiorcow nie ma sensu" }
        require(recipients.size == wrapIvs.size) { "Liczba odbiorcow != liczba IV" }
        require(messageKey.size == KEY_BYTES) { "Klucz wiadomosci musi miec $KEY_BYTES bajtow" }

        val wrapped = recipients.mapIndexed { index, (keyId, pubBytes) ->
            val iv = wrapIvs[index]
            val shared = sharedSecret(ephemeral.private, decodePublicKey(pubBytes))
            val wrapKey = hkdfSha256(shared, iv, HKDF_INFO, KEY_BYTES)
            WrappedKey(keyId, iv, aesGcmSeal(wrapKey, messageKey, iv))
        }

        return Sealed(
            envelope = Envelope(VERSION, ALG, encodePublicKey(ephemeral.public), bodyIv, wrapped),
            body = aesGcmSeal(messageKey, plaintext, bodyIv),
            messageKey = messageKey,
        )
    }

    /**
     * Otwiera kopertę moim kluczem tożsamości.
     *
     * Zwraca `null`, gdy koperta nie zawiera wpisu dla [keyId] — to normalna
     * sytuacja (wiadomość wysłana, zanim to urządzenie dołączyło), a nie błąd.
     * Rzuca tylko wtedy, gdy wpis JEST, ale się nie odszyfrowuje: to znaczy, że
     * ktoś podmienił klucz albo format się rozjechał — i o tym trzeba krzyczeć.
     */
    fun open(envelope: Envelope, keyId: String, identity: PrivateKey, body: ByteArray): ByteArray? {
        val entry = envelope.keys.firstOrNull { it.keyId == keyId } ?: return null
        val epk = decodePublicKey(envelope.epk)
        val shared = sharedSecret(identity, epk)
        val wrapKey = hkdfSha256(shared, entry.iv, HKDF_INFO, KEY_BYTES)
        val messageKey = aesGcmOpen(wrapKey, entry.wrap, entry.iv)
        return aesGcmOpen(messageKey, body, envelope.bodyIv)
    }

    /**
     * Wspólny sekret ECDH (współrzędna X punktu).
     *
     * `internal` (nie `private`), bo test wektorowy musi sprawdzić TEN krok
     * osobno: gdyby HKDF i AES-GCM były poprawne, a ECDH liczyło coś innego,
     * komunikat błędu wskazałby na złe miejsce i szukanie trwałoby godzinami.
     */
    internal fun sharedSecret(mine: PrivateKey, theirs: PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").apply {
            init(mine)
            doPhase(theirs, true)
        }.generateSecret()

    // ------------------------------------------------- kopia klucza na serwerze

    /**
     * Klucz z hasła odzyskiwania. PBKDF2-HMAC-SHA256.
     *
     * ⚠️ To jest jedyne miejsce, w którym „klucz konta na serwerze" pozostaje E2E:
     * na serwer trafia `AES-GCM(ten klucz, klucz prywatny)`. Bez hasła jest to
     * losowy ciąg bajtów. Wariant „serwer trzyma klucz jawnym tekstem" został
     * odrzucony w projekcie i nie ma go w kodzie.
     */
    fun deriveKeyFromPassphrase(passphrase: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        require(salt.size >= 8) { "Sól PBKDF2 za krotka" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * 8)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /**
     * Koperta kopii zapasowej klucza prywatnego.
     *
     * Układ bajtów jest JAWNY i wersjonowany, bo odszyfruje to także webapp:
     * `wersja(1) || iteracje(4, big-endian) || sól(16) || iv(12) || AES-GCM(PKCS#8)`.
     * Wersja na początku, a nie w JSON-ie, żeby dało się rozpoznać format bez
     * parsowania i żeby przyszła zmiana KDF nie wymagała migracji dokumentów.
     */
    fun encryptPrivateKeyForBackup(
        privateKey: PrivateKey,
        passphrase: CharArray,
        salt: ByteArray = randomBytes(PBKDF2_SALT_BYTES),
        iterations: Int = PBKDF2_ITERATIONS,
        iv: ByteArray = randomBytes(IV_BYTES),
    ): ByteArray {
        val key = deriveKeyFromPassphrase(passphrase, salt, iterations)
        val body = aesGcmSeal(key, encodePrivateKey(privateKey), iv)
        return byteArrayOf(BACKUP_VERSION) +
            iterations.toBytes() +
            salt +
            iv +
            body
    }

    fun decryptPrivateKeyFromBackup(blob: ByteArray, passphrase: CharArray): PrivateKey {
        require(blob.isNotEmpty() && blob[0] == BACKUP_VERSION) {
            "Nieznana wersja kopii klucza (${blob.firstOrNull()?.toInt()})"
        }
        val iterations = readInt(blob, 1)
        val salt = blob.copyOfRange(5, 5 + PBKDF2_SALT_BYTES)
        val iv = blob.copyOfRange(5 + PBKDF2_SALT_BYTES, 5 + PBKDF2_SALT_BYTES + IV_BYTES)
        val body = blob.copyOfRange(5 + PBKDF2_SALT_BYTES + IV_BYTES, blob.size)
        val key = deriveKeyFromPassphrase(passphrase, salt, iterations)
        return decodePrivateKey(aesGcmOpen(key, body, iv))
    }

    const val BACKUP_VERSION: Byte = 1

    private fun Int.toBytes(): ByteArray =
        byteArrayOf((this ushr 24).toByte(), (this ushr 16).toByte(), (this ushr 8).toByte(), toByte())

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
