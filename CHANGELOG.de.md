# Verbigem Android — Verlauf der Änderungen

---

## v1.0.50 (2026-09-09) — versionCode 51

**Behoben: PRO-Konten können sich wieder anmelden.**

- Die Cloud Function speicherte `noAdsUntil` als `Firestore Timestamp`, Android erwartete aber eine normale Zahl (ms). Beim Lesen des Profils stürzte die App mit „Failed to convert a value of type com.google.firebase.Timestamp to long" ab und **PRO-Konten konnten sich nicht anmelden**. `UserProfile` akzeptiert nun beide Formate, neue Einträge werden als `number` geschrieben.

## v1.0.49 (2026-09-09) — versionCode 50

**Werbung in der kostenlosen Version — mit voller Kontrolle über die Einwilligung.**

- Auf dem Übersetzungsbildschirm wird jetzt ein **Google-Werbebanner** angezeigt. PRO 💎-Konten (gekaufte Werbefreiheit oder Guthaben > 0) sehen keine Werbung — für sie ändert sich nichts.
- Im EWR, im Vereinigten Königreich und in der Schweiz zeigen wir beim ersten Start ein **Einwilligungsformular von Google**. Anzeigen werden erst nach Deiner Entscheidung geladen — keinen Moment früher.
- In der Karte **Datenschutz** in Deinem Profil gibt es neu die **Datenschutzeinstellungen für Werbung** — Du kannst Deine Einwilligung jederzeit ändern oder widerrufen.
- Die Anzeigen liefert Google. Der Text Deiner Übersetzungen gelangt nie ins Werbenetzwerk — übersetzt wird auf Deinem Gerät.

## v1.0.48 (2026-09-08) — versionCode 49

**PRO-Status wird jetzt berechnet, nicht dauerhaft gespeichert.**

- Konto ist PRO nur bei aktivem Werbe-Entfernungs-Kauf (`noAdsUntil` in der Zukunft) oder Guthaben > 0.
- Beim Ablauf von `noAdsUntil` mit leerem Guthaben kehrt das Konto zu Free zurück. Das Feld `plan` ist nur informativ.

## v1.0.47 (2026-09-08) — versionCode 48

**Kontostatus nach dem Kauf von „Werbung entfernen".**

- Nach dem Kauf **wechselt das Konto auf PRO 💎**. Nicht nur das Werbebanner verschwindet — auch die Pro-Funktionen werden freigeschaltet (einige sahen das Konto vorher weiterhin als Free).
- Die Statuskarte zeigt jetzt **immer dein Guthaben** — hast du nie aufgeladen, siehst du **0.00**. „Werbung entfernen" und Guthaben sind zwei getrennte Zahlungen.
- Darunter neu: ein **Countdown bis zur Rückkehr der Werbung** — du siehst sofort, wie viele Tage vom bezahlten Zeitraum übrig sind.

## v1.0.46 (2026-09-08) — versionCode 47

**Werbung entfernen — einmalige Vorauszahlung, kein Abo.**

- Neben **Guthaben aufladen** in der Karte zum Kontostatus gibt es jetzt die Schaltfläche **Werbung entfernen**. Sie öffnet eine sichere Einmalzahlung — du wählst einen Zeitraum und nach dem Kauf bleibt das Werbebanner für diese Dauer ausgeblendet.
- Vier Pakete zur Auswahl: **$1 · 1 Monat**, **$3 · 3 Monate**, **$5 · 5 Monate**, **$10 · 10 Monate**.
- Das ist **kein Abonnement** — du zahlst einmal, die Werbung bleibt für den gewählten Zeitraum ausgeblendet und nichts verlängert sich von selbst. **Dein Konto wechselt auf PRO 💎.**
- In der Kontostatus-Karte siehst du jetzt auch dein **Guthaben** — hast du nie aufgeladen, steht dort **0.00** („Werbung entfernen" lädt das Guthaben nicht auf, das ist eine eigene Zahlung) — sowie einen **Countdown bis zur Rückkehr der Werbung**, also die Anzahl der Tage, die vom bezahlten Zeitraum übrig sind.

## v1.0.45 (2026-09-08) — versionCode 46

**Behoben: kostenpflichtige Online-Übersetzung und Guthaben aufladen.**

- Der Verbigem-Server ist in eine andere Region umgezogen (passend zur Region des Projekts). Die App rief weiterhin die alte Adresse auf, deshalb **schlug das Übersetzen mit den kostenpflichtigen Online-Modellen fehl** — jetzt funktioniert es wieder.
- **Das Aufladen des Guthabens aus der App funktionierte überhaupt nicht** — die Schaltfläche konnte aus demselben Grund keine Zahlung öffnen. Behoben; nach dem Kauf werden die Credits automatisch gutgeschrieben.

## v1.0.44 (2026-09-07) — versionCode 45

**Einfachere Nutzung deines eigenen OpenRouter-Schlüssels.**

- Die Karte zur Auswahl des Online-Modells heißt jetzt **Standard-Online-Übersetzungsmodell**, sodass klar ist, dass hier das Modell für Online-Übersetzungen festgelegt wird.
- Die Karte **Eigener OpenRouter-Schlüssel** (kostenlose Modelle mit eigenem Schlüssel) hat jetzt einen klickbaren Link zur Seite zum Erstellen eines Schlüssels — sowohl in der Beschreibung unter der Karte als auch im Hilfefenster. Nach der Anmeldung bei OpenRouter führt ein Klick direkt zur Schlüsselerstellung.

## v1.0.43 (2026-09-07) — versionCode 44

**Guthaben direkt in der App aufladen.**

- Die Kontostatus-Karte hat jetzt einen Button **Wallet aufladen**. Er öffnet eine sichere Kasse und schreibt nach der Zahlung automatisch Guthaben auf dein Wallet — ohne Neuladen und ohne etwas von Hand einzutippen.
- Drei Pakete zur Auswahl: Klein (300 Credits), Mittel (500 Credits) und Groß (1000 Credits).
- Dein Wallet-Saldo aktualisiert sich im Hintergrund direkt nach einer erfolgreichen Zahlung.

## v1.0.42 (2026-09-06) — versionCode 43

**Hilfe funktioniert jetzt auch bei deaktivierten Buttons.**

- Einen Button lange drücken zeigt seine Erklärung selbst dann, wenn er ausgegraut ist — zum Beispiel bei einem leeren Textfeld, bei für ein Free-Konto gesperrten Engines oder wenn kein Foto zum Lesen da ist.

## v1.0.41 (2026-09-06) — versionCode 42

**Optik-Korrekturen nach dem Ausrollen der Hilfefenster.**

- Der „Verstanden“-Button in Hilfefenstern ist jetzt immer in deiner gewählten Oberflächensprache.
- Die untere Leiste ist wieder auf 5 Symbole: Übersetzen, Gespräch, Chat, Kontakte, Profil.
- Der Übersetzen-Bildschirm scrollt über die Tastatur, während du tippst.
- Die Engine-Symbole (genau, beide, online) zeigen Hilfe selbst bei einem Free-Konto.
- Das Profil hat eine **Über die App**-Karte mit Versionsnummer und einem **Neuerungen**-Link bekommen.
- Das App-Symbol hat jetzt einen cremefarbenen Hintergrund, der zum Theme der Seiten passt.

## v1.0.40 (2026-09-06) — versionCode 41

**Jedes Symbol in der App hat jetzt ein Erklär-Fenster.**

- Tippen führt die Aktion aus; langes Drücken öffnet eine Beschreibung, was es ist, was es tut und wie man es nutzt.
- Das gilt für die Bildschirme Übersetzen, Gespräch, Chat, Kontakte und Profil — Dutzende neue Beschreibungen in 6 Sprachen.

## v1.0.39 (2026-09-05) — versionCode 40

**Das größte Paket an Neuerungen bisher.**

- 1:1-Chat und Kontakte: Einladungen, Import gespeicherter Kontakte (.vcf-Dateien) und Freunde finden.
- Telefonnummern-Bestätigung per SMS funktioniert in jeder Region der Welt, mit klaren Fehlermeldungen.
- Push-Benachrichtigungen (FCM) für neue Nachrichten.
- Fotos mit Vollbild-Vorschau und Texterkennung aus Bildern (OCR) im Chat.
- Eigene QR-Codes zum Hinzufügen von Freunden plus ein Code-Scanner.
- Eine Datenschutzerklärung in 6 Sprachen und eine klare Erläuterung der Berechtigungen.
- Die App läuft in 6 Sprachen: Polnisch, Englisch, Spanisch, Chinesisch, Deutsch und Türkisch.
