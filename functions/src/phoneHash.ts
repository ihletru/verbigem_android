import * as crypto from "crypto";

/**
 * How a phone number reaches us, and how little of it we keep.
 *
 * 1. The app sends `SHA-256(E.164)` — never the number itself.
 * 2. We turn that into `HMAC-SHA256(hash, pepper)` and use it as the document id in
 *    `phoneDirectory`, which no client can read.
 *
 * Step 2 is what makes a stolen database useless: without the pepper (Secret
 * Manager, never in code) the hashes cannot be reversed into numbers. Step 1 is what
 * makes the network traffic useless.
 *
 * Both sides must hash the EXACT same string. The app normalises to E.164 with
 * `android.telephony.PhoneNumberUtils.formatNumberToE164` (see `PhoneNumbers.kt`);
 * Firebase Auth hands us the verified number already in E.164, so all we do here is
 * strip what should not be there.
 */
export const SHA256_HEX = /^[0-9a-f]{64}$/;

export function isSha256Hex(value: unknown): value is string {
  return typeof value === "string" && SHA256_HEX.test(value);
}

export function sha256Hex(value: string): string {
  return crypto.createHash("sha256").update(value, "utf8").digest("hex");
}

/**
 * The `phoneDirectory` document id for a hash.
 *
 * Throws rather than returning a fallback: an empty pepper would silently produce a
 * directory that every hash collides in, and worse, that another deployment with a
 * real pepper could not read. Callers surface it as `failed-precondition`.
 */
export function directoryId(hash: string, pepper: string | undefined): string {
  if (!pepper) {
    throw new Error("PHONE_HASH_PEPPER is not configured");
  }
  return crypto.createHmac("sha256", pepper).update(hash).digest("hex");
}

/**
 * Firebase Auth's `phone_number` claim is E.164 by definition (`+` then digits, no
 * separators). We only strip whitespace and re-assert the `+` so that a future
 * provider change cannot quietly shift every hash by one character.
 *
 * Returns null when the value is not usable, so callers can answer
 * `failed-precondition` instead of writing garbage into the directory.
 */
export function normaliseE164(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const trimmed = raw.replace(/[\s().-]/g, "");
  if (!trimmed) return null;
  const digits = trimmed.startsWith("+") ? trimmed.slice(1) : trimmed;
  if (!/^\d{6,15}$/.test(digits)) return null;
  return `+${digits}`;
}
