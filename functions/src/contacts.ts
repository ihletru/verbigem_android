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
 * Answers "which of these phone numbers already use Verbigem?" without ever
 * learning the numbers.
 *
 * The client sends `SHA-256(E.164)` of each contact. This function turns each
 * hash into `HMAC-SHA256(hash, pepper)` and looks that up in `phoneDirectory`,
 * which the client cannot read at all. The pepper lives in Secret Manager, so a
 * leak of the database alone does not allow a rainbow-table attack.
 *
 * NOT wired into the app yet — see `index.ts`. Task 3.4 needs it, and it needs
 * App Check turned on before it ships.
 */
export const matchContacts = onCall(
  {
    // TODO: flip to `true` before task 3.4 ships. It needs Play Integrity wired
    // up in the app first, otherwise every real device would be rejected.
    enforceAppCheck: false,
    secrets: [phonePepper],
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before matching contacts.");
    }
    const me = request.auth.uid;

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
