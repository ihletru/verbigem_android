package com.verbigem.app.util

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import java.util.Locale

/**
 * Rozwiązywanie tekstów w języku **interfejsu**, a nie w języku systemu.
 *
 * ## Problem, który to naprawia
 *
 * Język UI wybiera się w aplikacji (DataStore `uiLang`) i `MainActivity.LocalizationWrapper`
 * podstawia tak przetłumaczony kontekst przez `LocalContext`. Działa to dla `stringResource()`
 * w composables — ale **nie** dla kodu, który sięga po `Application`:
 *
 * ```kotlin
 * getApplication<Application>().getString(R.string.x)  // ❌ język SYSTEMU
 * appContext.getString(R.string.x)                     // ❌ język SYSTEMU
 * e.localizedMessage                                   // ❌ angielski tekst z SDK
 * ```
 *
 * `Application` nie wie nic o wyborze użytkownika, więc na polskim telefonie z interfejsem
 * ustawionym na angielski wychodziła polska wiadomość (i odwrotnie). Dokładnie ten objaw
 * zgłosił użytkownik: „Ustawiłem interfejs na angielski a komunikat o pobieraniu modelu
 * jest po polsku".
 *
 * ## Użycie
 *
 * W ViewModelu — bez odbiorcy, bo `AndroidViewModel` ma własne przeciążenie:
 *
 * ```kotlin
 * _errorMessage.value = uiString(R.string.translation_error_generic)
 * ```
 *
 * Poza ViewModelem (silnik, ekran, Toast) — na dowolnym `Context`:
 *
 * ```kotlin
 * Toast.makeText(this, uiString(R.string.hide_conversation_failed), Toast.LENGTH_SHORT).show()
 * ```
 *
 * `UiLangState.code` jest ustawiany raz na kompozycję przez `LocalizationWrapper`, więc
 * ViewModel-e nie muszą czytać DataStore, żeby pokazać tekst w dobrym języku.
 */
object UiLangState {

    /** Kod języka interfejsu, np. `"pl"`, `"en"`, `"zh"`. Domyślnie polski. */
    @Volatile
    var code: String = "pl"

    /**
     * Ten sam język jako [Locale] — do formatowania **dat i liczb**, nie tylko tekstów.
     *
     * Nic w aplikacji nie woła `Locale.setDefault()`, więc `Locale.getDefault()` to język
     * TELEFONU. Skutek był dokładnie taki jak przy tekstach: interfejs po polsku na
     * hiszpańskim telefonie pokazywał skróty dni tygodnia po hiszpańsku („lun" zamiast
     * „pon") i nazwę kraju po angielsku („Poland" zamiast „Polska").
     */
    val locale: Locale get() = Locale.forLanguageTag(code)

    // Resources dla bieżącego języka. `Application` jest singletonem, więc jeden cache
    // wystarcza — trzymamy go razem z językiem, dla którego powstał.
    @Volatile
    private var cachedLang: String? = null

    @Volatile
    private var cachedResources: Resources? = null

    fun resourcesOf(base: Context): Resources {
        val lang = code
        val cached = cachedResources
        if (cached != null && cachedLang == lang) return cached

        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(lang))
        // createConfigurationContext() zwraca goły ContextImpl (nie ContextWrapper), więc
        // bierzemy z niego WYŁĄCZNIE resources. Nigdy nie przekazuj go dalej jako Context —
        // bez łańcucha baseContext nie znajdzie Activity (patrz MainActivity.LocalizedContext).
        val resources = base.createConfigurationContext(config).resources
        cachedLang = lang
        cachedResources = resources
        return resources
    }
}

/** Tekst w języku interfejsu. */
fun Context.uiString(@StringRes resId: Int): String =
    UiLangState.resourcesOf(applicationContext).getString(resId)

/** Tekst w języku interfejsu, z podstawieniem argumentów (`%1$s`, `%1$d`, …). */
fun Context.uiString(@StringRes resId: Int, vararg formatArgs: Any): String =
    UiLangState.resourcesOf(applicationContext).getString(resId, *formatArgs)

/**
 * To samo dla ViewModel-i.
 *
 * `AndroidViewModel` **nie jest** `Context`-em, więc `uiString(R.string.x)` bez odbiorcy
 * nie miałoby się o co zaczepić — stąd osobne przeciążenie. Dzięki niemu w ViewModelu
 * pisze się po prostu `uiString(R.string.x)`, bez `getApplication<Application>()` przed
 * każdym komunikatem.
 */
fun AndroidViewModel.uiString(@StringRes resId: Int): String =
    UiLangState.resourcesOf(getApplication<Application>().applicationContext).getString(resId)

/** Wariant z argumentami — patrz [AndroidViewModel.uiString]. */
fun AndroidViewModel.uiString(@StringRes resId: Int, vararg formatArgs: Any): String =
    UiLangState.resourcesOf(getApplication<Application>().applicationContext)
        .getString(resId, *formatArgs)

/**
 * `Locale` języka interfejsu — do `SimpleDateFormat`, `String.format` i `getDisplayCountry`.
 *
 * Używaj tego **zamiast** `Locale.getDefault()` wszędzie, gdzie wynik zobaczy człowiek:
 * `Locale.getDefault()` to język telefonu, a ten bywa inny niż wybrany język interfejsu.
 */
val uiLocale: Locale get() = UiLangState.locale
