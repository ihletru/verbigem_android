# Verbigem Android — Verlauf der Änderungen

---

## v1.0.62 (2026-09-10) — versionCode 62

**Behoben: Die App stürzte beim Start ab.**

- Auf Android 14 und neuer stürzte die App im Bruchteil einer Sekunde nach dem Start ab — schwarzer Bildschirm und eine Fehlermeldung, ohne dass man hineinkam.
- Ursache: Ein Feld auf dem Hauptbildschirm wurde zu früh erzeugt, bevor das System die Anwendung eingehängt hatte. Ältere Android-Versionen haben das toleriert, neuere werfen einen Fehler.
- Das Feld wird jetzt erst erzeugt, wenn es wirklich gebraucht wird. Im Google-Play-Build wird es gar nicht erzeugt, weil es dort nicht verwendet wird.
- Hinweis: Ab dieser Version stimmt die Versionsnummer mit der Build-Nummer überein (62 = 1.0.62). Früher lagen sie um eins auseinander.

## v1.0.60 (2026-09-10) — versionCode 61

**Build: Compile- und Target-SDK auf API 36 (Android 16) angehoben.**

- Google Play verlangt seit dem 2026-08-31, dass neue Apps mindestens API 36 anvisieren; `compileSdk` und `targetSdk` wurden von 35 auf 36 angehoben.
- Keine Code-Änderungen; nur das Build-Ziel. Für Endnutzer sichtbar nichts.

## v1.0.59 (2026-09-10) — versionCode 60

**Behoben: „1 Kontakt importiert" bei fünf Kontakten.**

- Drei Meldungen mit einer Zahl sagten immer dasselbe, egal wie hoch sie war: Beim Import einer Datei mit fünf Kontakten stand „Imported 5 contact", und ein gemeinsamer Freund erschien als „1 mutual friends".
- Diese drei Meldungen richten sich jetzt nach den Pluralregeln der jeweiligen Sprache. Polnisch erhält eigene Formen für 1, 2–4 und 5+ („1 kontakt", „3 kontakty", „10 kontaktów"); Türkisch und Chinesisch behalten eine Form, weil diese Sprachen das Substantiv nach einer Zahl nicht beugen.
- Umsetzung mit den Plural-Ressourcen von Android statt eines handgeschriebenen „wenn 1 … sonst", damit eine neue Sprache später keine Code-Änderung braucht.


## v1.0.58 (2026-09-10) — versionCode 59

**Behoben: zwei Stellen, die so taten, als hätte alles geklappt.**

- Wurde eine Unterhaltung nicht gelöscht, gab es keinerlei Hinweis: Du kehrtest zum Posteingang zurück und sie war noch da — ohne jede Erklärung. Das Handy meldet jetzt, dass es nicht geklappt hat.
- Eine fehlgeschlagene Personen-Suche (offline oder keine Berechtigung) sah genau so aus wie „keinen solchen Nutzer gefunden": einfach eine leere Liste. Unter dem Suchfeld steht jetzt, dass die Anfrage fehlgeschlagen ist. Eine leere Liste heißt wieder nur: niemand gefunden.
- Beide Meldungen gibt es in allen 6 Sprachen, wie den Rest der App.


## v1.0.57 (2026-09-10) — versionCode 58

**Behoben: Die zweite Nachricht konnte auf „wird gesendet" hängen bleiben.**

- Wenn du etwas gesendet hast, während die vorherige Nachricht noch unterwegs war (typisch bei zwei Fotos hintereinander), wartete die neue auf den nächsten Auslöser — auf das Netz oder auf das nächste Senden. Jetzt leert sich die Warteschlange direkt danach noch einmal von selbst.
- Wenn „Vorlesen Pro" keine Sprache erzeugen konnte, passierte gar nichts: kein Ton und keine Erklärung. Jetzt sagt die App direkt, dass es nicht geklappt hat.


## v1.0.56 (2026-09-10) — versionCode 57

**Behoben: Das Mikrofon schwieg, wenn etwas schiefging.**

- Fehlende Mikrofonberechtigung, keine Spracherkennung im Telefon und ein Fehler beim Erkennen endeten bisher nur in einem Eintrag im Systemprotokoll. Für dich sahen alle drei gleich aus: Das Mikrofon „geht einfach nicht", ohne jeden Grund.
- Jetzt sagt jede dieser drei Situationen direkt, was los ist — mit einem kurzen Hinweis über dem Eingabefeld. Die Texte gibt es in 6 Sprachen, wie den Rest der App.


## v1.0.55 (2026-09-10) — versionCode 56

**Behoben: Ein Foto oder eine Sprachnachricht, die nicht gesendet werden konnte, verschwand spurlos.**

- Eine normale Textnachricht landet in einer lokalen Warteschlange: Fällt das Netz aus, bekommst du eine rote Sprechblase „nicht gesendet" mit einem Wiederholen-Button. Fotos und Sprachnachrichten liefen bisher an dieser Warteschlange vorbei — bei einem Fehler gab es weder Sprechblase noch Hinweis. Die Nachricht war einfach weg, und der einzige Rest stand im Systemprotokoll.
- Fotos und Sprachnachrichten gehen jetzt durch dieselbe Warteschlange wie Text: Die Sprechblase erscheint sofort (mit einer Miniatur direkt aus dem Telefonspeicher oder mit der Transkription), und nach einem Fehlversuch erscheinen „nicht gesendet" und „wiederholen" — genau wie bei einer Textnachricht. Beim Wiederholen musst du das Foto nicht erneut auswählen.
- Erfordert eine einmalige Migration der lokalen Datenbank (v9 → v10). Es geht nichts verloren.


## v1.0.54 (2026-09-10) — versionCode 55

**Behoben: Fehlermeldungen waren unabhängig von der App-Sprache polnisch (oder englisch).**

- Manche Fehler — Anmeldung, Übersetzung im Gesprächsmodus, Texterkennung vom Foto, Vorlesen Pro — hatten ihren Text fest im Code stehen. Bei englischer, deutscher, spanischer, türkischer oder chinesischer App bekam man trotzdem einen polnischen Satz, z. B. „Błąd logowania".
- Alle Fehlermeldungen kommen jetzt aus denselben Ressourcen wie der Rest der Oberfläche und sind damit in allen 6 Sprachen vorhanden.
- Dabei haben wir die gesamte Lokalisierung geprüft: 482 Texte, in keiner der 6 Sprachen fehlt einer.

## v1.0.53 (2026-09-09) — versionCode 54

**Neu: der Scanner-Bildschirm (OCR) hat eine eigene Motor-Auswahl.**

- Bisher übersetzte der Scanner immer mit dem schnellen Modell, egal was du im Übersetzer gewählt hattest. Über dem Textfeld gibt es jetzt dieselbe Auswahlleiste: ⚡ Schnell, 🎯 Genau, ⚖️ Beide, ☁️ Online.
- Die Auswahl ist mit dem Übersetzer-Bildschirm geteilt — einmal eingestellt, gilt sie für beide.
- ⚖️ Beide zeigt die zwei Ergebnisse untereinander, jedes mit eigener Beschriftung (⚡ Schnell / 🎯 Genau).
- Fehlen die Gewichte des gewählten Modells, zeigt der Scanner denselben Download-Dialog wie der Übersetzer — ohne Rücksprung zum Hauptbildschirm.

**Behoben: die Ergebnisüberschrift des Scanners sagte in jeder Sprache „(Schnell)".**

- Über dem Ergebnis stand „Schnell" fest eingebaut — auch in der englischen, spanischen, türkischen und chinesischen Fassung, wo ein polnisches Wort stehen blieb. Jetzt steht dort der Name des gewählten Motors.
- Die Schaltfläche **Übersetzen (X)** zeigt jetzt den kurzen Namen des Motors (Schnell / Genau / Beide / Online) statt der langen Beschreibung mit der Modellgröße.

## v1.0.52 (2026-09-09) — versionCode 53

**Behoben: Auf kostenlosen Konten wurden gar keine Anzeigen geladen.**

- Bei Konten, für die Google keine Einwilligung verlangt (also außerhalb von EWR und Großbritannien), meldete die Einwilligungsabfrage mitunter „Anzeigen können nicht geladen werden" — meist bei einem frisch freigegebenen AdMob-Konto ohne konfiguriertes „Privacy & messaging". Das Anzeigen-SDK wartete dann auf eine Einwilligung, die nie kommt, und das Banner blieb für immer leer. Jetzt initialisiert die App Anzeigen nach 3 Sekunden selbst.
- Im EWR und in Großbritannien bleibt alles gleich: Ohne deine Einwilligung laufen keine Anzeigen — wir verstoßen nicht gegen die Regeln von Google.
- Außerdem stehen jetzt der vollständige Einwilligungsstatus und der Fehlercode in den Logs, wenn eine Anzeige nicht lädt — so lässt sich ein leeres Banner leichter erklären.

**Behoben: Download-Dialog und „Übersetzen"-Button waren unabhängig vom Modell immer auf „Schnell" festgelegt.**

- Das Dialogfenster „Modell herunterladen" war fest auf das Schnellmodell (~440 MB) geschrieben. Beim Genauen (~1,1 GB) stand gleichzeitig „Schnell" im Titel und „~1,1 GB" darunter — widersprüchlich.
- Titel, Beschreibung, Download-Schaltfläche, Fortschritt und „Modell bereit" sind jetzt parametrisiert und spiegeln das tatsächlich gewählte Modell mit seiner Größe wider.
- Der Button **Übersetzen (X)** im Hauptbildschirm zeigt jetzt die tatsächlich gewählte Engine an — Schnell, Genau, Beide oder Online — und nicht mehr nur „Schnell".

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
