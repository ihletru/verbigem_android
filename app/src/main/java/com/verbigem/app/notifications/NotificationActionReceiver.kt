package com.verbigem.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.verbigem.app.data.repository.ChatRepository
import com.verbigem.app.notifications.VerbigemNotifications.ACTION_MARK_READ
import com.verbigem.app.notifications.VerbigemNotifications.ACTION_REPLY
import com.verbigem.app.notifications.VerbigemNotifications.EXTRA_CHAT_ID
import com.verbigem.app.notifications.VerbigemNotifications.KEY_TEXT_REPLY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Handles the two notification actions: inline reply and "mark as read".
 *
 * Both need Firestore, i.e. real async work inside a [BroadcastReceiver], which is why
 * every branch runs under `goAsync()` — without it the process can be killed the moment
 * [onReceive] returns and the write would never happen.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        if (chatId.isNullOrBlank()) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> handleReply(chatId, intent)
                    ACTION_MARK_READ -> handleMarkRead(chatId)
                    else -> Log.w(TAG, "Unknown action: ${intent.action}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Notification action failed", e)
            } finally {
                // Either way the notification has served its purpose: the user acted on it.
                VerbigemNotifications.cancelChat(context, chatId)
                pendingResult.finish()
            }
        }
    }

    /**
     * Sends the typed text as a normal message in the user's own language.
     *
     * No `senderTranslation` hint here: producing one would require booting the on-device
     * translation engine inside a broadcast receiver, which is far too heavy. Per decision
     * D1 the hint is only a shortcut anyway — the recipient's device translates the raw
     * text on arrival, which is exactly what happens for every message sent from the
     * background.
     */
    private suspend fun handleReply(chatId: String, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString()?.trim()
        if (text.isNullOrEmpty()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return

        val sourceLang = readSpeakLang(uid)
        ChatRepository().sendMessage(
            chatId = chatId,
            authorId = uid,
            text = text,
            sourceLang = sourceLang,
            hintLang = "",
            hintText = "",
            clientMsgId = UUID.randomUUID().toString()
        )
    }

    private suspend fun handleMarkRead(chatId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ChatRepository().markRead(chatId, uid, System.currentTimeMillis())
    }

    /**
     * The language the user *writes* in, which is the `sourceLang` of anything they send.
     * Read straight from Firestore because there is no ViewModel alive here; falls back to
     * Polish so a failed read still sends the message rather than dropping it.
     */
    private suspend fun readSpeakLang(uid: String): String = try {
        FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            .getString("speakLangSource")?.takeIf { it.isNotBlank() } ?: "pl"
    } catch (e: Exception) {
        Log.w(TAG, "Could not read speakLangSource; defaulting to pl", e)
        "pl"
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
    }
}
