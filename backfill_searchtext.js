/**
 * 1.12 — jednorazowy backfill `searchText` na istniejących wiadomościach.
 *
 * Trigger `onMessageSearchIndex` indeksuje tylko to, co powstanie OD TERAZ.
 * Wszystko, co już leży w bazie, nie ma pola `searchText`, więc wyszukiwanie
 * ignorowałoby całą dotychczasową historię — a to w czacie jest większością treści.
 *
 * Robi dokładnie to samo co trigger i nic więcej:
 *   searchText = normalizeForSearch(text)
 * Pomija wiadomości bez `text` (w fazie 5 będą to załączniki) oraz te, których
 * `searchText` jest już aktualny — dzięki temu skrypt jest idempotentny i można
 * go bezpiecznie odpalić drugi raz po poprawce normalizacji.
 *
 * Transformacja NIE jest skopiowana do tego pliku: importujemy ją ze zbudowanego
 * `functions/lib/searchIndex`, żeby nie mogła się rozjechać z triggerem. Należy
 * najpierw zbudować funkcje (`cd functions && npm run build`), inaczej skrypt
 * odmówi działania zamiast zaindeksować coś źle.
 *
 * Użycie:
 *   node backfill_searchtext.js            # DRY RUN — tylko wypisuje, co by zrobił
 *   node backfill_searchtext.js --apply    # faktycznie zapisuje do Firestore
 *
 * Token bierze z pliku firebase-tools (ten sam co pozostałe skrypty w repo).
 */
const fs = require('fs');
const https = require('https');
const path = require('path');
const { execFileSync } = require('child_process');

const PROJECT = 'mini-verbigem';
const TOKEN_PATH = 'C:/Users/milo/.config/configstore/firebase-tools.json';

const APPLY = process.argv.includes('--apply');

// ------------------------------------------------------------- transformacja

// Zbudowany kod funkcji, nie kopia. Jeśli `lib` jest nieaktualne, dostaniemy
// starą normalizację — dlatego przed --apply warto zrobić `npm run build`.
const LIB = path.join(__dirname, 'functions', 'lib', 'searchIndex.js');
if (!fs.existsSync(LIB)) {
  throw new Error(
    'Brak ' + LIB + '\n' +
    'Zbuduj funkcje: cd functions && npm run build\n' +
    '(skrypt celowo nie niesie własnej kopii normalizacji — inaczej mogłaby\n' +
    ' rozejść się z triggerem i wyszukiwanie przestałoby działać po cichu).'
  );
}
const { normalizeForSearch } = require(LIB);

// ---------------------------------------------------------------- auth

function readTokens() {
  return JSON.parse(fs.readFileSync(TOKEN_PATH, 'utf8')).tokens;
}

function isFresh(tokens) {
  // 60 s marginesu, żeby nie wygasł w trakcie długiego backfillu.
  return tokens.expires_at && Date.now() < tokens.expires_at - 60_000;
}

function refreshViaCli() {
  console.log('  token wygasł — odświeżam przez `firebase projects:list`…');
  execFileSync('firebase', ['projects:list'], { stdio: 'ignore' });
  const tokens = readTokens();
  if (!isFresh(tokens)) {
    throw new Error(
      'Token nadal nieważny po odświeżeniu. Zaloguj się ręcznie: firebase login'
    );
  }
  return tokens;
}

async function getAccessToken() {
  let tokens = readTokens();
  if (!isFresh(tokens)) tokens = refreshViaCli();
  return tokens.access_token;
}

// ---------------------------------------------------------------- firestore REST

function request(method, reqPath, bodyObj, token) {
  return new Promise((resolve, reject) => {
    const body = bodyObj === null ? null : JSON.stringify(bodyObj);
    const headers = { Authorization: 'Bearer ' + token };
    if (body) {
      headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = Buffer.byteLength(body);
    }
    const req = https.request(
      { hostname: 'firestore.googleapis.com', path: reqPath, method, headers },
      (res) => {
        let data = '';
        res.on('data', (c) => (data += c));
        res.on('end', () => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            resolve(data ? JSON.parse(data) : {});
          } else {
            const err = new Error(
              method + ' ' + reqPath + ' → ' + res.statusCode + ' ' + data
            );
            err.status = res.statusCode;
            reject(err);
          }
        });
      }
    );
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

const BASE = `/v1/projects/${PROJECT}/databases/(default)/documents`;

async function listDocs(collectionPath, token, state) {
  let p = `${BASE}/${collectionPath}?pageSize=300`;
  if (state.pageToken) p += '&pageToken=' + encodeURIComponent(state.pageToken);

  const parsed = new URL('https://firestore.googleapis.com' + p);
  const res = await request('GET', parsed.pathname + parsed.search, null, token);
  state.pageToken = res.nextPageToken;
  return res.documents || [];
}

function patchDoc(docPath, obj, token) {
  const mask = Object.keys(obj)
    .map((k) => 'updateMask.fieldPaths=' + k)
    .join('&');
  const fields = {};
  for (const [k, v] of Object.entries(obj)) fields[k] = { stringValue: v };
  return request('PATCH', `${BASE}/${docPath}?${mask}`, { fields }, token);
}

function str(fields, name) {
  const f = fields && fields[name];
  return f && typeof f.stringValue === 'string' ? f.stringValue : null;
}

/** `.../documents/chats/X/messages/Y` → `chats/X/messages/Y` (dla patchDoc). */
function relativePath(fullName) {
  return fullName.split('/documents/')[1];
}

// ---------------------------------------------------------------- główna pętla

async function main() {
  const token = await getAccessToken();

  console.log(
    APPLY
      ? '=== BACKFILL searchText — ZAPIS ==='
      : '=== BACKFILL searchText — DRY RUN (dodaj --apply, żeby zapisać) ==='
  );

  let chats = 0;
  let seen = 0;
  let alreadyOk = 0;
  let skippedNoText = 0;
  let wouldWrite = 0;
  let written = 0;
  let failed = 0;

  const chatState = { pageToken: null };
  do {
    const chatDocs = await listDocs('chats', token, chatState);
    for (const chatDoc of chatDocs) {
      chats++;
      const chatPath = relativePath(chatDoc.name);
      const msgState = { pageToken: null };

      do {
        const msgs = await listDocs(chatPath + '/messages', token, msgState);
        for (const msg of msgs) {
          seen++;
          const text = str(msg.fields, 'text');
          if (text === null) {
            // Załączniki (faza 5) nie mają czego indeksować.
            skippedNoText++;
            continue;
          }

          const searchText = normalizeForSearch(text);
          if (!searchText) {
            skippedNoText++;
            continue;
          }

          if (str(msg.fields, 'searchText') === searchText) {
            alreadyOk++;
            continue;
          }

          wouldWrite++;
          if (APPLY) {
            try {
              await patchDoc(relativePath(msg.name), { searchText }, token);
              written++;
            } catch (e) {
              failed++;
              console.log('  BŁĄD ' + relativePath(msg.name) + ': ' + e.message);
            }
          }
        }
      } while (msgState.pageToken);
    }
  } while (chatState.pageToken);

  console.log('\n--- podsumowanie ---');
  console.log('czaty:                     ' + chats);
  console.log('wiadomości:                ' + seen);
  console.log('bez tekstu / puste:        ' + skippedNoText);
  console.log('searchText już aktualny:   ' + alreadyOk);
  console.log('do zapisania:              ' + wouldWrite);
  if (APPLY) {
    console.log('zapisane:                  ' + written);
    console.log('błędy:                     ' + failed);
  }
  if (!APPLY && wouldWrite > 0) {
    console.log('\nUruchom ponownie z --apply, żeby zapisać.');
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
