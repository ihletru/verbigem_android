package com.verbigem.app.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri

/**
 * Zewnetrzne linki Verbigem — jedno zrodlo prawdy dla calego projektu.
 *
 * Polityka prywatnosci jest hostowana statycznie na Firebase Hostingu
 * (projekt `mini`) pod `https://mini.verbigem.com/privacy/<lang>/`.
 * Ma 6 wersji jezykowych (pl, en, de, es, zh, tr) wygenerowanych skryptem
 * `mini/scripts/build_privacy.py` — treść NIE jest w aplikacji, wiec zmiana
 * nie wymaga nowego wydania APK.
 *
 * Google Play wymaga, zeby polityka byla dostepna pod publicznym URL-em oraz
 * linkowana z aplikacji — dlatego `AppLinks` uzywaja zarowno `ProfileScreen`,
 * jak i ekran prominent disclosure przed prośba o `READ_CONTACTS`.
 */
object AppLinks {

    /** Adres kontaktowy do spraw prywatności (zgodny z trescia polityki). */
    const val PRIVACY_EMAIL = "privacy@verbigem.com"

    private const val PRIVACY_BASE = "https://mini.verbigem.com/privacy/"

    private val PRIVACY_LANGS = setOf("pl", "en", "de", "es", "zh", "tr")

    /**
     * URL polityki prywatnosci w jezyku interfejsu.
     * Nieznany kod -> `/privacy/` (serwer przekierowuje wg jezyka przegladarki,
     * a bez JS pokazuje wersje angielska).
     */
    fun privacyPolicy(uiLang: String?): String {
        val code = uiLang?.lowercase()?.substringBefore("-")
        return if (code in PRIVACY_LANGS) "$PRIVACY_BASE$code/" else PRIVACY_BASE
    }

    /**
     * Wersja w jezyku, w ktorym aplikacja **faktycznie sie wyswietla**
     * (resources moga isc za jezykiem systemu, nie za preferencja `uiLang`).
     * Uzywaj tam, gdzie nie masz pod reka preferencji uzytkownika.
     */
    fun privacyPolicyFor(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return privacyPolicy(locale?.language)
    }
}

/** Zaproszenie do Verbigem — zwykły link + systemowy share sheet (§4.2 planu). */
object InviteLinks {
    private const val BASE = "https://mini.verbigem.com/app"
    fun forUser(fromUid: String): String = "$BASE?inv=$fromUid"
}

/**
 * Otwiera URL w przegladarce. `runCatching`, bo na urzadzeniu bez przegladarki
 * `startActivity` rzuca ActivityNotFoundException — lepiej nic nie zrobic
 * niz wysadzic ekran profilu.
 */
fun Context.openUrl(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

/**
 * Udostępnia tekst systemowym share sheetem. Jeden mechanizm obsługuje
 * SMS, WhatsAppa, Telegrama i e-mail — bez żadnej integracji i bez uprawnienia
 * `SEND_SMS` (które Google Play traktuje jako wysokiego ryzyka).
 */
fun Context.shareText(text: String, chooserTitle: String? = null) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
