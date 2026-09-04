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
