// One-off fix: allow sending verification SMS to Paraguay (+595).
//
// The project had `smsRegionConfig: { allowlistOnly: {} }` — an empty allowlist,
// which is the default for new projects and means "no region at all". That is why
// every "Text me a code" tap failed with status 17006:
//   "SMS unable to be sent until this region enabled by the app developer."
//
// Usage: node set_sms_region.js PY,PL   (defaults to PY alone)
// Check what is set right now: node check_sms_region.js
//
// No credential lives in this file: we reuse the public OAuth client of
// firebase-tools itself (the same constants as node_modules/firebase-tools/lib/api.js)
// together with the refresh token that `firebase login` keeps outside the repo, in
// ~/.config/configstore.
const fs = require("fs");
const https = require("https");

const CFG = "C:/Users/milo/.config/configstore/firebase-tools.json";
const PROJECT = "mini-verbigem";
const CLIENT_ID =
  "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com";
const CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi";

const regions = (process.argv[2] || "PY").split(",").map((s) => s.trim().toUpperCase());
const cfg = JSON.parse(fs.readFileSync(CFG, "utf8"));

function post(url, body) {
  const data = new URLSearchParams(body).toString();
  return new Promise((resolve, reject) => {
    const req = https.request(
      url,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Content-Length": Buffer.byteLength(data),
        },
      },
      (res) => {
        let out = "";
        res.on("data", (c) => (out += c));
        res.on("end", () => resolve({ status: res.statusCode, body: out }));
      }
    );
    req.on("error", reject);
    req.end(data);
  });
}

function request(method, url, token, jsonBody) {
  const data = jsonBody === undefined ? null : JSON.stringify(jsonBody);
  return new Promise((resolve, reject) => {
    const req = https.request(
      url,
      {
        method,
        headers: Object.assign(
          { Authorization: "Bearer " + token },
          data ? { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(data) } : {}
        ),
      },
      (res) => {
        let out = "";
        res.on("data", (c) => (out += c));
        res.on("end", () => resolve({ status: res.statusCode, body: out }));
      }
    );
    req.on("error", reject);
    if (data) req.write(data);
    req.end();
  });
}

async function accessToken() {
  const t = cfg.tokens || {};
  if (t.access_token && t.expires_at && Date.now() < t.expires_at - 60_000) {
    return t.access_token;
  }
  const r = await post("https://oauth2.googleapis.com/token", {
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    refresh_token: t.refresh_token,
    grant_type: "refresh_token",
  });
  if (r.status !== 200) throw new Error("token refresh failed: " + r.body);
  return JSON.parse(r.body).access_token;
}

(async () => {
  const token = await accessToken();
  const base = `https://identitytoolkit.googleapis.com/admin/v2/projects/${PROJECT}/config`;

  const before = await request("GET", base, token);
  console.log("BEFORE smsRegionConfig:", JSON.parse(before.body).smsRegionConfig);

  const body = { smsRegionConfig: { allowlistOnly: { allowedRegions: regions } } };
  const r = await request("PATCH", base + "?updateMask=smsRegionConfig", token, body);
  console.log("PATCH HTTP", r.status);
  if (r.status !== 200) {
    console.log(r.body.slice(0, 2000));
    process.exit(1);
  }

  const after = await request("GET", base, token);
  console.log("AFTER  smsRegionConfig:", JSON.parse(after.body).smsRegionConfig);
})().catch((e) => {
  console.error("FAILED:", e.message);
  process.exit(1);
});
