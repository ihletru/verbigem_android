import * as admin from "firebase-admin";

const db = admin.firestore();

/** Firestore caps `in` queries at 30 values — larger arrays are rejected outright. */
const FIRESTORE_IN_LIMIT = 30;

/**
 * Looks up `phoneDirectory/{hmac}` in chunks and returns the uid per hmac.
 *
 * `phoneDirectory` is deliberately bare: `{ uid }` and nothing else. Even with the
 * pepper compromised, a uid is the most an attacker can get out of it — there is no
 * number, no name, no timestamp of when they joined.
 */
export async function lookupDirectory(hmacs: string[]): Promise<Map<string, string>> {
  const out = new Map<string, string>();
  if (hmacs.length === 0) return out;

  for (let i = 0; i < hmacs.length; i += FIRESTORE_IN_LIMIT) {
    const chunk = hmacs.slice(i, i + FIRESTORE_IN_LIMIT);
    const snap = await db
      .collection("phoneDirectory")
      .where(admin.firestore.FieldPath.documentId(), "in", chunk)
      .get();

    for (const doc of snap.docs) {
      const uid = doc.get("uid");
      if (typeof uid === "string" && uid) out.set(doc.id, uid);
    }
  }
  return out;
}

/** Public profile fields we are willing to hand out. Never email — see below. */
export interface PublicProfile {
  nickname: string;
  photoURL: string;
}

/**
 * Reads `usersPublic/{uid}` in chunks.
 *
 * Only `nickname` and `photoURL` come back. `usersPublic` also carries `email`
 * (that is how nick/e-mail search works at all), and an invite flow has no reason to
 * hand one stranger's address to another.
 */
export async function lookupProfiles(uids: string[]): Promise<Map<string, PublicProfile>> {
  const out = new Map<string, PublicProfile>();
  if (uids.length === 0) return out;

  for (let i = 0; i < uids.length; i += FIRESTORE_IN_LIMIT) {
    const chunk = uids.slice(i, i + FIRESTORE_IN_LIMIT);
    const snap = await db
      .collection("usersPublic")
      .where(admin.firestore.FieldPath.documentId(), "in", chunk)
      .get();

    for (const doc of snap.docs) {
      out.set(doc.id, {
        nickname: (doc.get("nickname") as string | undefined) ?? "",
        photoURL: (doc.get("photoURL") as string | undefined) ?? "",
      });
    }
  }
  return out;
}
