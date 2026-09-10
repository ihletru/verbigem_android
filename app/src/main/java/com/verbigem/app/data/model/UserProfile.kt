package com.verbigem.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class UserProfile(
    val uid: String = "",
    val nickname: String = "",
    val email: String = "",
    val photoURL: String? = "🙂",
    val uiLang: String = "pl",
    val speakLangSource: String = "pl",
    val speakLangTarget: String = "en",
    val plan: String = "free",
    /**
     * Koniec wykupionego „Bez reklam" w milisekundach od epoki.
     *
     * UWAGA NA SCHEMAT: Cloud Function `paddleWebhook` w `mini/functions/index.js`
     * historycznie zapisywała tu `admin.firestore.Timestamp` (linie 446 i 486
     * w pliku w momencie pisania tego komentarza), a Android deklarował `Long?`
     * — efektem był crash logowania kont PRO
     * („Failed to convert a value of type com.google.firebase.Timestamp to long"
     * na polu `noAdsUntil`). Trzymamy więc `Any?` i wyciągamy ms przez
     * [noAdsUntilMs] — wzorowane na webappie
     * (`mini/src/engine/translate/index.ts:toMs`). Serwer od 2026-09-09 pisze
     * tu już zwykły `number` (ms), więc nowe dokumenty są spójne; stare
     * Timestamp-y nadal działają, bo [toNoAdsMs] akceptuje oba.
     */
    val noAdsUntil: Any? = null,
    val walletCreditsCents: Long = 0,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    /**
     * [noAdsUntil] przeliczone na milisekundy od epoki. Zwraca `null`, gdy
     * pole jest puste albo ma nieobsługiwany typ (obrona przed przyszłymi
     * zmianami schematu).
     */
    val noAdsUntilMs: Long?
        get() = toNoAdsMs(noAdsUntil)

    /**
     * Status PRO jest WYLICZANY, nie zapisany: konto jest PRO wtedy i tylko wtedy,
     * gdy spełniony jest co najmniej jeden z dwóch warunków:
     *   1. aktywne wykupienie wyłączenia bannera (noAdsUntil w przyszłości),
     *   2. saldo portfela > 0.
     * Gdy żaden nie jest spełniony (np. wygaśnięcie noAds przy zerowym portfelu),
     * konto wraca na FREE. Zapisane pole [plan] jest tylko informacyjne i NIE
     * może być tu używany jako źródło prawdy.
     */
    val isPro: Boolean
        get() = (noAdsUntilMs ?: 0L) > System.currentTimeMillis() || walletCreditsCents > 0

    companion object {
        /**
         * Normalizuje `noAdsUntil` (Number, String ISO, java.util.Date, albo
         * `com.google.firebase.Timestamp`) do milisekund. Zwraca `null` dla
         * pustych i nierozpoznanych wartości — żeby [isPro] nie wybuchał na
         * danych poza schematem. Prawdziwe porównanie „w przyszłości" i tak
         * wymaga wartości dodatniej.
         */
        private fun toNoAdsMs(v: Any?): Long? {
            if (v == null) return null
            return when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: runCatching {
                    java.time.Instant.parse(v).toEpochMilli()
                }.getOrNull()
                is java.util.Date -> v.time
                is Timestamp -> v.toDate().time
                is Map<*, *> -> {
                    // Firestore SDK czasem opakowuje Timestamp w mapę {seconds, nanos}
                    // przy deserializacji polimorficznej — wyciągnij to.
                    val sec = (v["seconds"] as? Number)?.toLong()
                    val ns = (v["nanoseconds"] as? Number)?.toLong() ?: 0L
                    if (sec != null) sec * 1000L + ns / 1_000_000L else null
                }
                else -> null
            }
        }
    }
}
