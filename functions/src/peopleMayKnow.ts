import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { enforceRateLimit } from "./rateLimit";
import { lookupProfiles } from "./directory";

const db = admin.firestore();

/**
 * Friends-of-friends suggestions — computed server-side because the client can only
 * read its own `friendships` (member-scoped Firestore rules). There is no composite
 * index and none is needed: every query here is a single `whereArrayContains("members",
 * <one uid>)`, which the `members` array already serves.
 *
 * Rate limit per user. The Friends tab reloads this when it opens, so a generous
 * ceiling (30/hour) is plenty for a real app while still throttling enumeration.
 */
const RATE_LIMIT_CALLS = 30;
const RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000;

/** Bound the graph walk so one call can't fan out across thousands of docs. */
const MAX_FRIENDS_WALKED = 100;
/** How many suggestions we hand back at most. */
const MAX_SUGGESTIONS = 20;

interface Suggestion {
  uid: string;
  nickname: string;
  photoURL: string;
  mutualCount: number;
}

/**
 * Answers "who among my friends' friends might I know?" without exposing the graph.
 *
 * Walk:
 *   1. Read my own friendships; split into accepted friends and pending (either
 *      direction — a pending request is "already handled", so skip it).
 *   2. For each accepted friend, read THEIR accepted friendships and tally how many
 *      of my friends are also friends with each candidate.
 *   3. Drop candidates who are me, already my friend, or pending with me.
 *   4. Rank by mutual-count, cap, hydrate nicknames/photos, return.
 */
export const suggestFriends = onCall(
  {
    // App Check stays off on purpose — same reasoning as `matchContacts`: the shipped
    // APK is a debug build Play Integrity will not vouch for. Flip together with the
    // first Play Store release. See the comment in `contacts.ts`.
    enforceAppCheck: false,
    maxInstances: 10,
  },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in before loading suggestions.");
    }
    const me = request.auth.uid;

    await enforceRateLimit(me, "suggestFriends", RATE_LIMIT_CALLS, RATE_LIMIT_WINDOW_MS);

    // --- Step 1: my friendships ----------------------------------------------
    const mySnap = await db
      .collection("friendships")
      .where("members", "array-contains", me)
      .get();

    const myFriends = new Set<string>();
    const pendingWith = new Set<string>();

    for (const doc of mySnap.docs) {
      const status = doc.get("status");
      const members = doc.get("members");
      if (!Array.isArray(members)) continue;
      const other = members.find(
        (m: unknown) => typeof m === "string" && m !== me
      ) as string | undefined;
      if (!other) continue;
      if (status === "accepted") myFriends.add(other);
      else if (status === "pending") pendingWith.add(other);
    }

    // --- Step 2: walk friends-of-friends -------------------------------------
    const mutualCount = new Map<string, number>();
    const friendList = [...myFriends].slice(0, MAX_FRIENDS_WALKED);

    for (const friend of friendList) {
      const fSnap = await db
        .collection("friendships")
        .where("members", "array-contains", friend)
        .get();

      for (const doc of fSnap.docs) {
        if (doc.get("status") !== "accepted") continue;
        const members = doc.get("members");
        if (!Array.isArray(members)) continue;
        const other = members.find(
          (m: unknown) => typeof m === "string" && m !== friend
        ) as string | undefined;
        if (!other) continue;
        if (other === me) continue;
        if (myFriends.has(other)) continue;
        if (pendingWith.has(other)) continue;
        mutualCount.set(other, (mutualCount.get(other) ?? 0) + 1);
      }
    }

    // --- Step 3 + 4: rank, cap, hydrate --------------------------------------
    const ranked = [...mutualCount.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, MAX_SUGGESTIONS)
      .map(([uid]) => uid);

    if (ranked.length === 0) return { suggestions: [] as Suggestion[] };

    const profiles = await lookupProfiles(ranked);

    const suggestions: Suggestion[] = ranked.map((uid) => ({
      uid,
      nickname: profiles.get(uid)?.nickname ?? "",
      photoURL: profiles.get(uid)?.photoURL ?? "",
      mutualCount: mutualCount.get(uid) ?? 0,
    }));

    logger.debug("suggestFriends", { me, candidates: suggestions.length });
    return { suggestions };
  }
);
