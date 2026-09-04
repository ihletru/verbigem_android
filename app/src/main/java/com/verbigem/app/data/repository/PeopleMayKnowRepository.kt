package com.verbigem.app.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * "Możesz znać" — friend-of-friend suggestions.
 *
 * The graph is computed entirely server-side by the `suggestFriends` callable: the
 * client only ever reads its own `friendships` (member-scoped Firestore rules), so it
 * cannot enumerate friends-of-friends on its own. The function walks the graph with the
 * Admin SDK and returns ranked candidates with a mutual-friend count. No phone numbers
 * or graph topology cross the wire.
 *
 * This is a non-critical, best-effort surface: any failure (offline, rate-limited,
 * function not yet deployed) yields an empty list and the UI simply hides the section.
 */
data class FriendSuggestion(
    val uid: String,
    val nickname: String,
    val photoURL: String,
    /** How many of my friends are also friends with this person. */
    val mutualCount: Int
)

class PeopleMayKnowRepository {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    /** Returns up to ~20 ranked candidates, or an empty list on any error. */
    suspend fun suggest(): List<FriendSuggestion> {
        val result = try {
            functions.getHttpsCallable(FUNCTION_SUGGEST).call().await()
        } catch (e: FirebaseFunctionsException) {
            // Rate-limited / unauthenticated / not-yet-deployed all just mean "no
            // suggestions this time" — the section is optional, never block the screen.
            Log.w(TAG, "suggestFriends failed (${e.code}): ${e.message}")
            return emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "suggestFriends call failed", e)
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        val suggestions = (result.data as? Map<String, Any>)
            ?.get("suggestions") as? List<Map<String, Any>>
            ?: return emptyList()

        return suggestions.mapNotNull { entry ->
            val uid = entry["uid"] as? String ?: return@mapNotNull null
            FriendSuggestion(
                uid = uid,
                nickname = entry["nickname"] as? String ?: "",
                photoURL = entry["photoURL"] as? String ?: "",
                mutualCount = (entry["mutualCount"] as? Number)?.toInt() ?: 0
            )
        }
    }

    companion object {
        private const val TAG = "PeopleMayKnowRepository"
        private const val FUNCTION_SUGGEST = "suggestFriends"
    }
}
