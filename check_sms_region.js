// One-off diagnostic: read the Firebase Auth (Identity Platform) config for the
// project, to see whether SMS sending is allowed for Paraguay (+595).
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

function get(url, token) {
  return new Promise((resolve, reject) => {
    const req = https.request(
      url,
      { method: "GET", headers: { Authorization: "Bearer " + token } },
      (res) => {
        let out = "";
        res.on("data", (c) => (out += c));
        res.on("end", () => resolve({ status: res.statusCode, body: out }));
      }
    );
    req.on("error", reject);
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
  const url = `https://identitytoolkit.googleapis.com/admin/v2/projects/${PROJECT}/config`;
  const r = await get(url, token);
  console.log("HTTP", r.status);
  if (r.status === 200) {
    const cfg = JSON.parse(r.body);
    console.log("signIn.phone:", JSON.stringify(cfg.signIn && cfg.signIn.phoneNumber, null, 2));
    console.log("smsRegionConfig:", JSON.stringify(cfg.smsRegionConfig, null, 2));
  } else {
    console.log(r.body.slice(0, 2000));
  }
})().catch((e) => {
  console.error("FAILED:", e.message);
  process.exit(1);
});
