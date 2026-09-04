package com.verbigem.app.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Everything the app knows about turning "how a human typed a phone number" into
 * something two devices can agree on.
 *
 * WHY THIS FILE EXISTS: contact matching compares HASHES, not strings. `+595981123456`
 * and `0981 123 456` are the same number but hash to completely different values, so
 * anything that gets hashed has to be normalised to real E.164 first. That is the
 * job of [e164Candidates].
 *
 * [loosely] is the older, weaker normalisation. It is still the right answer for
 * `wa.me/<number>` and `smsto:<number>` links, where the number is shown to a human
 * and handed to another app — just never for hashing.
 *
 * TODO(faza 3.x): replace the country guessing with libphonenumber. Until then the
 * guess is "SIM country, then device locale country", which covers a resident with a
 * local SIM and misses an expat with a foreign one.
 */
object PhoneNumbers {

    /**
     * Plausible E.164 spellings of [raw], most likely first.
     *
     * More than one comes back when [raw] is written the national way
     * (`0981 123 456`) and we cannot be sure which country it belongs to. The caller
     * hashes every candidate and the server answers for whichever one is right — a
     * wrong guess costs one extra hash, not a missing friend.
     *
     * A number already written internationally is returned as-is, so it still matches
     * even when both country guesses are wrong.
     */
    fun e164Candidates(raw: String, defaultIsos: List<String>): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()

        val out = LinkedHashMap<String, Unit>()

        for (iso in defaultIsos) {
            // `formatNumberToE164` uses the system's bundled libphonenumber. It
            // returns null rather than guessing when the number cannot be parsed,
            // which is exactly what we want — a wrong E.164 silently never matches.
            val e164 = runCatching { PhoneNumberUtils.formatNumberToE164(trimmed, iso) }
                .getOrNull()
            if (!e164.isNullOrBlank()) out[e164] = Unit
        }

        val loose = loosely(trimmed)
        if (loose.startsWith("+")) out[loose] = Unit

        return out.keys.toList()
    }

    /**
     * Countries to try for numbers written without a country code, in order.
     *
     * The SIM's network country is the strongest signal — it is where the device
     * physically is. The device locale is the fallback for tablets and Wi-Fi-only
     * phones, where there is no SIM to ask.
     */
    fun defaultCountryIsos(context: Context): List<String> {
        val out = LinkedHashMap<String, Unit>()

        fun add(value: String?) {
            val iso = value?.trim()?.uppercase(Locale.US)
            if (iso != null && iso.length == 2 && iso.all { it.isLetter() }) out[iso] = Unit
        }

        // `networkCountryIso` is documented as needing READ_PHONE_STATE on newer
        // Android versions. We do not have that permission and do not want it —
        // a blank answer just drops us to the next guess.
        runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            add(tm?.networkCountryIso)
        }

        runCatching { add(context.resources.configuration.locales[0].country) }
        add(Locale.getDefault().country)

        return out.keys.toList()
    }

    /**
     * Loose normalisation: digits and a leading `+`, nothing else. `00…` becomes `+…`.
     *
     * Good enough for a `wa.me` link a human will look at. NOT good enough for
     * matching — see the class comment.
     */
    fun loosely(raw: String): String {
        val trimmed = raw.filter { it.isDigit() || it == '+' }
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("+") -> trimmed
            trimmed.startsWith("00") -> "+" + trimmed.removePrefix("00")
            else -> trimmed
        }
    }
}
