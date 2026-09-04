package com.verbigem.app.data

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Własny parser vCard — import kontaktów spoza książki (3.7).
 *
 * Celowo **bez zależności**. `ez-vcard` / `vcard` niosą za sobą całą maszynerię
 * (nazwiska strukturalne, zdjęcia, grupa), a my potrzebujemy tylko trzech pól:
 * nazwy, numeru i e-maila — dokładnie tyle, by otworzyć wątek jednokierunkowy (3.6)
 * albo zaprosić przez kanał (3.5).
 *
 * Obsługiwane: `BEGIN:VCARD`/`END:VCARD`, wiele kart w jednym pliku, `VERSION:2.1`
 * i `3.0`, `FN`, `N` (jako fallback nazwy), `TEL`, `EMAIL`. Parametry (`TYPE=`,
 * `PREF`) są ignorowane — bierzemy pierwszy trafiony TEL i pierwszy EMAIL na kartę.
 *
 * Celowo NIE obsługujemy: zdjęć (`PHOTO`), grup, adresów pocztowych, wielu numerów
 * na jedną kartę. vCard potrafi być nieskończenie skomplikowany; import to gest
 * „dodaj tę osobę, z którą ktoś mi podesłał wizytówkę", a nie migracja książki.
 */
object VcfImporter {

    /** Zwraca kontakty z pliku pod [uri]. Pusty plik / błędny URI = pusta lista. */
    fun parseUri(context: Context, uri: Uri): List<PhoneContact> {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            }
        }.getOrNull().orEmpty()
        return parse(text)
    }

    /** Parsuje czysty tekst vCard. Bezpieczne dla wszystkiego: ignoruje linie,
     * których nie rozumie, i nie rzuca na uszkodzonych kartach.
     *
     * @param isos kraje do rozwinięcia numeru do E.164 (z [PhoneNumbers]
     *   .defaultCountryIsos) — parser sam nie trzyma kontekstu, więc podajemy je
     *   wyżej, z [parseUri]. */
    fun parse(text: String, isos: List<String> = emptyList()): List<PhoneContact> {
        val cards = mutableListOf<PhoneContact>()
        var inCard = false
        var fn: String? = null
        var n: String? = null
        var tel: String? = null
        var email: String? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue

            val upper = line.uppercase()
            when {
                upper.startsWith("BEGIN:VCARD") -> {
                    inCard = true
                    fn = null; n = null; tel = null; email = null
                }
                upper.startsWith("END:VCARD") -> {
                    if (inCard) {
                        val phone = PhoneNumbers.loosely(tel.orEmpty())
                        if (phone.isNotBlank()) {
                            cards += PhoneContact(
                                name = (fn ?: n)?.trim().orEmpty().ifBlank { phone },
                                phone = phone,
                                e164Candidates = PhoneNumbers.e164Candidates(tel.orEmpty(), isos),
                                email = email?.trim().orEmpty()
                            )
                        }
                    }
                    inCard = false
                }
                inCard -> collect(line, upper) { kind, value ->
                    when (kind) {
                        Kind.FN -> if (fn == null) fn = value
                        Kind.N -> if (n == null) n = value
                        Kind.TEL -> if (tel == null) tel = value
                        Kind.EMAIL -> if (email == null) email = value
                    }
                }
            }
        }
        return cards.distinctBy { it.phone }
    }

    private enum class Kind { FN, N, TEL, EMAIL }

    /**
     * Wyciąga `kind` + `value` z jednej linii właściwości.
     *
     * Format: `NAME;PARAM1=PARAM2:VALUE`. Wartość może być zakodowana
     * (`ENCODING=QUOTED-PRINTABLE` / `BASE64`) — to celowo pomijamy, bo bajty
     * base64 w numerze telefonu nie mają sensu, a nazwa base64 byłaby bezużyteczna.
     * Zostawiamy tylko czysty tekst po ostatnim `:`.
     */
    private fun collect(line: String, upper: String, emit: (Kind, String) -> Unit) {
        val kind = when {
            upper.startsWith("FN") -> Kind.FN
            upper.startsWith("N:") || upper.startsWith("N;") -> Kind.N
            upper.startsWith("TEL") -> Kind.TEL
            upper.startsWith("EMAIL") -> Kind.EMAIL
            else -> return
        }
        val colon = line.indexOf(':')
        if (colon < 0) return
        val value = line.substring(colon + 1).trim()
        if (value.isNotBlank()) emit(kind, value)
    }
}
