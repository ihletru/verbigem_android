package com.verbigem.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.verbigem.app.MainActivity
import com.verbigem.app.R

/**
 * Everything the app posts into the notification shade.
 *
 * Three design decisions worth knowing before editing this file:
 *
 * 1. **The channel id is shared with the Cloud Function.** `functions/src/messaging.ts`
 *    documents the same `verbigem_messages` id, and [ensureChannel] creates exactly
 *    that id here. Rename one without the other and pushes land in the wrong channel
 *    (or nowhere at all on Android 8+).
 * 2. **One notification per conversation, grouped under a single summary.** Each chat
 *    gets a stable notification id derived from `chatId`, so a second message from the
 *    same person updates the existing entry instead of stacking a new one. The summary
 *    exists because Android only *groups* notifications on Android 7+ when a
 *    group-summary notification is also posted.
 * 3. **Message history for the MessagingStyle is kept in memory, not read back out of
 *    the notification.** Reading it back (`extractMessagingStyleFromNotification`) is
 *    possible but depends on the system round-tripping the extras intact; a plain map
 *    is deterministic. The only cost is that a process restart starts a fresh
 *    notification — history is not worth persisting, the thread itself is in Firestore.
 */
object VerbigemNotifications {

    /** Must match `CHANNEL_ID` described in `functions/src/messaging.ts`. */
    const val CHANNEL_MESSAGES = "verbigem_messages"

    private const val GROUP_MESSAGES = "verbigem_chat_messages"
    private const val SUMMARY_ID = 0x0100_0000
    private const val ID_MASK = 0x00FF_FFFF
    private const val MAX_KEPT_MESSAGES = 12

    /** MainActivity reads this to open a thread straight from a notification tap. */
    const val EXTRA_OPEN_CHAT_UID = "open_chat_uid"

    const val ACTION_REPLY = "com.verbigem.app.notifications.REPLY"
    const val ACTION_MARK_READ = "com.verbigem.app.notifications.MARK_READ"
    const val EXTRA_CHAT_ID = "chat_id"
    const val EXTRA_AUTHOR_ID = "author_id"
    const val KEY_TEXT_REPLY = "key_text_reply"

    private class Line(val authorId: String, val authorName: String, val body: String, val at: Long)

    /** chatId -> most recent lines of that conversation, oldest first. */
    private val historyLock = Any()
    private val history = LinkedHashMap<String, MutableList<Line>>()

    /**
     * Stable per-conversation notification id. Masked so it can never collide with
     * [SUMMARY_ID] no matter what `String.hashCode()` returns.
     */
    fun notificationId(chatId: String): Int = chatId.hashCode() and ID_MASK

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_MESSAGES) != null) return
        val channel = NotificationChannel(
            CHANNEL_MESSAGES,
            context.getString(R.string.notif_channel_messages_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_messages_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Shows (or updates) the notification for one conversation.
     *
     * Safe to call from any thread — it only touches NotificationManager.
     */
    fun showMessage(
        context: Context,
        chatId: String,
        authorId: String,
        authorName: String,
        body: String
    ) {
        ensureChannel(context)
        // Without the runtime permission (Android 13+) `notify()` is a silent no-op,
        // so bail out instead of pretending the message was delivered.
        if (!hasPermission(context)) return

        val lines = appendHistory(chatId, Line(authorId, authorName, body, System.currentTimeMillis()))
        // NOTE: no `.apply { }` here, and that is not a style choice. `MessagingStyle`
        // declares its OWN `apply(NotificationBuilderWithBuilderAccessor)` member, which
        // shadows Kotlin's `apply` extension — the compiler picks the member, complains
        // that the lambda is not a builder, and every call inside goes "unresolved".
        // Use plain statements on this class.
        val style = NotificationCompat.MessagingStyle(mePerson())
        // Setting a conversation title also turns OFF group-conversation rendering,
        // which is what we want: this is a 1:1 chat.
        style.setConversationTitle(authorName)
        lines.forEach { style.addMessage(it.body, it.at, personFor(it.authorId, it.authorName)) }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setContentTitle(authorName)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup(GROUP_MESSAGES)
            .setContentIntent(contentPendingIntent(context, chatId))
            .addAction(replyAction(context, chatId))
            .addAction(markReadAction(context, chatId))
            .build()

        val manager = NotificationManagerCompat.from(context)
        manager.notify(notificationId(chatId), notification)

        // Grouping only actually groups when a summary exists, and only on N+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.notify(SUMMARY_ID, buildSummary(context))
        }
    }

    fun cancelChat(context: Context, chatId: String) {
        synchronized(historyLock) { history.remove(chatId) }
        NotificationManagerCompat.from(context).cancel(notificationId(chatId))
    }

    private fun appendHistory(chatId: String, line: Line): List<Line> = synchronized(historyLock) {
        val list = history.getOrPut(chatId) { mutableListOf() }
        list.add(line)
        while (list.size > MAX_KEPT_MESSAGES) list.removeAt(0)
        list.toList()
    }

    /**
     * The person the notification is *for*.
     *
     * `MessagingStyle` insists on a non-null "user" Person, but nothing renders it in a
     * 1:1 conversation, so no name (and no extra string resource) is needed.
     */
    private fun mePerson(): Person = Person.Builder().setKey("me").setName("").build()

    private fun personFor(uid: String, name: String): Person =
        Person.Builder().setKey(uid).setName(name).build()

    private fun buildSummary(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setSummaryText(context.getString(R.string.notif_group_summary))
            )
            .setGroup(GROUP_MESSAGES)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(inboxIntent(context))
            .build()

    // ------------------------------------------------------------------ intents

    private fun contentPendingIntent(context: Context, chatId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId(chatId),
            MainActivity.intentForChat(context, chatId),
            pendingIntentFlags(mutable = false)
        )

    private fun inboxIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            MainActivity.intentForChat(context, ""),
            pendingIntentFlags(mutable = false)
        )

    private fun replyAction(context: Context, chatId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.notif_action_reply))
            .build()
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_REPLY)
            .putExtra(EXTRA_CHAT_ID, chatId)
        // RemoteInput results are written INTO the delivered intent, which requires a
        // MUTABLE PendingIntent on Android 12+ — an immutable one silently drops the
        // typed reply and the whole action looks broken.
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(chatId) + 1,
            intent,
            pendingIntentFlags(mutable = true)
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_message,
            context.getString(R.string.notif_action_reply),
            pending
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(context: Context, chatId: String): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_MARK_READ)
            .putExtra(EXTRA_CHAT_ID, chatId)
        val pending = PendingIntent.getBroadcast(
            context,
            notificationId(chatId) + 2,
            intent,
            pendingIntentFlags(mutable = false)
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_message,
            context.getString(R.string.notif_action_mark_read),
            pending
        ).build()
    }

    private fun pendingIntentFlags(mutable: Boolean): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (mutable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }
}
