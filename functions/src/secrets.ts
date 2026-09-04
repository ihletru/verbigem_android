import { defineSecret } from "firebase-functions/params";

/**
 * Every secret in the project, declared exactly ONCE.
 *
 * `defineSecret` registers the parameter against the module that calls it, so two
 * modules each calling `defineSecret("PHONE_HASH_PEPPER")` create two parameters
 * with one name and collide at deploy time. Both `contacts.ts` and `invites.ts` need
 * the pepper, hence this file.
 *
 * ⚠️ Rotating the pepper invalidates every hash already in `phoneDirectory` — the
 * whole directory would have to be recomputed from scratch. It is set once, before
 * phone verification starts populating it.
 */
export const phonePepper = defineSecret("PHONE_HASH_PEPPER");
