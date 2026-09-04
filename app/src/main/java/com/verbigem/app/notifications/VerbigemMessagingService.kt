package com.verbigem.app.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.verbigem.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM messages.
 *
 * The Cloud Function sends **data-only** messages (no `notification` payload). That is
 * a deliberate trade-off:
 *
 *  - With a `notification` payload the system renders the notification itself while the
 *    app is backgrounded, and `onMessageReceived` is never called — so the reply and
 *    "mark as read" actions would only exist for messages that arrive while the app is
 *    open. Half-working actions are worse than none.
 *  - With data-only, [onMessageReceived] runs every time (foreground, background, and
 *    after process death), so the notification always carries the full action set.
 *    The cost is that delivery is subject to Doze/App Standby; the function sends with
 *    `priority: high` to keep that window as small as possible.
 */
class VerbigemMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Fires on first install and whenever Google rotates the token.
     *
     * With no signed-in user there is nothing to attach the token to, so it is simply
     * dropped — [FcmTokenManager.registerCurrentToken] runs from the auth-state listener
     * in `VerbigemApplication` as soon as a session exists.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch { FcmTokenManager.saveToken(uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data["type"] != TYPE_CHAT_MESSAGE) return

        val chatId = data["chatId"]
        val authorId = data["authorId"]
        if (chatId.isNullOrBlank() || authorId.isNullOrBlank()) {
            Log.w(TAG, "Chat message push without chatId/authorId: $data")
            return
        }

        // Title and body are already localised by the Cloud Function (it knows the
        // recipient's uiLang); the device just renders them.
        val title = data["title"]?.takeIf { it.isNotBlank() }
            ?: getString(R.string.app_name)
        val body = data["body"]?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notif_new_message)

        VerbigemNotifications.showMessage(
            context = this,
            chatId = chatId,
            authorId = authorId,
            authorName = title,
            body = body
        )
    }

    override fun onDeletedMessages() {
        // FCM could not deliver (>100 pending messages or the device was offline for a
        // long time). Nothing to do: the thread is rebuilt from Firestore on open, and a
        // missing push is a missing buzz — not a missing message.
        super.onDeletedMessages()
        Log.w(TAG, "FCM dropped pending messages for this device")
    }

    companion object {
        private const val TAG = "VerbigemMessagingService"
        private const val TYPE_CHAT_MESSAGE = "chat_message"
    }
}
