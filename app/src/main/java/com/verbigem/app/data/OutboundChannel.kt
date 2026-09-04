package com.verbigem.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import com.verbigem.app.R

/**
 * Komu chcemy coś przekazać — osoba spoza Verbigem.
 *
 * To **nie** jest docelowa encja `external_contacts` z zadania 3.6, tylko lekki
 * nośnik na czas lotu do innej aplikacji. Kiedy 3.6 wprowadzi tabelę w Room,
 * encja dostanie `toTarget()` i reszta kodu się nie zmieni. Robienie z tego
 * encji bazy danych teraz byłoby zgadywaniem, jak ma wyglądać coś, czego
 * jeszcze nie zapisujemy.
 */
data class OutboundTarget(
    val name: String,
    /** Pełny numer międzynarodowy (`+5959…`) albo pusty, gdy nie udało się ustalić. */
    val e164: String,
    /** Cyfry z wiodącym `+` — postać do wyświetlania i do `smsto:` / `wa.me`. */
    val loose: String,
    val email: String
) {
    companion object {
        fun from(contact: PhoneContact): OutboundTarget = OutboundTarget(
            name = contact.name,
            e164 = contact.e164Candidates.firstOrNull().orEmpty(),
            loose = contact.phone,
            email = contact.email
        )
    }
}

/**
 * Kanał, którym oddajemy wiadomość innej aplikacji.
 *
 * Verbigem tłumaczy, potem **przekazuje** — nie próbujemy wysyłać niczego sami.
 * To jedyna uczciwa postać kontaktu z kimś, kto nie ma konta: nie wiemy, czy
 * wiadomość dotarła, i nie udajemy, że wiemy.
 */
interface OutboundChannel {

    /**
     * Stabilny identyfikator zapisywany w historii wysyłek (`external_outbox`).
     *
     * Nie etykieta: etykieta jest tłumaczona, więc po zmianie języka stare wiersze
     * stałyby się nie do odczytania. Nie nazwa klasy: ta zmienia się przy
     * refactoringu, a historia zostaje.
     */
    val id: String

    val labelRes: Int
        @StringRes get

    /**
     * Czy ten kanał da się użyć dla tego odbiorcy: czy mamy potrzebny adres
     * (numer dla SMS/WhatsApp, e-mail dla poczty) i czy cokolwiek na telefonie
     * potrafi obsłużyć intencję.
     */
    fun isAvailable(context: Context, target: OutboundTarget): Boolean

    /**
     * Oddaje wiadomość aplikacji zewnętrznej.
     *
     * @param link osobno, bo Telegram potrafi wkleić tekst **tylko** przez
     *   `t.me/share/url?url=…&text=…`, a parametr `url` musi być samym linkiem.
     *   Wyciąganie go z treści byłoby zgadywaniem.
     * @return true, gdy ktoś to przejął.
     */
    fun handOff(context: Context, target: OutboundTarget, text: String, link: String): Boolean
}

/**
 * WhatsApp: `wa.me/<numer>?text=<tekst>`.
 *
 * Numer musi być bez `+` i bez spacji — to wymaganie samego `wa.me`, nie nasze.
 * Celujemy w `com.whatsapp`, potem w `com.whatsapp.w4b` (Business), a gdy nie ma
 * żadnego, puszczamy bez paczki: otworzy się przeglądarka z WhatsApp Web albo
 * systemowy wybór aplikacji.
 *
 * `isAvailable` celowo sprawdza tylko „czy cokolwiek otworzy ten link", a nie
 * „czy ten numer ma konto na WhatsApp". Tej drugiej odpowiedzi nie da się uzyskać
 * bez zewnętrznego API, a udawanie, że wiemy, byłoby gorsze niż przycisk, który
 * po prostu otworzy WhatsApp.
 */
object WhatsAppChannel : OutboundChannel {

    override val id: String = "whatsapp"


    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    override val labelRes: Int = R.string.channel_whatsapp

    override fun isAvailable(context: Context, target: OutboundTarget): Boolean =
        digits(target).isNotBlank() && context.canBeHandled(intent(context, target, ""))

    override fun handOff(
        context: Context,
        target: OutboundTarget,
        text: String,
        link: String
    ): Boolean = context.start(intent(context, target, bodyOf(text, link)))

    private fun digits(target: OutboundTarget): String =
        (target.e164.ifBlank { target.loose }).filter { it.isDigit() }

    private fun intent(context: Context, target: OutboundTarget, body: String): Intent {
        val uri = Uri.parse("https://wa.me/${digits(target)}")
            .buildUpon()
            .appendQueryParameter("text", body)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        // Preferuj właściwą aplikację; bez niej `wa.me` i tak otworzy się w sieci.
        listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { context.hasPackage(it) }
            ?.let { intent.setPackage(it) }
        return intent
    }
}

/**
 * Telegram: `t.me/share/url?url=<link>&text=<tekst>`.
 *
 * **To nie jest to, co mówi plan.** §5.4 zakładał `t.me/<username>` i pójście na
 * schowek, bo zwykły URL Telegrama nie potrafi wkleić tekstu. `t.me/share/url`
 * potrafi — i to z jednoczesnym podaniem linku, czyli dokładnie tym, czego
 * potrzebuje zaproszenie. Odpada więc snackbar „skopiowaliśmy do schowka".
 *
 * Nie da się natomiast trafić do **konkretnej osoby** — `share/url` otwiera
 * wybór rozmowy. Uczciwie: kanał jest dostępny zawsze, gdy jest co otworzyć, i
 * nie udaje, że wie, komu piszesz.
 */
object TelegramChannel : OutboundChannel {

    override val id: String = "telegram"


    override val labelRes: Int = R.string.channel_telegram

    override fun isAvailable(context: Context, target: OutboundTarget): Boolean =
        context.canBeHandled(intent("https://t.me", ""))

    override fun handOff(
        context: Context,
        target: OutboundTarget,
        text: String,
        link: String
    ): Boolean {
        // Bez linku `share/url` nie ma sensu — zostaje zwykły arkusz udostępniania.
        if (link.isBlank()) return context.sharePlain(bodyOf(text, null))
        return context.start(intent(link, text))
    }

    private fun intent(link: String, text: String): Intent {
        val uri = Uri.parse("https://t.me/share/url")
            .buildUpon()
            .appendQueryParameter("url", link)
            .appendQueryParameter("text", text)
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }
}

/**
 * SMS: `smsto:<numer>` z prewypełnioną treścią.
 *
 * **Celowo nie używamy `SmsManager`.** Wysyłka w tle wymaga uprawnienia
 * `SEND_SMS`, a to w polityce Google Play oznacza wniosek, uzasadnienie i
 * ryzyko odrzucenia — dla funkcji, która i tak musiałaby spytać użytkownika, czy
 * na pewno. `ACTION_SENDTO` daje to samo za jednym kliknięciem.
 *
 * Treść jest dublowana pod dwoma kluczami: `sms_body` to nazwa, którą znają
 * wszystkie aplikacje SMS, `EXTRA_TEXT` ta, której używają nowsze.
 */
object SmsChannel : OutboundChannel {

    override val id: String = "sms"


    override val labelRes: Int = R.string.channel_sms

    override fun isAvailable(context: Context, target: OutboundTarget): Boolean {
        val number = number(target)
        return number.isNotBlank() && context.canBeHandled(intent(number, ""))
    }

    override fun handOff(
        context: Context,
        target: OutboundTarget,
        text: String,
        link: String
    ): Boolean {
        val number = number(target)
        if (number.isBlank()) return false
        return context.start(intent(number, bodyOf(text, link)))
    }

    private fun number(target: OutboundTarget): String = target.e164.ifBlank { target.loose }

    private fun intent(number: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            if (body.isNotBlank()) {
                putExtra("sms_body", body)
                putExtra(Intent.EXTRA_TEXT, body)
            }
        }
}

/** E-mail: `mailto:` z tematem i treścią. Wymaga adresu — numer tu nie przejdzie. */
object EmailChannel : OutboundChannel {

    override val id: String = "email"


    override val labelRes: Int = R.string.channel_email

    override fun isAvailable(context: Context, target: OutboundTarget): Boolean =
        target.email.isNotBlank() && context.canBeHandled(intent(context, target, ""))

    override fun handOff(
        context: Context,
        target: OutboundTarget,
        text: String,
        link: String
    ): Boolean {
        if (target.email.isBlank()) return false
        return context.start(intent(context, target, bodyOf(text, link)))
    }

    private fun intent(context: Context, target: OutboundTarget, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf(target.email))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.channel_email_subject))
            putExtra(Intent.EXTRA_TEXT, body)
        }
}

/**
 * Systemowy arkusz udostępniania.
 *
 * Zostaje jako ostatnia deska ratunku, ale jest **lepszy, niż się wydaje**: to on
 * pokrywa Signal, Messenger i wszystko, czego nie przewidzieliśmy. Umieszczamy go
 * na końcu listy, nie na początku, bo celowe kanały oszczędzają użytkownikowi
 * wyboru.
 */
object SystemShareChannel : OutboundChannel {

    override val id: String = "system"


    override val labelRes: Int = R.string.channel_other

    override fun isAvailable(context: Context, target: OutboundTarget): Boolean =
        context.canBeHandled(plain(""))

    override fun handOff(
        context: Context,
        target: OutboundTarget,
        text: String,
        link: String
    ): Boolean = context.start(chooser(plain(bodyOf(text, link))))

    private fun plain(body: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
        }
}

/** Kanały w kolejności, w jakiej je pokazujemy: od najbardziej konkretnego. */
object OutboundChannels {

    val all: List<OutboundChannel> = listOf(
        WhatsAppChannel,
        SmsChannel,
        EmailChannel,
        TelegramChannel,
        SystemShareChannel
    )

    /**
     * Tylko te, których da się użyć dla tego odbiorcy.
     *
     * Filtrowanie po adresach (numer / e-mail) i po zainstalowanych aplikacjach
     * naraz: przycisk „SMS" nie ma sensu, gdy wpis w książce nie ma numeru, a
     * „E-mail", gdy nikt na telefonie nie ma klienta poczty.
     */
    fun availableFor(context: Context, target: OutboundTarget): List<OutboundChannel> =
        all.filter { it.isAvailable(context, target) }

    /**
     * Etykieta dla zapisanego w historii `id`.
     *
     * Historia trzyma id, nie tekst, więc odczyt musi wrócić do tłumaczenia w
     * momencie wyświetlania — wtedy wiersz sprzed miesiąca czyta się w języku,
     * który użytkownik ma dziś. Nieznane id (kanał usunięty z kodu) dostaje
     * etykietę „inne", bo zniknięcie napisu z historii byłoby gorsze niż napis
     * przybliżony.
     */
    fun labelResFor(id: String): Int =
        all.firstOrNull { it.id == id }?.labelRes ?: R.string.channel_other
}

// ----------------------------------------------------------------- helpers

/** Treść i link w jednej wiadomości. WhatsApp i SMS nie pokazują linku osobno. */
private fun bodyOf(text: String, link: String?): String =
    listOf(text, link.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")

private fun chooser(intent: Intent): Intent = Intent.createChooser(intent, null)

private fun Context.hasPackage(pkg: String): Boolean = runCatching {
    packageManager.getLaunchIntentForPackage(pkg) != null
}.getOrDefault(false)

/** Czy cokolwiek na telefonie otworzy tę intencję. */
private fun Context.canBeHandled(intent: Intent): Boolean = runCatching {
    intent.resolveActivity(packageManager) != null
}.getOrDefault(false)

/**
 * Odpala intencję. `FLAG_ACTIVITY_NEW_TASK` jest konieczny, bo startujemy z
 * kontekstu aplikacji, nie z aktywności — bez tej flagi `startActivity` rzuca.
 */
private fun Context.start(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    // Nic na telefonie nie przyjęło intencji — kanał znika z listy przy następnym
    // otwarciu, bo `isAvailable` też zwróci false.
    false
} catch (e: SecurityException) {
    false
}

private fun Context.sharePlain(text: String): Boolean = start(
    chooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    )
)
