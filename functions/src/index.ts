import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2";

admin.initializeApp();

// Default region (us-central1) on purpose: the Firestore triggers have to run
// somewhere, and picking a region the project has no resources in only adds
// latency. Raise it only if the logs ever show this being a bottleneck.
setGlobalOptions({ maxInstances: 20 });

export { onMessageCreated } from "./messaging";

// Contact matching (task 2.3). Enabled 2026-09-04 once PHONE_HASH_PEPPER existed in
// Secret Manager.
//
// ⚠️ Rotating that pepper invalidates every hash already in `phoneDirectory` — the
// whole directory would have to be recomputed. It is set once, before the directory
// is populated by phone verification (2.6).
//
// ⚠️ This function declares a secret, and Firebase validates ALL secrets at deploy
// time. If you ever comment this line back out, do it to unblock an unrelated deploy,
// not because the secret went missing.
export { matchContacts } from "./contacts";

// Phone verification and invitations (task 2.4/2.6), 2026-09-04.
//
// `verifyPhone` proves the account owns a number WITHOUT the client ever sending one:
// Firebase Phone Auth puts the E.164 number in the ID token and the function hashes
// what it finds there. `onPhoneVerified` then derives `phoneDirectory` from the user
// document, so the directory follows the source of truth instead of one caller.
// `inviteByPhone` needs the pepper like `matchContacts` does — see `secrets.ts` for
// why both import the same declaration instead of each calling `defineSecret`.
export { verifyPhone, inviteByPhone, onPhoneVerified } from "./invites";

// Search indexing (task 1.12), 2026-09-04. Reacts to the same document as
// `onMessageCreated` on purpose — see the comment in `searchIndex.ts` for why they
// are two functions and not one.
export { onMessageSearchIndex } from "./searchIndex";
