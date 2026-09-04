import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

const db = admin.firestore();

/**
 * The Android notification channel (`verbigem_messages`) is created by the app in
 * `VerbigemNotifications.ensureChannel()`. The device takes sound and vibration from
 * that channel — the payload below deliberately says nothing about either, because a
 * data-only message has no `notification` block to put them in.
 */

/** Lock-screen previews are capped so a long message cannot flood the shade. */
const PREVIEW_MAX_CHARS = 120;

/**
 * Tokens that will never work again. FCM tells us the token is dead, so keeping
 * it would mean a failing send on every single message forever — delete it.
 */
const STALE_TOKEN_CODES = new Set([
  "messaging/registration-token-not-registered",
  "messaging/invalid-registration-token",
]);

/** "New message" in all six UI languages — the cloud must localise, not the device. */
const NEW_MESSAGE_LABELS: Record<string, string> = {
  pl: "Nowa wiadomość",
  en: "New message",
  de: "Neue Nachricht",
  es: "Mensaje nuevo",
  zh: "新消息",
  tr: "Yeni mesaj",
};

interface SenderHint {
  lang?: string;
  text?: string;
}

/**
 * Pushes a notification to every other member of a chat when a message is written.
 *
 * Three things here are deliberate and easy to get wrong:
 *
 * 1. **The preview text comes from `senderTranslation`, not from `text`.** The
 *    sender's hint was produced *for this recipient's language* (decision D1), so
 *    it is the one version of the message they can actually read. The raw `text`
 *    is in the sender's language and would be nonsense on the lock screen.
 * 2. **Previews are OFF unless `app_config/notifications.showMessagePreview` is
 *    true.** Push payloads leave the device and pass through Google's servers,
 *    which is a different privacy story from "translation happens on your phone".
 *    The switch lives in Firestore so it can be flipped without a new APK.
 * 3. **The message is data-only — there is no `notification` payload.** With a
 *    notification payload Android renders the notification itself while the app is
 *    backgrounded and never calls `onMessageReceived`, so the reply / mark-as-read
 *    actions would only exist for messages that land while the app is open. Sending
 *    data-only hands rendering to `VerbigemMessagingService` in every case, at the
 *    cost of delivery being subject to Doze — hence `priority: "high"` below.
 */
export const onMessageCreated = onDocumentCreated(
  "chats/{chatId}/messages/{msgId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const chatId = event.params.chatId;
    const msgId = event.params.msgId;
    const msg = snap.data();

    const authorId = msg.authorId;
    if (typeof authorId !== "string" || authorId.length === 0) {
      logger.warn("Message without authorId", { chatId, msgId });
      return;
    }

    // Members come from the chat document, not from the message: the message is
    // append-only and carries no roster.
    const chatSnap = await db.doc(`chats/${chatId}`).get();
    const members = chatSnap.get("members");
    if (!Array.isArray(members)) return;

    const recipients = members.filter(
      (uid): uid is string => typeof uid === "string" && uid !== authorId
    );
    if (recipients.length === 0) return;

    // One read shared by everyone in the thread.
    const showPreview = await messagePreviewEnabled();

    const results = await Promise.allSettled(
      recipients.map((uid) =>
        notifyRecipient({ recipientUid: uid, authorId, chatId, msgId, msg, showPreview })
      )
    );
    results.forEach((r, i) => {
      if (r.status === "rejected") {
        // A failed push must never fail the trigger — the message is already
        // stored, so retrying would just re-notify everyone.
        logger.warn("Push failed", { chatId, recipient: recipients[i], error: r.reason });
      }
    });
  }
);

interface NotifyArgs {
  recipientUid: string;
  authorId: string;
  chatId: string;
  msgId: string;
  msg: admin.firestore.DocumentData;
  showPreview: boolean;
}

async function notifyRecipient(args: NotifyArgs): Promise<void> {
  const { recipientUid, authorId, chatId, msgId, msg, showPreview } = args;

  // "Mute" is stored per contact on MY side, so it is honoured here rather than
  // filtered on the device — a muted chat should not even be woken up.
  const contactSnap = await db.doc(`users/${recipientUid}/contacts/${authorId}`).get();
  if (contactSnap.get("muted") === true) return;

  const tokenSnap = await db.collection(`users/${recipientUid}/fcmTokens`).get();
  if (tokenSnap.empty) return;
  const tokens = tokenSnap.docs.map((doc) => doc.id).filter((t) => t.length > 0);
  if (tokens.length === 0) return;

  const [title, body] = await Promise.all([
    senderNickname(authorId),
    buildBody(msg, recipientUid, showPreview),
  ]);

  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    // Everything travels in `data` — see point 3 of the comment above. `title` and
    // `body` are already localised here (the cloud knows the recipient's uiLang),
    // so the device never has to guess.
    data: { type: "chat_message", chatId, authorId, msgId, title, body },
    android: {
      // High priority is the only lever we have against Doze for a data-only message.
      priority: "high",
      // 4 weeks: if the device comes back online after that, a "you have 37 new
      // messages" buzz is noise, not signal. The thread itself is always in Firestore.
      ttl: 4 * 7 * 24 * 60 * 60 * 1000,
    },
  });

  const stale = response.responses
    .map((r, i) => ({ ok: r.success, code: r.error?.code, token: tokens[i] }))
    .filter((r) => !r.ok && r.code !== undefined && STALE_TOKEN_CODES.has(r.code));

  if (stale.length > 0) {
    logger.info("Removing stale FCM tokens", { recipientUid, count: stale.length });
    await Promise.all(
      stale.map((s) =>
        db.doc(`users/${recipientUid}/fcmTokens/${s.token}`).delete().catch((e) => {
          logger.warn("Could not delete stale token", { error: e });
        })
      )
    );
  }
}

/** Falls back to the app name — a notification titled "null" looks broken. */
async function senderNickname(authorId: string): Promise<string> {
  try {
    const snap = await db.doc(`usersPublic/${authorId}`).get();
    const nickname = snap.get("nickname");
    if (typeof nickname === "string" && nickname.trim().length > 0) {
      return nickname.trim();
    }
  } catch (e) {
    logger.warn("Could not read sender nickname", { authorId, error: e });
  }
  return "Verbigem";
}

/**
 * Picks the text for the lock screen.
 *
 * The recipient's own language is read from `usersPublic`, which is the same
 * document the app uses to decide its own translation target — so the preview and
 * the in-app translation agree.
 */
async function buildBody(
  msg: admin.firestore.DocumentData,
  recipientUid: string,
  showPreview: boolean
): Promise<string> {
  let uiLang = "en";
  let speakLang = "";
  try {
    const snap = await db.doc(`usersPublic/${recipientUid}`).get();
    uiLang = (snap.get("uiLang") as string | undefined) ?? "en";
    speakLang = (snap.get("speakLangSource") as string | undefined) ?? "";
  } catch (e) {
    logger.warn("Could not read recipient profile", { recipientUid, error: e });
  }

  const generic = NEW_MESSAGE_LABELS[uiLang] ?? NEW_MESSAGE_LABELS.en;
  if (!showPreview) return generic;

  const hint = msg.senderTranslation as SenderHint | undefined;
  if (hint?.text && hint.lang && hint.lang === speakLang) {
    return truncate(hint.text, PREVIEW_MAX_CHARS);
  }
  const text = msg.text;
  if (typeof text === "string" && text.trim().length > 0) {
    return truncate(text.trim(), PREVIEW_MAX_CHARS);
  }
  return generic;
}

/**
 * Whether lock-screen previews are on, read from `app_config/notifications`.
 *
 * Default is FALSE: a privacy-relevant default should fail closed, and the
 * document is operator-controlled so it can be turned on with no new release.
 */
async function messagePreviewEnabled(): Promise<boolean> {
  try {
    const snap = await db.doc("app_config/notifications").get();
    return snap.get("showMessagePreview") === true;
  } catch (e) {
    logger.warn(
      "app_config/notifications unreadable; assuming previews are off",
      { error: e }
    );
    return false;
  }
}

function truncate(value: string, max: number): string {
  return value.length <= max ? value : `${value.slice(0, max - 1)}…`;
}
