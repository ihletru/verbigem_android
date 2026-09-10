# R8 / ProGuard — Verbigem Android
#
# Po co ten plik, skoro minifikacja jest WYŁĄCZONA:
#   app/build.gradle.kts → buildTypes.release { proguardFiles(..., "proguard-rules.pro") }
# odwołuje się do tego pliku, a pliku w repo NIE BYŁO. Przy isMinifyEnabled = false
# AGP go nie czyta, więc build przechodzi — ale to mina z opóźnionym zapłonem:
# wystarczy włączyć minifikację (Play przy 38 MB APK prosi o to w pierwszej
# kolejności), żeby dostać "Proguard configuration file does not exist" albo —
# gorzej — ciche wycięcie klas używanych wyłącznie przez odbicie.
#
# Zasada: trzymamy TYLKO to, co znika przez odbicie albo przez JNI. Zwykły kod
# Kotlin/Compose nie potrzebuje żadnego -keep, a każdy nadmiarowy -keep to
# większy APK. Nowa klasa czytana przez toObject() ląduje w pakiecie modeli,
# więc jest objęta regułą poniżej — nie trzeba nic dopisywać.

# ---------------------------------------------------------------------------
# JNI — llama.cpp
# ---------------------------------------------------------------------------
# `external fun` muszą zachować nazwę i sygnaturę: kod natywny szuka ich po
# nazwie (GetMethodID). Bez tego: NoSuchMethodError dopiero na telefonie.
-keep class com.verbigem.app.jni.LlamaNativeBridge { *; }

# generateNativeStreaming(..., callback) — z C++ wołane jest
# GetMethodID("onToken", "(Ljava/lang/String;)V"). Interfejs i metoda muszą
# przeżyć; Kotlin nie może zamienić tego w lambdę (zob. komentarz w
# LlamaNativeBridge.TokenStreamCallback).
-keep interface com.verbigem.app.jni.LlamaNativeBridge$TokenStreamCallback { *; }

# ---------------------------------------------------------------------------
# Firestore — toObject(X::class.java)
# ---------------------------------------------------------------------------
# Firestore mapuje pola po nazwie przez odbicie. Wycięte pole = null w obiekcie
# i zero błędów w logach — najgorszy możliwy objaw, bo wygląda na "brak danych".
# Trzymane są też konstruktory, bo Firestore ich potrzebuje do deserializacji.
# Cały pakiet, nie pojedyncze klasy: modeli jest kilkanaście i ciągle przybywa,
# a koszt (kilka małych data class) jest pomijalny.
-keep class com.verbigem.app.data.model.** { *; }

# ---------------------------------------------------------------------------
# Gson (OnlineApiEngine)
# ---------------------------------------------------------------------------
# Używany tylko z JsonObject (typ Gsona, ma własne reguły) — żadnego modelu
# aplikacji nie przepuszczamy przez refleksję Gsona, więc nie ma czego trzymać.
# Gdyby kiedyś doszło gson.fromJson(..., MójModel::class.java), trzeba dopisać
# -keepclassmembers dla tej klasy, bo pola znikną po minifikacji.

# ---------------------------------------------------------------------------
# Room — celowo BEZ -keep
# ---------------------------------------------------------------------------
# Encje i DAO są używane przez kod wygenerowany w KSP, nie przez odbicie.
# Room waliduje schemat po migracji (czyta DDL z wygenerowanej klasy), ale
# robi to swoim kodem, więc -keep nie jest potrzebne. Jeśli po włączeniu
# minifikacji migracja zacznie się sypać — dopisz tu kom.verbigem.app.data.local.**
