package com.verbigem.app.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.verbigem.app.data.PhoneContact
import com.verbigem.app.data.PhoneContactsImporter
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/** One address-book entry that turned out to already have a Verbigem account. */
data class ContactMatch(
    /** The normalized phone number from the address book, so the UI can find its row. */
    val phone: String,
    val uid: String,
    val nickname: String,
    val photoURL: String
)

/** Why a lookup failed — the ViewModel turns this into a localized message. */
enum class ContactMatchFailure {
    RATE_LIMITED,
    NOT_CONFIGURED,
    UNAUTHENTICATED,
    UNKNOWN
}

class ContactMatchException(val reason: ContactMatchFailure) : Exception()

/**
 * Asks the `matchContacts` Cloud Function which of the user's contacts are already
 * on Verbigem.
 *
 * What leaves the phone: `SHA-256(normalized number)` per contact, nothing else. No
 * names, no raw numbers. The Cloud Function turns each hash into
 * `HMAC-SHA256(hash, pepper)` and looks that up in `phoneDirectory`, which no client
 * can read. A stolen database therefore cannot be reversed into phone numbers
 * without the pepper, which lives in Secret Manager.
 */
class ContactMatchRepository {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    /**
     * @param contacts entries straight from the address book
     * @return the subset that has an account; empty when there is nothing to send or
     *         nobody matched
     */
    suspend fun match(contacts: List<PhoneContact>): List<ContactMatch> {
        if (contacts.isEmpty()) return emptyList()

        // Normalize again before hashing: the address book may hold the same number
        // in two spellings, and a difference of one character produces a completely
        // different hash — the match would silently fail.
        val normalized = contacts.map { it.copy(phone = PhoneContactsImporter.normalize(it.phone)) }

        val hashes = normalized.map { sha256Hex(it.phone) }
        val result = try {
            functions.getHttpsCallable(FUNCTION_NAME)
                .call(hashMapOf("hashes" to hashes))
                .await()
        } catch (e: FirebaseFunctionsException) {
            throw ContactMatchException(e.code.toFailure())
        } catch (e: Exception) {
            // A network error is not a bug worth crashing for; the screen just shows
            // the address book without matches.
            Log.w(TAG, "Contact matching call failed", e)
            throw ContactMatchException(ContactMatchFailure.UNKNOWN)
        }

        @Suppress("UNCHECKED_CAST")
        val matches = (result.data as? Map<String, Any>)?.get("matches") as? List<Map<String, Any>>
            ?: return emptyList()

        return matches.mapNotNull { entry ->
            val index = (entry["index"] as? Number)?.toInt() ?: return@mapNotNull null
            val uid = entry["uid"] as? String ?: return@mapNotNull null
            if (index !in normalized.indices) return@mapNotNull null
            ContactMatch(
                phone = normalized[index].phone,
                uid = uid,
                nickname = entry["nickname"] as? String ?: "",
                photoURL = entry["photoURL"] as? String ?: ""
            )
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun FirebaseFunctionsException.Code.toFailure(): ContactMatchFailure = when (this) {
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ContactMatchFailure.RATE_LIMITED
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> ContactMatchFailure.NOT_CONFIGURED
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> ContactMatchFailure.UNAUTHENTICATED
        else -> ContactMatchFailure.UNKNOWN
    }

    companion object {
        private const val TAG = "ContactMatchRepository"
        private const val FUNCTION_NAME = "matchContacts"
    }
}
