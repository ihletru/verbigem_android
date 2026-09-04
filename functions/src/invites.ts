import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { logger } from "firebase-functions";
import { directoryId, isSha256Hex, normaliseE164, sha256Hex } from "./phoneHash";
import { lookupDirectory, lookupProfiles } from "./directory";
import { enforceRateLimit } from "./rateLimit";
import { phonePepper } from "./secrets";

const db = admin.firestore();

/** A large address book is a few hundred numbers; 1000 leaves headroom. */
const MAX_HASHES = 1000;

/** Firestore batches cap out at 500 operations. */
const BATCH_LIMIT = 500;

/** Taps, not automation — but 60/hour still stops a loop. */
const INVITE_RATE_CALLS = 60;
const INVITE_RATE_WINDOW_MS = 60 * 60 * 1000;

/**
 * An invitation stops being meaningful eventually: the number may have changed hands,
 * and a friend request appearing out of nowhere a year later is worse than none.
 */
const INVITE_MAX_AGE_MS = 90 * 24 * 60 * 60 * 1000;

const SHA_CLAIM_MISSING =
  "No verified phone number on this account yet. Finish phone verification first.";

// ══════════════════════════════════════════════════════════════════════════════
// verifyPhone — the only place we learn a real phone number
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Records that the signed-in user owns a verified phone number.
 *
 * THE NUMBER COMES FROM FIREBASE AUTH, NOT FROM THE CLIENT.
 *
 * That is the whole design. The app proves ownership with Firebase Phone Auth
 * (`linkWithCredential`), Firebase puts the E.164 number in the ID token, and this
 * function reads `request.auth.token.phone_number` and hashes it. There is no
 * `phoneNumber` argument to forge, and nothing stops a client from lying because a
 * client is never asked.
 *
 * The client MUST call `getIdToken(true)` after linking — the token it already holds
 * was minted before the phone number existed and still claims there is none.
 *
 * We store `phoneHash` (SHA-256 of E.164) and a boolean, never the number. The
 * `phoneDirectory` entry that makes the user findable is written by the
 * `onPhoneVerified` trigger below, so any path that flips those two fields — this
 * function, a future admin tool, a manual console edit — stays consistent.
 */
export const verifyPhone = onCall(
  {
    enforceAppCheck: false, // see the long comment in contacts.ts
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before verifying a number.");
    }
    const me = request.auth.uid;

    const e164 = normaliseE164(request.auth.token.phone_number);
    if (!e164) {
      // Usually a missing token refresh rather than a missing verification, but we
      // cannot tell them apart here and the client's answer to both is the same.
      throw new HttpsError("failed-precondition", SHA_CLAIM_MISSING);
    }

    const hash = sha256Hex(e164);
    const ref = db.doc(`users/${me}`);
    const snap = await ref.get();

    if (snap.get("phoneVerified") === true && snap.get("phoneHash") === hash) {
      return { verified: true, changed: false };
    }

    // `phoneHash` stays server-only: the security rules put it on the write
    // blacklist for `users/{uid}`, so a client cannot quietly make itself
    // discoverable under a number it never proved it owns.
    await ref.set(
      {
        phoneVerified: true,
        phoneHash: hash,
        phoneVerifiedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    logger.info("phone verified", { uid: me });
    return { verified: true, changed: true };
  }
);

// ══════════════════════════════════════════════════════════════════════════════
// inviteByPhone — "this person is not on Verbigem yet, tell them when they are"
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Leaves an invitation against a phone number we have never seen.
 *
 * The client sends the same `SHA-256(E.164)` hashes it sends to `matchContacts`. If
 * the number turns out to be in `phoneDirectory` after all (the UI was simply
 * stale), we skip the invite and create the friend request right now — otherwise the
 * invitation would sit there forever, because `onPhoneVerified` only runs when a
 * number *changes*.
 *
 * Returns one result per input index so the app can redraw the row it tapped.
 */
export const inviteByPhone = onCall(
  {
    enforceAppCheck: false, // see the long comment in contacts.ts
    secrets: [phonePepper],
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before inviting contacts.");
    }
    const me = request.auth.uid;

    await enforceRateLimit(me, "inviteByPhone", INVITE_RATE_CALLS, INVITE_RATE_WINDOW_MS);

    const raw = (request.data as { hashes?: unknown } | undefined)?.hashes;
    if (!Array.isArray(raw)) {
      throw new HttpsError("invalid-argument", "hashes must be an array of strings.");
    }
    if (raw.length > MAX_HASHES) {
      throw new HttpsError("invalid-argument", `At most ${MAX_HASHES} hashes per call.`);
    }

    const hashes = raw.filter(isSha256Hex);
    if (hashes.length === 0) return { results: [] };

    const pepper = process.env.PHONE_HASH_PEPPER;
    if (!pepper) {
      logger.error("PHONE_HASH_PEPPER is not configured");
      throw new HttpsError("failed-precondition", "Invitations are not configured.");
    }

    const hmacs = [...new Set(hashes.map((h) => directoryId(h, pepper)))];
    const uidByHmac = await lookupDirectory(hmacs);

    // An existing invitation is left alone — refreshing `createdAt` on every tap
    // would keep it alive forever and defeat the 90-day expiry below.
    const alreadyInvited = await existingInviteIds(hmacs, me);

    const invites: { id: string; hmac: string }[] = [];
    const results: { index: number; status: string; uid?: string }[] = [];
    const pendingFriendships: string[] = [];

    hashes.forEach((hash, index) => {
      const mac = directoryId(hash, pepper);
      const uid = uidByHmac.get(mac);

      // Already a user: connect them now instead of filing an invitation that no
      // trigger will ever pick up.
      if (uid && uid !== me) {
        pendingFriendships.push(uid);
        results.push({ index, status: "friend_requested", uid });
        return;
      }
      if (uid === me) {
        results.push({ index, status: "self" });
        return;
      }
      const id = `${mac}_${me}`;
      if (alreadyInvited.has(id)) {
        results.push({ index, status: "invited" });
        return;
      }
      invites.push({ id, hmac: mac });
      results.push({ index, status: "invited" });
    });

    const myProfile = (await lookupProfiles([me])).get(me);
    const fromName = myProfile?.nickname ?? "";

    let batch = db.batch();
    let queued = 0;
    for (const invite of invites) {
      batch.set(db.doc(`invites/${invite.id}`), {
        hmac: invite.hmac,
        fromUid: me,
        fromName,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      if (++queued === BATCH_LIMIT) {
        await batch.commit();
        batch = db.batch();
        queued = 0;
      }
    }
    if (queued > 0) await batch.commit();

    // Sequential on purpose: each one is a transaction, and a friend request is a
    // tap-driven event measured in single digits.
    for (const uid of [...new Set(pendingFriendships)]) {
      await requestFriendship(me, uid);
    }

    return { results };
  }
);

// ══════════════════════════════════════════════════════════════════════════════
// onPhoneVerified — keeps the directory in step and resolves waiting invitations
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Fires on every write to `users/{uid}`, so the FIRST thing it does is bail out when
 * nothing phone-related moved. Profile edits, history syncs and settings changes all
 * flow through this document; without the early exit we would be doing a Firestore
 * read on all of them.
 *
 * What it maintains:
 *   • `phoneDirectory/{hmac}` — add the new hash, remove the old one when the number
 *     changed. Leaving the old entry behind would let someone who no longer owns the
 *     number keep receiving matches for it.
 *   • `invites/{hmac}_{fromUid}` — everyone who tried to invite this number before it
 *     existed now gets a real friend request, and the invitation is consumed.
 *
 * Deliberately a trigger and not part of `verifyPhone`: the directory is DERIVED
 * data, and derived data should follow the source of truth rather than whichever
 * caller remembered to update it.
 */
export const onPhoneVerified = onDocumentWritten(
  {
    document: "users/{uid}",
    secrets: [phonePepper],
    maxInstances: 10,
  },
  async (event) => {
    const change = event.data;
    if (!change) return;

    const before = change.before;
    const after = change.after;

    const beforeHash = readHash(before);
    const afterHash = readHash(after);
    const verified = after.get("phoneVerified") === true;

    // Cheap exit: nothing about the phone number moved.
    if (beforeHash === afterHash) return;

    const uid = event.params.uid;
    const pepper = process.env.PHONE_HASH_PEPPER;
    if (!pepper) {
      logger.error("PHONE_HASH_PEPPER is not configured");
      return;
    }

    const afterId = afterHash ? directoryId(afterHash, pepper) : null;
    const beforeId = beforeHash ? directoryId(beforeHash, pepper) : null;

    if (beforeId && beforeId !== afterId) {
      await db.doc(`phoneDirectory/${beforeId}`).delete();
      logger.info("phone directory entry removed", { uid });
    }

    if (!afterId || !verified) return;

    await db.doc(`phoneDirectory/${afterId}`).set({
      uid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    logger.info("phone directory entry written", { uid });

    await resolveInvites(uid, afterId);
  }
);

/**
 * Turns every waiting invitation for `hashId` into a friend request.
 *
 * One failure must not stop the rest — Promise.allSettled, and log what broke.
 * The invitation is deleted only after the friendship exists, so a crash in between
 * leaves it to be retried rather than silently dropped.
 */
async function resolveInvites(uid: string, hashId: string): Promise<void> {
  const snap = await db.collection("invites").where("hmac", "==", hashId).get();
  if (snap.empty) return;

  const now = Date.now();

  const settled = await Promise.allSettled(
    snap.docs.map(async (doc) => {
      const fromUid = doc.get("fromUid");
      const createdAt = doc.get("createdAt");

      const stale =
        createdAt && typeof createdAt.toMillis === "function"
          ? now - createdAt.toMillis() > INVITE_MAX_AGE_MS
          : false;

      if (stale || typeof fromUid !== "string" || !fromUid || fromUid === uid) {
        await doc.ref.delete();
        return;
      }

      await requestFriendship(fromUid, uid);
      await doc.ref.delete();
      logger.info("invitation resolved into a friend request", {
        fromUid,
        toUid: uid,
      });
    })
  );

  const failed = settled.filter((r) => r.status === "rejected");
  if (failed.length > 0) {
    logger.error("some invitations could not be resolved", {
      uid,
      failed: failed.length,
      total: settled.length,
    });
  }
}

/**
 * Creates a pending `Friendship` exactly the way the app's `ChatRepository` does —
 * same document id, same field names, same ordering — so both devices read the same
 * document with no special-casing for "server created this one".
 *
 * `uidA` is the lexicographically smaller uid, which is what makes the id identical
 * on both sides. A transaction guards against two invitations racing: whoever gets
 * there second finds an existing document and leaves it alone, which also means a
 * declined invitation is never resurrected.
 */
async function requestFriendship(fromUid: string, toUid: string): Promise<boolean> {
  const sorted = [fromUid, toUid].sort();
  const [uidA, uidB] = sorted;
  const id = `${uidA}__${uidB}`;
  const ref = db.doc(`friendships/${id}`);

  const created = await db.runTransaction(async (tx) => {
    const existing = await tx.get(ref);
    if (existing.exists) return false;

    const profiles = await lookupProfiles([uidA, uidB]);
    tx.set(ref, {
      uidA,
      uidB,
      members: [uidA, uidB],
      status: "pending",
      requestedBy: fromUid,
      nicknameA: profiles.get(uidA)?.nickname ?? "",
      nicknameB: profiles.get(uidB)?.nickname ?? "",
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    return true;
  });

  if (!created) {
    logger.info("friendship already exists, invitation ignored", { id });
  }
  return created;
}

/** Which invitations this user has already filed, so we do not reset their age. */
async function existingInviteIds(hmacs: string[], me: string): Promise<Set<string>> {
  const ids = hmacs.map((mac) => `${mac}_${me}`);
  const found = new Set<string>();

  for (let i = 0; i < ids.length; i += 30) {
    const chunk = ids.slice(i, i + 30);
    const snap = await db
      .collection("invites")
      .where(admin.firestore.FieldPath.documentId(), "in", chunk)
      .get();
    for (const doc of snap.docs) found.add(doc.id);
  }
  return found;
}

function readHash(snap: admin.firestore.DocumentSnapshot): string | null {
  const value = snap.get("phoneHash");
  return typeof value === "string" && isSha256Hex(value) ? value : null;
}
