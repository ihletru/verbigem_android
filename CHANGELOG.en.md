# Verbigem Android — changelog

---

## v1.0.66 (2026-09-10) — versionCode 66

**Fixed: the payment opened our website instead of the Paddle checkout.**

- After picking a wallet or "Remove ads" package the browser showed our homepage and nothing happened — there was no way to reach the payment at all.
- Cause: Paddle builds the checkout link from your own domain and expects that page to load Paddle.js. Our homepage did not, so the checkout had nothing to open on.
- There is now a dedicated checkout page which opens the payment at once, also when nobody is signed in in the browser.
- Wallet packages used to read "300 / 500 / 1000 credits" although those were cents. You now see the real prices: $3, $5 and $10 (1 USD paid = 1 USD of balance), the same as on the website.

## v1.0.65 (2026-09-10) — versionCode 65

**Fixed: some messages ignored the chosen interface language.**

- With English selected, some messages still appeared in Polish (and the other way round). This covered sign-in errors, translation, speech and text recognition, account top-up and the app-update window. The same rule now covers model-download messages, the notification-channel name, the notification action labels (Reply, Mark as read) and the e-mail subject used when inviting a contact.
- The interface language is now read at app startup rather than on the first screen. That removes the brief flash of Polish on launch, and makes the notification-channel name and action labels use the chosen language even while the app runs in the background, with no screen and no selected context.
- Cause: those texts asked the system context for a translation, and the system context knows nothing about the language picked in the app — it used the phone's language. Every such place now goes through one shared, correct source of texts.
- Technical errors from libraries (e.g. "HTTP 402") no longer reach the screen — they stay in the log, while the user sees a clear message in the chosen language.
- Speech-recognition errors had **eleven** Polish texts hardcoded in the source (including "Network error" and "No speech detected"). They now all come from the translations and follow the interface language.
- Wrong password, e-mail already in use and no internet during sign-in finally have their own messages instead of English text from Firebase.
- Update download errors (incomplete file, server error, installer error) are in the chosen language too.
- The same mistake reached **date and number formatting**: the weekday abbreviations in the chat list ("Mon", "Tue") and the detected-country name on the phone-verification screen ("Poland" instead of "Polska") took the phone's language, not the interface language. The model size used the phone's decimal separator ("2.9 GB" instead of "2,9 GB").
- The fallback title and body of a new-message notification (used when a push carries no text of its own) took the phone's language too — it now goes through the same path as every other message.
- An error code from Firebase we do not recognise used to append an English sentence from the library to a message in your language. In that rare case you now get a sentence in the language you picked, and the technical detail stays in the log.

## v1.0.64 (2026-09-10) — versionCode 64

**Fixed: the Google Play build had only two interface languages.**

- Installing from Play gave you Polish and English only — German, Spanish, Turkish and Chinese were missing entirely, even though all six worked in the version downloaded from our own site.
- Cause: Google Play splits the AAB into smaller pieces, and by default it splits them **by language** too — the phone received only its own language plus English as the fallback. The APK from our site is a single file that contains everything, which is why the problem never showed up there.
- Language splitting is now switched off for the Play build. It costs a few hundred kilobytes, but every language now works on every phone.

**Fixed: the model-download window lied, and it spoke Polish.**

- After downloading the Fast model, switching to Accurate showed a green "The Accurate model is ready to use!" — even though that model was not on the phone at all. The app took the state of the last download and stuck the new model's name on it.
- The download window ignored the chosen interface language: with English selected it still read in Polish. Strings shown inside system windows have to be handed the in-app language explicitly, and this one window did not.
- **Eight other windows** in the app had the same fault (confirmations in Profile, the channel picker in Contacts, the photo preview in chat, the update dialogs). They all came up in the phone's language instead of the chosen one — they now share one correct wrapper.
- Polish texts hardcoded in the download code (out of memory, out of space, server error) are gone — they would have shown in Polish no matter which language was selected.
- An expired SMS code now has its own message. "That does not look like the code we sent" used to mean both a typo and a code that had already expired, and those two need completely different reactions.
- The window for entering the code went from 60 to 120 seconds. With a slower SMS delivery the old 60 seconds ran out before the code could be typed, and a perfectly correct code was then rejected.

## v1.0.63 (2026-09-10) — versionCode 63

**Fixed: the app crashed on launch on newer phones.**

- On Android 14 and above the app crashed within a fraction of a second of starting — a black screen and an error, with no way to get in.
- Cause: a field on the main screen was being created too early, before the system had finished attaching the application. Older Android versions tolerated this; newer ones throw an error.
- The field is now created only when it is actually needed. On the Google Play build it is not created at all, since it is unused there.
- Note: from this version the version name matches the build number (63 = 1.0.63). They used to be off by one.

## v1.0.60 (2026-09-10) — versionCode 61

**Build: compile/target SDK raised to API 36 (Android 16).**

- Google Play requires new apps to target API 36 since 2026-08-31; we raised `compileSdk` and `targetSdk` from 35 to 36.
- No code changes; build target only. End users see no difference.

## v1.0.59 (2026-09-10) — versionCode 60

**Fixed: "1 contacts", "1 mutual friends".**

- Three messages with a count always said the same thing whatever the number: importing a file with five contacts produced "Imported 5 contact", and one shared friend showed as "1 mutual friends".
- Those three messages now follow each language's plural rules. Polish gets separate forms for 1, 2-4 and 5+ ("1 kontakt", "3 kontakty", "10 kontaktów"); Turkish and Chinese keep a single form, because those languages do not inflect the noun after a number.
- Built on Android's plural resources rather than a hand-written "if 1 … else", so adding a language later does not require code changes.


## v1.0.58 (2026-09-10) — versionCode 59

**Fixed: two places that pretended everything worked.**

- Deleting a conversation that failed to save gave no sign at all — you went back to your inbox and the conversation was still sitting there, with no explanation. The phone now says it did not work.
- A failed people search (offline, or permission denied) looked exactly like "no such user": just an empty list. The search box now reports that the request failed. An empty list means nobody was found again.
- Both messages are localised in all 6 languages, like the rest of the app.


## v1.0.57 (2026-09-10) — versionCode 58

**Fixed: the second message could get stuck on "sending".**

- When you sent something while a previous message was still on its way (typical with two photos in a row), the new one waited for the next trigger — the network coming back, or another send. Now the queue drains itself again right after, immediately.
- When Read Pro could not generate speech, it did nothing at all: no sound and no explanation. Now it says plainly that it failed.


## v1.0.56 (2026-09-10) — versionCode 57

**Fixed: the microphone stayed silent when something went wrong.**

- No microphone permission, no speech recognition on the phone, and a failed recognition all ended in nothing but a line in the system log. To you they looked identical: the microphone "just doesn't work", with no reason given.
- Now each of those three cases says plainly what happened, in a short message above the input box. The wording is in 6 languages, like the rest of the app.


## v1.0.55 (2026-09-10) — versionCode 56

**Fixed: a photo or voice message that failed to send used to disappear without a trace.**

- A plain text message goes into a local queue: when the network drops you get a red "not sent" bubble with a retry button. Photos and voice messages used to bypass that queue — on failure there was no bubble and no message of any kind. It simply vanished, and the only trace was a line in the system log.
- Photos and voice messages now go through the same queue as text: the bubble appears immediately (with a thumbnail straight from the phone, or with the transcript), and after a failed send it gets "not sent" and "retry" — exactly like a text message. Retrying does not ask you to pick the photo again.
- Requires a one-off local database migration (v9 → v10). Nothing is lost.


## v1.0.54 (2026-09-10) — versionCode 55

**Fixed: error messages were in Polish (or English) regardless of the app language.**

- Some errors — sign-in, conversation-mode translation, text recognition from a photo, Read Pro — had their text hardcoded. With the app set to English, German, Spanish, Turkish or Chinese you still got a Polish sentence, e.g. "Błąd logowania".
- All error messages now come from the same resources as the rest of the interface, so they exist in all 6 languages.
- We also audited the whole localization: 482 strings, none missing in any of the 6 languages.

## v1.0.53 (2026-09-09) — versionCode 54

**New: the scanner (OCR) screen has its own engine picker.**

- Until now the scanner always translated with the Fast model, no matter what you picked on the translator screen. There is now the same picker above the text field: ⚡ Fast, 🎯 Accurate, ⚖️ Both, ☁️ Online.
- The choice is shared with the translator screen — set it once and it applies to both.
- ⚖️ Both shows the two results one under the other, each with its own label (⚡ Fast / 🎯 Accurate).
- When the weights for the selected model aren't downloaded, the scanner shows the same download dialog as the translator — no need to go back to the main screen.

**Fixed: the scanner result header said "(Fast)" in every language.**

- The label above the result had "Fast" hardcoded — including the English, German, Spanish, Turkish and Chinese versions, where a Polish word was left in place. It now shows the name of the selected engine.
- The **Translate (X)** button on the translator screen now shows the short engine name (Fast / Accurate / Both / Online) instead of the long description with the model size.

## v1.0.52 (2026-09-09) — versionCode 53

**Fixed: ads never showed on free accounts.**

- For accounts where Google does not require consent (outside the EEA and the UK), the consent flow could report „cannot request ads" — typically on a freshly approved AdMob account with no „Privacy & messaging" set up. The ads SDK then waited for a consent that was never coming, and the banner stayed empty forever. The app now initialises ads on its own after 3 seconds.
- Nothing changes in the EEA and the UK: without your consent ads stay off — we do not break Google's rules.
- Logs now include the full consent state and the error code when an ad fails to load, so an empty banner is easier to diagnose.

**Fixed: the model-download dialog and the Translate button talked about the Fast model no matter which tier was picked.**

- The „Download model" dialog was hardcoded for the Fast model (~440 MB). When downloading the Accurate (~1.1 GB) one it showed „Fast" in the title and „~1.1 GB" underneath — contradictory information.
- The title, body, download button, progress label and ready label are now parameterised, so they always match the tier you are actually downloading.
- The **Translate (X)** button in the main translator now shows the engine you actually picked — Fast, Accurate, Both or Online — not always „Fast".

## v1.0.50 (2026-09-09) — versionCode 51

**Fixed: PRO accounts can sign in again.**

- The cloud function wrote `noAdsUntil` as a `Firestore Timestamp`, but Android expected a plain number (ms). Reading the profile crashed with „Failed to convert a value of type com.google.firebase.Timestamp to long" and **PRO accounts could not sign in**. `UserProfile` now accepts both formats, and new writes go in as a `number`.

## v1.0.49 (2026-09-09) — versionCode 50

**Ads on the free plan, with full control over consent.**

- A **Google banner ad** now shows on the translation screen. PRO 💎 accounts (ad-removal purchase or wallet balance > 0) see no ads — nothing changes for them.
- In the EEA, the UK and Switzerland you get a **Google consent form** on first launch. Ads load only after you decide — never before.
- **Ad privacy settings** were added to the **Privacy** card in your profile, so you can change or withdraw consent at any time.
- Ads are served by Google. The text you translate never reaches the ad network — translation happens on your device.

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
