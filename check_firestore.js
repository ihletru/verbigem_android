const fs = require('fs');
const path = require('path');

const CONFIGSTORE = path.join(process.env.USERPROFILE, '.config', 'configstore', 'firebase-tools.json');
const token = JSON.parse(fs.readFileSync(CONFIGSTORE, 'utf8')).tokens.access_token;
const PROJECT = 'mini-verbigem';

async function getDoc(coll, doc) {
  const url = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents/${coll}/${doc}`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  const body = await res.json();
  if (!res.ok) {
    console.log(`[${coll}/${doc}] ERROR ${res.status}:`, JSON.stringify(body));
    return;
  }
  const fields = body.fields || {};
  const out = {};
  for (const [k, v] of Object.entries(fields)) {
    out[k] = v.integerValue ?? v.stringValue ?? v.booleanValue ?? v.doubleValue ?? v;
  }
  console.log(`[${coll}/${doc}]`, JSON.stringify(out, null, 2));
}

(async () => {
  await getDoc('app_config', 'update');
  await getDoc('app_config', 'tts');
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
