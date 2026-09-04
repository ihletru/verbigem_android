package com.verbigem.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * The sender's own translation, sent ALONG with the original text.
 *
 * It is only a *hint*: the receiving device translates the original itself
 * (decision D1), and falls back to this hint when it has no model downloaded.
 * `lang` is the language the hint is written in, so the receiver can tell whether
 * the hint is even usable before showing it.
 */
data class SenderTranslation(
    val lang: String = "",
    val text: String = ""
)

/**
 * A chat message.
 *
 * `text` is ALWAYS the original, in `sourceLang`. Translation happens on the
 * receiving device; `senderTranslation` is only the hint for receivers that have
 * no model yet.
 *
 * The document id is the sender's `clientMsgId` (a UUID generated before the
 * network call), which makes a retry after a dropped connection idempotent —
 * Firestore `set()` on the same id is a no-op instead of a duplicate message.
 *
 * `translatedText` is legacy: messages written before phase 1 stored the hint as a
 * plain string. It is kept so old threads still render (see [hintText]).
 */
data class ChatMessage(
    val id: String = "",
    val authorId: String = "",
    val sourceLang: String = "pl",
    val text: String = "",
    val senderTranslation: SenderTranslation? = null,
    val type: String = "text",
    val clientMsgId: String = "",
    /** Faza 5: załącznik (zdjęcie/audio) — URL pobierania z Firebase Storage. */
    val attachmentUrl: String = "",
    /** Faza 5.2: wynik OCR ze zdjęcia, obliczony na urządzeniu nadawcy. */
    val ocrText: String = "",
    /** Faza 5.3: transkrypcja STT z nagrania, obliczona na urządzeniu nadawcy. */
    val transcript: String = "",
    /** LEGACY (pre-phase-1) sender hint. Never written any more, still read. */
    val translatedText: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    fun hintText(): String =
        senderTranslation?.text?.takeIf { it.isNotBlank() } ?: translatedText

    fun hintLang(): String =
        senderTranslation?.lang?.takeIf { it.isNotBlank() } ?: ""

    /** Zdjęcie (Faza 5.2): odbiorca renderuje miniaturę i tłumaczy [ocrText]. */
    fun isImage(): Boolean = type == "image"

    /** Nagranie głosowe (Faza 5.3). */
    fun isAudio(): Boolean = type == "audio"

    /** Czy wiadomość niesie załącznik w Storage. */
    fun hasAttachment(): Boolean = attachmentUrl.isNotBlank()
}

/**
 * One row of the inbox. Mirrors `chats/{chatId}`.
 *
 * The inbox query is a plain `whereArrayContains("members", uid)` with **no**
 * orderBy — sorting happens in the ViewModel. Firestore would need a composite
 * index for array-contains + orderBy on another field, and that index has to be
 * deployed separately; a handful of conversations is far cheaper to sort locally.
 */
data class ChatSummary(
    val chatId: String = "",
    val members: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageAuthorId: String = "",
    val lastMessageAt: Long = 0,
    /** Faza 5: rodzaj ostatniej wiadomości — inbox pokazuje zlokalizowany placeholder. */
    val lastMessageType: String = "text"
) {
    fun otherUid(me: String): String = members.firstOrNull { it != me }.orEmpty()
}

/**
 * Public, searchable projection of a user profile.
 *
 * Kept in `usersPublic/{uid}` because `users/{uid}` is readable ONLY by its owner
 * (see `firestore.rules`) — searching `users/` directly always ended in
 * PERMISSION_DENIED for everyone else.
 *
 * Deliberately minimal: nickname + e-mail + avatar + languages. No plan, no wallet,
 * no timestamps that would leak activity. `searchNick` / `searchEmail` are lowercased
 * copies, because Firestore has no case-insensitive `whereGreaterThanOrEqualTo`.
 *
 * `discoverableByPhone` is NOT written by the client — it is owned by Cloud Functions
 * (phase 2, phone verification). Writing it here would clobber a `true` set later.
 */
data class PublicProfile(
    val uid: String = "",
    val nickname: String = "",
    val photoURL: String? = null,
    val uiLang: String = "pl",
    val speakLangSource: String = "pl",
    val speakLangTarget: String = "en",
    val searchNick: String = "",
    val searchEmail: String = ""
) {
    companion object {
        fun from(profile: UserProfile): PublicProfile = PublicProfile(
            uid = profile.uid,
            nickname = profile.nickname,
            photoURL = profile.photoURL,
            uiLang = profile.uiLang,
            speakLangSource = profile.speakLangSource,
            speakLangTarget = profile.speakLangTarget,
            searchNick = profile.nickname.trim().lowercase(),
            searchEmail = profile.email.trim().lowercase()
        )
    }
}

/**
 * A friendship (or pending invitation) between two users.
 *
 * `members` is the field queries use (`whereArrayContains`), because Firestore has no
 * OR — a query on `uidA` alone only ever matched ONE of the two people involved.
 * `uidA`/`uidB` are kept for ordering and per-side nicknames. uidA is the
 * lexicographically smaller uid, so both devices compute the same document id.
 */
data class Friendship(
    val id: String = "",
    val uidA: String = "",
    val uidB: String = "",
    val members: List<String> = emptyList(),
    val status: String = "pending",
    val requestedBy: String = "",
    val nicknameA: String = "",
    val nicknameB: String = "",
    @ServerTimestamp
    val createdAt: Timestamp? = null
) {
    val isAccepted: Boolean
        get() = status == "accepted"

    /** The other person's uid, whichever side of the pair we are on. */
    fun otherUid(me: String): String =
        members.firstOrNull { it != me } ?: if (uidA == me) uidB else uidA

    /**
     * The other person's nickname. `nicknameA` belongs to `uidA` and `nicknameB`
     * to `uidB`, so we pick the one that is NOT ours.
     */
    fun otherNickname(me: String): String =
        if (uidA == me) nicknameB else nicknameA

    /** Pending invitation addressed to `me` (created by the other side). */
    fun isIncoming(me: String): Boolean = !isAccepted && requestedBy != me

    /** Pending invitation `me` sent out and is waiting on. */
    fun isOutgoing(me: String): Boolean = !isAccepted && requestedBy == me
}

/**
 * Per-contact settings, private to the owner: `users/{uid}/contacts/{otherUid}`.
 *
 * These are MY opinions about someone else, so they live under my own `users`
 * document and are never readable by the other person — an alias I give a contact
 * must not show up on their device.
 *
 * Kept in Firestore rather than Room on purpose: Firestore keeps its own offline
 * cache on Android, so the card still renders with no network, and the settings
 * follow the account to a second device without inventing a new sync mechanism.
 *
 * `langOverride` is the language incoming messages from this contact are translated
 * INTO, overriding the profile default. Blank means "use my profile language".
 * It deliberately does NOT change the language my outgoing messages are tagged
 * with — that one has to match what I actually type, or the receiver would
 * translate from the wrong source.
 */
data class ContactSettings(
    /** Shown instead of the nickname everywhere in the UI. Blank = no alias. */
    val alias: String = "",
    /** Language code (see [LangCode]) for incoming translations. Blank = profile default. */
    val langOverride: String = "",
    /** Suppresses the unread dot in the inbox. Server-side push silencing lands in phase 2. */
    val muted: Boolean = false,
    /** Floats the conversation to the top of the inbox. */
    val pinned: Boolean = false,
    /** Hides the conversation from my inbox. Client-side only — see the note in the README. */
    val blocked: Boolean = false,
    /** Free-form note, visible on the contact card only. */
    val note: String = "",
    val updatedAt: Long = 0
) {
    companion object {
        val EMPTY = ContactSettings()
    }
}

data class TranslationHistory(
    val id: Long = 0,
    // Stable cross-device key (UUID); empty only for pre-sync local rows.
    val syncId: String = "",
    val sourceText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Last local write time (ms). Drives last-write-wins merge during Firestore sync.
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Creates a new history entry with a fresh UUID syncId and current timestamps. */
        fun create(
            sourceText: String,
            translatedText: String,
            sourceLang: String,
            targetLang: String
        ): TranslationHistory {
            val now = System.currentTimeMillis()
            return TranslationHistory(
                syncId = java.util.UUID.randomUUID().toString(),
                sourceText = sourceText,
                translatedText = translatedText,
                sourceLang = sourceLang,
                targetLang = targetLang,
                timestamp = now,
                updatedAt = now
            )
        }
    }
}

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState
    data object LoadingToMemory : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}
