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
    val e164Candidates: List<String> = emptyList()
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
        val contacts = mutableListOf<PhoneContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
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
                while (cursor.moveToNext()) {
                    val name = if (iName >= 0) cursor.getString(iName).orEmpty().trim() else ""
                    val raw = if (iPhone >= 0) cursor.getString(iPhone).orEmpty() else ""
                    val phone = PhoneNumbers.loosely(raw)
                    if (phone.isBlank()) continue
                    contacts += PhoneContact(
                        name = name.ifBlank { phone },
                        phone = phone,
                        e164Candidates = PhoneNumbers.e164Candidates(raw, isos)
                    )
                }
            }
        }

        return contacts.distinctBy { it.phone }
    }

    /**
     * Wygodny skrót tam, gdzie nie mamy kontekstu (np. podgląd w logach).
     * Wynik nadaje się do linków `wa.me` / `smsto`, **nie** do haszowania —
     * do matchingu używaj [PhoneNumbers.e164Candidates].
     */
    fun normalize(raw: String): String = PhoneNumbers.loosely(raw)
}
