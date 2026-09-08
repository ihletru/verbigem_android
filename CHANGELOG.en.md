# Verbigem Android — changelog

---

## v1.0.48 (2026-09-08) — versionCode 49

**PRO status is now derived, not stored permanently.**

- Account is PRO only when an active ad-removal purchase (`noAdsUntil` in the future) OR wallet balance > 0.
- On `noAdsUntil` expiry with empty wallet the account returns to Free. The stored `plan` field is informational only.

## v1.0.47 (2026-09-08) — versionCode 48

**Account status after buying "Remove ads".**

- Buying it **switches the account to PRO 💎**. Not only does the ad banner disappear — the Pro features unlock too (some of them previously still saw the account as Free).
- The status card now **always shows your wallet balance** — if you never topped it up you'll see **0.00**. Removing ads and the wallet are two separate purchases.
- Below it there's a new **countdown to when ads come back** — you can see straight away how many days are left of the period you paid for.

## v1.0.46 (2026-09-08) — versionCode 47

**Remove ads — a one-time prepaid purchase, not a subscription.**

- Next to **Top up wallet** in the account status card there is now a **Remove ads** button. It opens a secure one-time payment — you pick a period and, once paid, the ad banner stays hidden for that long.
- Four tiers to choose from: **$1 · 1 mo**, **$3 · 3 mo**, **$5 · 5 mo**, **$10 · 10 mo**.
- This is **not a subscription** — you pay once, ads stay hidden for the chosen period and nothing renews by itself. **Your account switches to PRO 💎.**
- The account status card now also shows your **wallet balance** — if you never topped it up you'll see **0.00** (removing ads does not add credits, that's a separate purchase) — plus a **countdown to when ads come back**, i.e. how many days are left of the period you paid for.

## v1.0.45 (2026-09-08) — versionCode 46

**Fixed: paid online translation and wallet top-up.**

- Verbigem's backend moved to a different region (matching the project's own region). The app was still calling the old address, so **translating with the paid online models failed** — it works again now.
- **Topping up your wallet from the app did not work at all** — the button couldn't open the checkout, for the same reason. Fixed; credits are added automatically after payment.

## v1.0.44 (2026-09-07) — versionCode 45

**Easier to use your own OpenRouter key.**

- The online-model picker card is now called **Default online translation model**, so it's clear it sets the model used for online translations.
- The **Own OpenRouter key** card (free models on your own key) now has a clickable link to the key-generation page — both in the description under the card and in the help window. Once you've signed up at OpenRouter, one tap takes you straight to creating a key.

## v1.0.43 (2026-09-07) — versionCode 44

**Top up your account right inside the app.**

- The account status card now has a **Top up wallet** button. It opens a secure checkout and, once you pay, automatically adds credits to your wallet — no reload and nothing to type in by hand.
- Three packs to choose from: Small (300 credits), Medium (500 credits) and Large (1000 credits).
- Your wallet balance refreshes itself in the background right after a successful payment.

## v1.0.42 (2026-09-06) — versionCode 43

**Help now works on disabled buttons too.**

- Long-pressing a button shows its explanation even when the button is greyed out — for example an empty text field, engines locked on a free account, or when there is no photo to read.

## v1.0.41 (2026-09-06) — versionCode 42

**Look-and-feel fixes after the help windows rolled out.**

- The “Got it” button in help windows is now always in your chosen interface language.
- The bottom bar is back to 5 icons: Translate, Conversation, Chat, Contacts, Profile.
- The Translate screen scrolls above the keyboard while you type.
- The engine icons (accurate, both, online) show help even on a free account.
- The profile gained an **About the app** card with the version number and a **What's new** link.
- The app icon now uses a cream background that matches the pages' theme.

## v1.0.40 (2026-09-06) — versionCode 41

**Every icon in the app now has an explainer window.**

- Tapping an icon does its job; long-pressing it opens a description of what it is, what it does and how to use it.
- This covers the Translate, Conversation, Chat, Contacts and Profile screens — dozens of new descriptions across 6 languages.

## v1.0.39 (2026-09-05) — versionCode 40

**The biggest batch of new features so far.**

- 1:1 Chat and Contacts: invitations, importing saved contacts (.vcf files), and finding friends.
- Phone-number verification by SMS works in every region of the world, with clear error messages.
- Push notifications (FCM) for new messages.
- Photos with full-screen preview and text reading from images (OCR) in chat.
- Your own QR codes to add friends, plus a code scanner.
- A privacy policy in 6 languages and a clear explanation of app permissions.
- The app runs in 6 languages: Polish, English, Spanish, Chinese, German and Turkish.
