package com.verbigem.app.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.verbigem.app.data.PhoneContact
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

/** What came of tapping "invite" on an address-book row. */
enum class ContactInviteStatus {
    /** Stored against the number; it turns into a friend request if they ever join. */
    INVITED,

    /** They already have an account — the friend request went out right away. */
    FRIEND_REQUESTED,

    /** Not actionable: the number is our own, or nothing came back. */
    NOOP
}

/**
 * Asks the cloud which of the user's contacts are already on Verbigem, and leaves
 * invitations for the rest.
 *
 * What leaves the phone: `SHA-256(E.164)` per candidate spelling, nothing else. No
 * names, no raw numbers. The Cloud Function turns each hash into
 * `HMAC-SHA256(hash, pepper)` and looks that up in `phoneDirectory`, which no client
 * can read. A stolen database therefore cannot be reversed into phone numbers without
 * the pepper, which lives in Secret Manager.
 *
 * Two implementation details that are easy to get wrong:
 *
 * 1. **Hashes are per candidate spelling, not per contact.** `0981 123 456` and
 *    `+595981123456` hash differently, so the number has to be in full E.164 before
 *    it is hashed. [PhoneContact.e164Candidates] carries the guesses.
 * 2. **Requests are chunked.** The function accepts 1000 hashes, and a big address
 *    book with two candidates per number blows past that silently — the call would
 *    fail with `invalid-argument` and the screen would show nothing.
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

        // hash -> which contact produced it. Several spellings of one number collapse
        // into one entry here, and so does the same number stored on two contacts.
        val indexByHash = LinkedHashMap<String, Int>()
        contacts.forEachIndexed { index, contact ->
            val candidates = contact.e164Candidates.ifEmpty {
                // Older importers, or a number that could not be parsed at all. The
                // loose form is wrong for international numbers but it is all we have,
                // and it hashes the same way the server does for an E.164-spelled one.
                listOf(contact.phone)
            }
            candidates.forEach { candidate ->
                if (candidate.isNotBlank()) indexByHash.putIfAbsent(sha256Hex(candidate), index)
            }
        }
        if (indexByHash.isEmpty()) return emptyList()

        val hashes = indexByHash.keys.toList()
        val found = LinkedHashMap<Int, ContactMatch>()

        for (chunk in hashes.chunked(MAX_HASHES)) {
            val result = call(FUNCTION_MATCH) { hashMapOf("hashes" to chunk) } ?: continue
            @Suppress("UNCHECKED_CAST")
            val matches = result["matches"] as? List<Map<String, Any>> ?: continue

            for (entry in matches) {
                // `index` is the position inside the chunk we sent, not inside
                // `contacts` — the map below is what turns one into the other.
                val index = (entry["index"] as? Number)?.toInt() ?: continue
                val uid = entry["uid"] as? String ?: continue
                val contactIndex = indexByHash[chunk.getOrNull(index)] ?: continue
                val contact = contacts.getOrNull(contactIndex) ?: continue

                // First match wins: a number stored under two candidate spellings that
                // both resolve must not appear twice in the list.
                if (found.containsKey(contactIndex)) continue

                found[contactIndex] = ContactMatch(
                    phone = contact.phone,
                    uid = uid,
                    nickname = entry["nickname"] as? String ?: "",
                    photoURL = entry["photoURL"] as? String ?: ""
                )
            }
        }

        return found.values.toList()
    }

    /**
     * Leaves an invitation against one contact's number.
     *
     * The row's share sheet still opens afterwards — registering the invitation and
     * sending a link are the same intent expressed twice, and either one alone is
     * incomplete: plenty of people install an app from a link and never verify the
     * number they were invited under.
     */
    suspend fun invite(contact: PhoneContact): ContactInviteStatus {
        val candidates = contact.e164Candidates.ifEmpty { listOf(contact.phone) }
        val hashes = candidates.map(::sha256Hex)
        val result = call(FUNCTION_INVITE) { hashMapOf("hashes" to hashes) }
            ?: return ContactInviteStatus.NOOP

        @Suppress("UNCHECKED_CAST")
        val results = result["results"] as? List<Map<String, Any>> ?: return ContactInviteStatus.NOOP
        val status = results.firstNotNullOfOrNull { it["status"] as? String }
        return when (status) {
            "friend_requested" -> ContactInviteStatus.FRIEND_REQUESTED
            "invited" -> ContactInviteStatus.INVITED
            else -> ContactInviteStatus.NOOP
        }
    }

    /** Runs one callable, turning transport errors into [ContactMatchException]. */
    private suspend fun call(
        name: String,
        args: () -> HashMap<String, Any>
    ): Map<String, Any>? {
        val result = try {
            functions.getHttpsCallable(name).call(args()).await()
        } catch (e: FirebaseFunctionsException) {
            throw ContactMatchException(e.code.toFailure())
        } catch (e: Exception) {
            // A network error is not worth crashing for; the screen just shows the
            // address book without matches.
            Log.w(TAG, "$name call failed", e)
            throw ContactMatchException(ContactMatchFailure.UNKNOWN)
        }

        @Suppress("UNCHECKED_CAST")
        return result.data as? Map<String, Any>
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
        private const val FUNCTION_MATCH = "matchContacts"
        private const val FUNCTION_INVITE = "inviteByPhone"

        /** Must stay at or under the server's MAX_HASHES. */
        private const val MAX_HASHES = 1000
    }
}
