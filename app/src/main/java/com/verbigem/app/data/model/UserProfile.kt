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
    val noAdsUntil: Long? = null,
    val walletCreditsCents: Long = 0,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    /**
     * Status PRO jest WYLICZANY, nie zapisany: konto jest PRO wtedy i tylko wtedy,
     * gdy spełniony jest co najmniej jeden z dwóch warunków:
     *   1. aktywne wykupienie wyłączenia bannera (noAdsUntil w przyszłości),
     *   2. saldo portfela > 0.
     * Gdy żaden nie jest spełniony (np. wygaśnięcie noAds przy zerowym portfelu),
     * konto wraca na FREE. Zapisane pole [plan] jest tylko informacyjne i NIE
     * może być tu używane jako źródło prawdy.
     */
    val isPro: Boolean
        get() = (noAdsUntil != null && noAdsUntil > System.currentTimeMillis()) || walletCreditsCents > 0
}
