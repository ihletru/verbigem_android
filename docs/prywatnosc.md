> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🔒 Polityka prywatności (opublikowana)

**URL do zgłoszenia w Google Play:** `https://mini.verbigem.com/privacy/` · wersje: `/privacy/pl/`, `/en/`, `/de/`, `/es/`, `/zh/`, `/tr/` · kontakt: **privacy@verbigem.com**.

**Źródło treści:** `verbigem/mini/scripts/build_privacy.py` — jedyne źródło prawdy, generuje statyczne HTML do `public/privacy/` **i** `dist/privacy/`.

```bash
cd verbigem/mini && python scripts/build_privacy.py
cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem
```

- **Dlaczego `public/` i `dist/` naraz:** `firebase deploy --only hosting` zastępuje hosting zawartością `dist/`; `public/` musi być, żeby treść przetrwała przyszły `npm run build` (Vite kopiuje `public/` → `dist/`, ale najpierw czyści `dist/`).
- **Struktura URL-i:** katalogi (`/privacy/pl/index.html`), **nie** płaskie pliki — `firebase.json` ma catch-all rewrite `** → /index.html`, więc brak pliku = strona webappy. `/privacy/pl` dostaje 301 → `/privacy/pl/`.
- **⚠️ Cache:** `/privacy/**` ma `public, max-age=3600, must-revalidate`. **Nigdy nie dawaj tam `immutable`** — Cloudflare zamroziłby politykę na rok (ta sama pułapka co przy `/android/**`). Styl jest **inlinowany** właśnie dlatego, że reguła `**/*.css` ma `immutable`.

| Miejsce | Plik | Co robi |
|---|---|---|
| Profil → karta „Polityka prywatności" | `ProfileScreen.kt` | otwiera `/privacy/<uiLang>/` w przeglądarce |
| Profil → karta „Kontakt" | `ProfileScreen.kt` | otwiera `/contact/<uiLang>/` w przeglądarce (`AppLinks.contact`) |
| Kontakty → prominent disclosure | `ContactsPermissionScreen.kt` | ekran wyjaśnienia **przed** systemowym dialogiem `READ_CONTACTS` (wymóg Play) |

URL-e buduje **`data/AppLinks.kt`** (jedno źródło prawdy): `privacyPolicy(uiLang)`,
`privacyPolicyFor(context)` i `contact(uiLang)`. Wszystkie otwierają **przeglądarkę, nie WebView**.

**Zasada spójności:** treść disclosure (stringi `contacts_perm_*` × 6) musi zgadzać się z opublikowaną polityką. Zmiana polityki na stronie **nie wymaga** nowego APK; zmiana stringów — tak.

**`READ_CONTACTS`** jest w manifeście, ale aplikacja prosi o nie wyłącznie po ekranie wyjaśnienia. Numery nie opuszczają urządzenia — do chmury (Cloud Function `matchContacts`) idą wyłącznie skróty SHA-256 + HMAC.

---
