package com.verbigem.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Jeden wpis z książki telefonicznej, po normalizacji numeru. */
data class PhoneContact(
    val name: String,
    /**
     * Luźna normalizacja ([PhoneNumbers.loosely]): cyfry i wiodący `+`.
     * Dobra do wyświetlania i do linków `wa.me` / `smsto`.
     */
    val phone: String,
    /**
     * Możliwe pełne postacie E.164, od najbardziej prawdopodobnej.
     *
     * Matching idzie po skrótach, więc numer zapisany krajowo (`0981 123 456`)
     * musi zostać rozwinięty do `+595981123456` **przed** haszowaniem — inaczej
     * skrót nie zgadza się z tym, co funkcja policzyła z numeru zweryfikowanego
     * przez Firebase Auth. Bez libphonenumber kraj tylko zgadujemy, stąd lista.
     */
    val e164Candidates: List<String> = emptyList(),

    /**
     * Pierwszy adres e-mail przypisany do tego kontaktu, o ile jest.
     *
     * Potrzebny kanałowi e-mail (3.5) — `mailto:` nie przyjmie numeru. Czytamy go
     * dlatego, że `Phone.CONTENT_URI` i `Email.CONTENT_URI` to **dwa osobne
     * zapytania**: ten sam kontakt ma w nich osobne wiersze, a łączenie idzie po
     * `CONTACT_ID`. Większość wpisów w książce adresowej nie ma maila i to jest
     * normalne — kanał po prostu znika z listy.
     */
    val email: String = ""
)

/**
 * Odczyt książki telefonicznej przez `ContactsContract`.
 *
 * UWAGA (zgodność z polityką prywatności): wynik zostaje **na urządzeniu**.
 * Do chmury — i to dopiero w fazie 2/3, przez Cloud Function `matchContacts` —
 * wysyłamy wyłącznie skróty SHA-256 numerów, nigdy ich treści.
 *
 * TODO(faza 3.3): pełna normalizacja E.164 przez libphonenumber. Tu jest tylko
 * lekka wersja, wystarczająca do linków `wa.me/<numer>` i `smsto:<numer>`.
 */
object PhoneContactsImporter {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Zwraca posortowane, zdeduklikowane po numerze wpisy. Pusto, gdy brak zgody. */
    fun read(context: Context): List<PhoneContact> {
        if (!hasPermission(context)) return emptyList()

        val isos = PhoneNumbers.defaultCountryIsos(context)
        val emailsByContactId = readEmails(context)
        val contacts = mutableListOf<PhoneContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID
        )

        // ContentResolver potrafi rzucić (np. gdy dostawca kontaktów padł) —
        // wolno zwrócić pustą listę niż wysadzić ekran Kontaktów.
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val iName =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val iPhone = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val iContactId =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                while (cursor.moveToNext()) {
                    val name = if (iName >= 0) cursor.getString(iName).orEmpty().trim() else ""
                    val raw = if (iPhone >= 0) cursor.getString(iPhone).orEmpty() else ""
                    val contactId = if (iContactId >= 0) cursor.getLong(iContactId) else -1L
                    val phone = PhoneNumbers.loosely(raw)
                    if (phone.isBlank()) continue
                    contacts += PhoneContact(
                        name = name.ifBlank { phone },
                        phone = phone,
                        e164Candidates = PhoneNumbers.e164Candidates(raw, isos),
                        email = emailsByContactId[contactId].orEmpty()
                    )
                }
            }
        }

        return contacts.distinctBy { it.phone }
    }

    /**
     * `CONTACT_ID` → pierwszy adres e-mail tego kontaktu.
     *
     * Osobne zapytanie, bo adresy leżą w innej tabeli danych niż numery. Bierzemy
     * pierwszy trafiony: ktoś z trzema adresami i tak dostanie jedno zaproszenie,
     * a nie trzy.
     */
    private fun readEmails(context: Context): Map<Long, String> {
        val out = LinkedHashMap<Long, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val iId = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
                val iAddr = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                while (cursor.moveToNext()) {
                    val id = if (iId >= 0) cursor.getLong(iId) else continue
                    val address = if (iAddr >= 0) cursor.getString(iAddr).orEmpty().trim() else ""
                    if (address.isBlank() || out.containsKey(id)) continue
                    out[id] = address
                }
            }
        }
        return out
    }

    /**
     * Wygodny skrót tam, gdzie nie mamy kontekstu (np. podgląd w logach).
     * Wynik nadaje się do linków `wa.me` / `smsto`, **nie** do haszowania —
     * do matchingu używaj [PhoneNumbers.e164Candidates].
     */
    fun normalize(raw: String): String = PhoneNumbers.loosely(raw)
}
