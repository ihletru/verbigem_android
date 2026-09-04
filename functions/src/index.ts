import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions/v2";

admin.initializeApp();

// Default region (us-central1) on purpose: the Firestore triggers have to run
// somewhere, and picking a region the project has no resources in only adds
// latency. Raise it only if the logs ever show this being a bottleneck.
setGlobalOptions({ maxInstances: 20 });

export { onMessageCreated } from "./messaging";

// Contact matching (tasks 2.3 / 2.4) is written but NOT exported yet, and that is
// deliberate: it declares the PHONE_HASH_PEPPER secret, and Firebase validates that
// every secret exists at deploy time. While this line is commented out, `matchContacts`
// is simply invisible to the deploy; uncommenting it before the secret is set makes
// EVERY deploy fail — including the push one, which has nothing to do with it.
//
// To ship it (task 2.3):
//   1. firebase functions:secrets:set PHONE_HASH_PEPPER --project mini-verbigem
//   2. uncomment the line below
//   3. firebase deploy --only functions --project mini-verbigem
//
// Rotating the pepper later invalidates every hash already in `phoneDirectory`, so it
// must only be set once, before the directory is populated.
// export { matchContacts } from "./contacts";
