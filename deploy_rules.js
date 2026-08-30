const fs = require('fs');
const path = require('path');

const CONFIGSTORE = path.join(process.env.USERPROFILE, '.config', 'configstore', 'firebase-tools.json');
const token = JSON.parse(fs.readFileSync(CONFIGSTORE, 'utf8')).tokens.access_token;
const PROJECT = 'mini-verbigem';
const rules = fs.readFileSync(path.join(__dirname, 'firestore.rules'), 'utf8');

function api(method, url, body) {
  return fetch(url, {
    method,
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  }).then(async r => {
    const txt = await r.text();
    if (!r.ok) throw new Error(`HTTP ${r.status}: ${txt}`);
    return txt ? JSON.parse(txt) : {};
  });
}

(async () => {
  // 1. Utwórz ruleset
  const rs = await api('POST',
    `https://firebaserules.googleapis.com/v1/projects/${PROJECT}/rulesets`,
    { source: { files: [{ name: 'firestore.rules', content: rules }] } });
  console.log('Created ruleset:', rs.name);

  // 2. Nadpisz istniejący release (PATCH z nowym rulesetName, zagnieżdżone w release)
  const rel = await api('PATCH',
    `https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases/cloud.firestore`,
    { release: { rulesetName: rs.name } });
  console.log('Released:', JSON.stringify(rel));
  console.log('DEPLOY_OK');
})().catch(e => { console.error('DEPLOY_ERR', e.message); process.exit(1); });
