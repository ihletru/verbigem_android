const fs = require('fs');
const https = require('https');

const PROJECT = 'mini-verbigem';
const TOKEN_PATH = 'C:/Users/milo/.config/configstore/firebase-tools.json';

function readToken() {
  const j = JSON.parse(fs.readFileSync(TOKEN_PATH, 'utf8'));
  return j.tokens;
}

function refreshAccessToken(refreshToken) {
  return new Promise((resolve, reject) => {
    const body = new URLSearchParams({
      client_id: '563584335869-fgrhgmd47bqnekij5i8b5pr03ho549e.apps.googleusercontent.com',
      client_secret: 'j9iVZV37Wk3v8Tu7LTKj7a9H',
      refresh_token: refreshToken,
      grant_type: 'refresh_token'
    }).toString();
    const req = https.request({
      hostname: 'oauth2.googleapis.com',
      path: '/token',
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': Buffer.byteLength(body) }
    }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        const j = JSON.parse(data);
        if (j.access_token) resolve(j.access_token);
        else reject(new Error('refresh failed: ' + data));
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function toFirestoreValue(v) {
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'number') {
    if (Number.isInteger(v)) return { integerValue: String(v) };
    return { doubleValue: v };
  }
  if (typeof v === 'boolean') return { booleanValue: v };
  return { nullValue: null };
}

function docBody(obj) {
  const fields = {};
  for (const [k, v] of Object.entries(obj)) fields[k] = toFirestoreValue(v);
  return JSON.stringify({ fields });
}

function patchDoc(path, obj, accessToken) {
  return new Promise((resolve, reject) => {
    const body = docBody(obj);
    const url = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents/${path}?updateMask.fieldPaths=` +
      Object.keys(obj).join('&updateMask.fieldPaths=');
    const u = new URL(url);
    const req = https.request({
      hostname: u.hostname,
      path: u.pathname + u.search,
      method: 'PATCH',
      headers: {
        'Authorization': 'Bearer ' + accessToken,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body)
      }
    }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) resolve(data);
        else reject({ status: res.statusCode, data });
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function main() {
  let tokens = readToken();
  let accessToken = tokens.access_token;
  const refreshToken = tokens.refresh_token;

  const tts = {
    apiKey: "",
    defaultModelId: "google/gemini-3.1-flash-tts-preview",
    chineseModelId: "fish-audio/s2.1-pro",
    defaultVoice: "default",
    chineseVoice: "default",
    updatedAt: 0
  };
  const update = {
    versionCode: 2,
    versionName: "1.0.1",
    apkUrl: "https://github.com/ihletru/verbigem_android/releases/download/v1.0.1/app-debug-v2.apk",
    playStoreUrl: "https://play.google.com/store/apps/details?id=com.verbigem.app",
    onPlayStore: false,
    minSupportedCode: 1
  };

  try {
    console.log('Writing app_config/tts ...');
    await patchDoc('app_config/tts', tts, accessToken);
    console.log('OK tts');
    console.log('Writing app_config/update ...');
    await patchDoc('app_config/update', update, accessToken);
    console.log('OK update');
  } catch (e) {
    if (e && e.status === 401) {
      console.log('Token expired, refreshing...');
      accessToken = await refreshAccessToken(refreshToken);
      console.log('Writing app_config/tts (retry)...');
      await patchDoc('app_config/tts', tts, accessToken);
      console.log('OK tts');
      console.log('Writing app_config/update (retry)...');
      await patchDoc('app_config/update', update, accessToken);
      console.log('OK update');
    } else {
      console.error('ERROR:', JSON.stringify(e).slice(0, 500));
      process.exit(1);
    }
  }
}

main();
