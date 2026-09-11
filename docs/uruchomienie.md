> 📄 Fragment dokumentacji Verbigem Android. Indeks: [README.md](../README.md)

# 🚀 Jak uruchomić projekt

1. Otwórz katalog `c:\Users\milo\verbigem\android` w **Android Studio**.
2. Poczekaj na synchronizację Gradle (`Sync Project with Gradle Files`).
3. Podłącz urządzenie z Androidem lub uruchom emulator (`arm64-v8a` lub `x86_64`).
4. Kliknij **Run** (`Shift + F10`) lub zbuduj APK:
   ```bash
   ./gradlew assembleDebug
   ```

**Build (Windows, bash):** JDK 21 w `C:/Users/milo/.jdks/jbr-21.0.11` (ustaw `JAVA_HOME`). Natywny build NDK (~2–3 min) kompiluje `libverbigem_llama.so` (Release + KleidiAI + Vulkan).

## Flow wydania (auto-update end-to-end — BEZ KABLA)

Poniższa lista to **JEDYNE źródło prawdy** dla wypuszczania wersji (wcześniejsza wersja skrótu błędnie kazała edytować `dist/android/version.json` ręcznie, co rozjeżdżało się z generowaniem pliku przez Vite). **Zanim wykonasz kroki, przeczytaj pułapki wyżej:** „⚠️ Źródło pliku update — DWA pliki, nie pomyl", „⚠️ Pułapka: `immutable` cache na `/android/**`", „⚠️ `mini.verbigem.com` to TA SAMA webapp".

1. Podbić `versionCode`/`versionName` w `app/build.gradle.kts`. **Konwencja (zmieniona 2026-09-10): `versionCode` = numer patch w `versionName`** — code 64 → name `1.0.64`. (Stara, porzucona konwencja `1.0.(code-1)` rozjeżdżała numerki i została odrzucona.)
2. Build APK → `app/build/outputs/apk/debug/app-debug.apk`. Preferowana komenda (bezpośrednio przez wrapper Javy — w niektórych środowiskach `cmd.exe` jest blokowany i `gradlew.bat` nie przejdzie):
   ```bash
   JAVA_HOME="C:/Users/milo/.jdks/jbr-21.0.11" ANDROID_HOME="C:/Users/milo/AppData/Local/Android/Sdk" \
     "C:/Users/milo/.jdks/jbr-21.0.11/bin/java.exe" \
     -classpath "gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain \
     assembleDebug --console=plain
   ```
3. APK → `verbigem/mini/dist/android/app-debug-vN.apk` (N = nowy versionCode).
4. **`verbigem/mini/vite.config.ts`** (plugin `injectBuildId`) → `versionCode`, `versionName`, `apkUrl` = `https://mini.verbigem.com/android/app-debug-vN.apk?v=N` (**z cache-busterem `?v=N`**), `updatedAt`. Źródło prawdy dla przyszłych buildów Vite.
5. **`verbigem/mini/dist/updates/version.json`** — TE SAME wartości (to ten plik czyta appka). ⚠️ **Nie uruchamiaj `npm run build`**, chyba że celowo przebudowujesz webapp. Ręczna edycja jest bezpieczna wtedy i tylko wtedy, gdy krok 4 zrobiłeś identycznie — inaczej pierwszy `npm run build` cofnie `versionCode`.
6. `cd verbigem/mini && firebase deploy --only hosting --project mini-verbigem`. Przed deployem sprawdź `find dist -type f`, czy nie brakuje plików webappy.
7. Zweryfikuj: `curl -s https://mini.verbigem.com/updates/version.json` (**nie `/android/`!**) musi zwrócić nowy `versionCode` i `apkUrl` z `?v=N`. W razie wątpliwości `curl -s ".../android/app-debug-vN.apk?v=N" | sha256sum`.
   - Jeśli zmieniono reguły Firestore (np. nowa kolekcja): `cd verbigem/android && firebase deploy --only firestore:rules --project mini-verbigem`. App czyta `app_config/*` PRZED loginem (read:true), a zapisuje do `users/{uid}/history` i `users/{uid}/ocr_history` (oba `allow read,write` dla właściciela).
8. Test: zainstaluj starszy APK (niższy versionCode) → otwórz → dialog update → pobierz nowy.

## Repozytorium i zasady pracy

**Git:** `https://github.com/ihletru/verbigem_android` (branch `master`, prywatne). `.gitignore` wyklucza: `build/`, `llama_master/`, `*.gguf`, `model_probe/`, `build_log*`, `crash_log*`, `local.properties`, `.workbuddy-ai/`.

**Po każdej zmianie większej niż kosmetyczna: zaktualizuj README, potem commit + `git push`.**

Projekt `mini` (webapp + hosting) ma własne repozytorium (`verbigem-mini`, prywatne); deploy hostingu idzie przez `firebase deploy --project mini-verbigem`.
