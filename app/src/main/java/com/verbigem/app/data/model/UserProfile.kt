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
    val isPro: Boolean
        get() = plan == "pro" || (noAdsUntil != null && noAdsUntil > System.currentTimeMillis()) || walletCreditsCents > 0
}
