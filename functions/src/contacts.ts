import * as admin from "firebase-admin";
import { defineSecret } from "firebase-functions/params";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import * as crypto from "crypto";

const db = admin.firestore();
const phonePepper = defineSecret("PHONE_HASH_PEPPER");

/** Firestore caps `in` queries at 30 values — larger arrays are rejected outright. */
const FIRESTORE_IN_LIMIT = 30;

/** A large address book is a few hundred numbers; 1000 leaves headroom. */
const MAX_HASHES = 1000;

const SHA256_HEX = /^[0-9a-f]{64}$/;

/**
 * Rate limit per user.
 *
 * This function is expensive by design: 1000 hashes is 34 `in` queries against
 * `phoneDirectory` plus the profile lookups. It is also the obvious way to try to
 * enumerate a phone number — send a hash, see whether it matches — so it needs a
 * ceiling even for a well-behaved client.
 *
 * One call per address book is all a real app needs; 20/hour is generous.
 */
const RATE_LIMIT_CALLS = 20;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;

/**
 * Answers "which of these phone numbers already use Verbigem?" without ever
 * learning the numbers.
 *
 * The client sends `SHA-256(E.164)` of each contact. This function turns each
 * hash into `HMAC-SHA256(hash, pepper)` and looks that up in `phoneDirectory`,
 * which the client cannot read at all. The pepper lives in Secret Manager, so a
 * leak of the database alone does not allow a rainbow-table attack.
 *
 * Wired into the app from task 2.3 on — see `index.ts`.
 */
export const matchContacts = onCall(
  {
    // ENFORCEMENT IS OFF, AND THAT IS NOT OVERSIGHT.
    //
    // App Check is initialised in the app (`AppCheckProvider`), but the APK we
    // currently ship through auto-update is a DEBUG build, and Play Integrity does
    // not vouch for apps the Play Store did not install. Turning this on today would
    // reject every real user, not just abusers.
    //
    // Flip to `true` together with the first Play Store release, and only after the
    // app's SHA-256 signing certificate is registered in
    // Firebase Console → App Check → Apps. Until then the attestation is still sent
    // and visible in the console's App Check metrics — it just is not required.
    enforceAppCheck: false,
    secrets: [phonePepper],
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before matching contacts.");
    }
    const me = request.auth.uid;

    await enforceRateLimit(me);

    const raw = (request.data as { hashes?: unknown } | undefined)?.hashes;
    if (!Array.isArray(raw)) {
      throw new HttpsError("invalid-argument", "hashes must be an array of strings.");
    }
    if (raw.length > MAX_HASHES) {
      throw new HttpsError("invalid-argument", `At most ${MAX_HASHES} hashes per call.`);
    }

    // Anything that is not a well-formed SHA-256 hex string is either a client bug
    // or someone probing with raw phone numbers. Drop it rather than echo it back.
    const hashes = raw.filter(
      (h): h is string => typeof h === "string" && SHA256_HEX.test(h)
    );
    if (hashes.length === 0) return { matches: [] };

    const pepper = process.env.PHONE_HASH_PEPPER;
    if (!pepper) {
      logger.error("PHONE_HASH_PEPPER is not configured");
      throw new HttpsError("failed-precondition", "Contact matching is not configured.");
    }

    // Map HMAC -> the caller's original index, so the app can line results up with
    // the contacts it sent without us ever returning a hash.
    const indexByHmac = new Map<string, number>();
    hashes.forEach((hash, index) => {
      const mac = crypto.createHmac("sha256", pepper).update(hash).digest("hex");
      if (!indexByHmac.has(mac)) indexByHmac.set(mac, index);
    });

    const hmacs = [...indexByHmac.keys()];
    const uidByIndex = new Map<number, string>();

    for (let i = 0; i < hmacs.length; i += FIRESTORE_IN_LIMIT) {
      const chunk = hmacs.slice(i, i + FIRESTORE_IN_LIMIT);
      const snap = await db
        .collection("phoneDirectory")
        .where(admin.firestore.FieldPath.documentId(), "in", chunk)
        .get();

      for (const doc of snap.docs) {
        const index = indexByHmac.get(doc.id);
        if (index === undefined) continue;
        const uid = doc.get("uid");
        // Never match the caller with themselves.
        if (typeof uid !== "string" || uid === me) continue;
        uidByIndex.set(index, uid);
      }
    }

    const uids = [...new Set(uidByIndex.values())];
    const profiles = new Map<string, { nickname: string; photoURL: string }>();

    for (let i = 0; i < uids.length; i += FIRESTORE_IN_LIMIT) {
      const chunk = uids.slice(i, i + FIRESTORE_IN_LIMIT);
      const snap = await db
        .collection("usersPublic")
        .where(admin.firestore.FieldPath.documentId(), "in", chunk)
        .get();

      for (const doc of snap.docs) {
        profiles.set(doc.id, {
          nickname: (doc.get("nickname") as string | undefined) ?? "",
          photoURL: (doc.get("photoURL") as string | undefined) ?? "",
        });
      }
    }

    const matches = [...uidByIndex.entries()].map(([index, uid]) => ({
      index,
      uid,
      nickname: profiles.get(uid)?.nickname ?? "",
      photoURL: profiles.get(uid)?.photoURL ?? "",
    }));

    return { matches };
  }
);

/**
 * Sliding-window counter kept in `users/{uid}/rateLimits/matchContacts`.
 *
 * A transaction rather than a plain `increment`: two concurrent calls would both
 * read the same count and both let themselves through. The document is writable by
 * the owner under the current rules, which is fine — the worst a client can do is
 * lock itself out for an hour.
 */
async function enforceRateLimit(uid: string): Promise<void> {
  const ref = db.doc(`users/${uid}/rateLimits/matchContacts`);
  const now = Date.now();

  const allowed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const windowStart = snap.get("windowStart");
    const count = snap.get("count");

    const fresh =
      !snap.exists ||
      typeof windowStart !== "number" ||
      now - windowStart >= RATE_LIMIT_WINDOW_MS ||
      typeof count !== "number";

    // Keep the call that gets rejected out of the counter, otherwise a client
    // hammering the endpoint would push its own window forward forever.
    if (!fresh && count >= RATE_LIMIT_CALLS) return false;

    tx.set(
      ref,
      fresh
        ? { windowStart: now, count: 1, updatedAt: now }
        : { count: count + 1, updatedAt: now },
      { merge: true }
    );
    return true;
  });

  if (!allowed) {
    logger.warn("matchContacts rate limited", { uid });
    throw new HttpsError(
      "resource-exhausted",
      "Too many contact lookups. Try again later."
    );
  }
}
