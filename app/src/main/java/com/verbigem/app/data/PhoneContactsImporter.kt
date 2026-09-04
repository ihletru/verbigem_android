package com.verbigem.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Jeden wpis z książki telefonicznej, po normalizacji numeru. */
data class PhoneContact(
    val name: String,
    val phone: String
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
                    val phone = normalize(if (iPhone >= 0) cursor.getString(iPhone).orEmpty() else "")
                    if (phone.isNotBlank()) {
                        contacts += PhoneContact(name = name.ifBlank { phone }, phone = phone)
                    }
                }
            }
        }

        return contacts.distinctBy { it.phone }
    }

    /**
     * Lekka normalizacja: zostawiamy cyfry i wiodący `+`, `00` zamieniamy na `+`.
     * Bez libphonenumber nie ma pewności co do numeru kierunkowego kraju, więc
     * wynik nadaje się do ręcznego wysłania (wa.me / smsto), a nie do matchingu —
     * matching i tak idzie po skrótach i jest tematem fazy 2/3.
     */
    fun normalize(raw: String): String {
        val trimmed = raw.filter { it.isDigit() || it == '+' }
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("+") -> trimmed
            trimmed.startsWith("00") -> "+" + trimmed.removePrefix("00")
            else -> trimmed
        }
    }
}
