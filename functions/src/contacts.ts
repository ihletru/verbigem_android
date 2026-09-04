import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { directoryId, isSha256Hex } from "./phoneHash";
import { enforceRateLimit } from "./rateLimit";
import { phonePepper } from "./secrets";
import { lookupDirectory, lookupProfiles } from "./directory";

/** A large address book is a few hundred numbers; 1000 leaves headroom. */
const MAX_HASHES = 1000;

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

    await enforceRateLimit(me, "matchContacts", RATE_LIMIT_CALLS, RATE_LIMIT_WINDOW_MS);

    const raw = (request.data as { hashes?: unknown } | undefined)?.hashes;
    if (!Array.isArray(raw)) {
      throw new HttpsError("invalid-argument", "hashes must be an array of strings.");
    }
    if (raw.length > MAX_HASHES) {
      throw new HttpsError("invalid-argument", `At most ${MAX_HASHES} hashes per call.`);
    }

    // Anything that is not a well-formed SHA-256 hex string is either a client bug
    // or someone probing with raw phone numbers. Drop it rather than echo it back.
    const hashes = raw.filter(isSha256Hex);
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
      const mac = directoryId(hash, pepper);
      if (!indexByHmac.has(mac)) indexByHmac.set(mac, index);
    });

    const uidByHmac = await lookupDirectory([...indexByHmac.keys()]);
    const uidByIndex = new Map<number, string>();
    for (const [mac, index] of indexByHmac) {
      const uid = uidByHmac.get(mac);
      // Never match the caller with themselves.
      if (uid && uid !== me) uidByIndex.set(index, uid);
    }

    const profiles = await lookupProfiles([...new Set(uidByIndex.values())]);

    const matches = [...uidByIndex.entries()].map(([index, uid]) => ({
      index,
      uid,
      nickname: profiles.get(uid)?.nickname ?? "",
      photoURL: profiles.get(uid)?.photoURL ?? "",
    }));

    return { matches };
  }
);
