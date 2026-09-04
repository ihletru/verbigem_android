import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";

/**
 * Firestore documents are capped at 1 MiB and no real message comes close, but an
 * index field is not a place to store prose — a pasted wall of text would make the
 * document measurably more expensive to read for no gain.
 */
const MAX_INDEXED_CHARS = 2000;

/**
 * Turns a message into the string search matches against.
 *
 * THE CLIENT MUST APPLY THE EXACT SAME TRANSFORMATION before querying — see
 * `MessageSearch.normalize` in the app. If the two ever drift, every search silently
 * returns nothing, and nothing in the logs will say why.
 *
 * Two steps, both deliberate:
 *
 * 1. **NFD, then strip combining marks.** Polish users type "jestes" as often as
 *    "jesteś"; without this, one of the two spellings never matches. This has to be
 *    NFD (decompose) and not NFC, and `\p{M}` is the Unicode *Mark* category — the
 *    combining accents left behind by decomposition.
 * 2. **Lowercase.** Firestore string comparisons are case-sensitive, so "Kot" and
 *    "kot" are different values.
 *
 * Exported (not just module-private) so `backfill_searchtext.js` can reuse the exact
 * same code instead of keeping a second copy that would drift silently.
 */
export function normalizeForSearch(raw: string): string {
  return raw
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .trim()
    .slice(0, MAX_INDEXED_CHARS);
}

/**
 * Writes `searchText` onto every new message so the app can search across
 * conversations (task 1.12).
 *
 * Why a trigger instead of letting the client write the field itself: this is
 * derived data, and derived data belongs to whoever can be trusted to keep it in
 * step — the same reason `phoneDirectory` is maintained by `onPhoneVerified`. The
 * security rules forbid the client from touching it at all.
 *
 * Why a SEPARATE function from `onMessageCreated`, which already reacts to this very
 * document: pushing a notification and indexing a message have nothing to do with
 * each other, and sharing a handler would mean a broken push endpoint also stops
 * search — or vice versa.
 *
 * There is no infinite loop: this is `onDocumentCreated`, and the write below is an
 * update, which does not re-trigger it.
 */
export const onMessageSearchIndex = onDocumentCreated(
  {
    document: "chats/{chatId}/messages/{msgId}",
    maxInstances: 10,
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    // Media messages (phase 5) carry an attachment, not `text`. They get indexed
    // when they get a transcript worth indexing.
    const text = snap.get("text");
    if (typeof text !== "string") return;

    const searchText = normalizeForSearch(text);
    if (!searchText) return;

    await snap.ref.set({ searchText }, { merge: true });
    logger.debug("message indexed", { msgId: snap.id });
  }
);
