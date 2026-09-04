import * as admin from "firebase-admin";
import { logger } from "firebase-functions";
import { HttpsError } from "firebase-functions/v2/https";

const db = admin.firestore();

/**
 * Sliding-window counter kept in `users/{uid}/rateLimits/{name}`.
 *
 * A transaction rather than a plain `increment`: two concurrent calls would both read
 * the same count and both let themselves through. The document is writable by its
 * owner under the current rules, which is fine — the worst a client can do is lock
 * itself out for an hour.
 *
 * Rejected calls do NOT advance the window, otherwise a client hammering the
 * endpoint would push its own window forward forever and stay permanently blocked.
 */
export async function enforceRateLimit(
  uid: string,
  name: string,
  maxCalls: number,
  windowMs: number
): Promise<void> {
  const ref = db.doc(`users/${uid}/rateLimits/${name}`);
  const now = Date.now();

  const allowed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const windowStart = snap.get("windowStart");
    const count = snap.get("count");

    const fresh =
      !snap.exists ||
      typeof windowStart !== "number" ||
      now - windowStart >= windowMs ||
      typeof count !== "number";

    if (!fresh && count >= maxCalls) return false;

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
    logger.warn("rate limit hit", { uid, name });
    throw new HttpsError(
      "resource-exhausted",
      "Too many requests. Try again later."
    );
  }
}
