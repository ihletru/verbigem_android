const fs = require('fs');
const path = require('path');

const CONFIGSTORE = path.join(process.env.USERPROFILE, '.config', 'configstore', 'firebase-tools.json');
const token = JSON.parse(fs.readFileSync(CONFIGSTORE, 'utf8')).tokens.access_token;
const PROJECT = 'mini-verbigem';

async function getActiveRulesetName() {
  const res = await fetch(`https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  console.error('releases:', JSON.stringify(body));
  if (body.releases && body.releases.length) {
    const active = body.releases.find(r => r.name.endsWith('/prod')) || body.releases[0];
    return active.rulesetName;
  }
  return null;
}

async function getRulesetContent(name) {
  const res = await fetch(`https://firebaserules.googleapis.com/v1/${name}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  return body;
}

(async () => {
  const rsName = await getActiveRulesetName();
  if (!rsName) { console.log('NO_ACTIVE_RULESET'); return; }
  const rs = await getRulesetContent(rsName);
  const files = (rs.source && rs.source.files) || [];
  for (const f of files) {
    console.log('===== FILE:', f.name, '=====');
    console.log(f.content);
  }
})().catch(e => { console.error('ERR', e.message); process.exit(1); });
