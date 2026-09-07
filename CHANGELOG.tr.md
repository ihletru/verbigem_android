# Verbigem Android — değişiklik geçmişi


> ⚠️ `v1.0.1`–`v1.0.3` etiketleri **erken tarihli derlemelerdir** (versionCode 2–3).
> Güncel sürüm **v1.0.42** (versionCode 43).
> Sürüm `app/build.gradle.kts` içinde tutulur (`versionCode` / `versionName`).

---

## v1.0.42 (2026-09-06) — versionCode 43

**Yardım artık devre dışı kontrollerde de çalışıyor.**

- `Modifier.helpClickable(enabled = false)` `enabled` değerini
  `combinedClickable`a iletiyordu; bu **uzun basmayı da** kapatır — bu yüzden
  soluk görünen kontrolde yardım penceresi sessizce kayboluyordu. Artık
  `enabled` yalnızca eylemi engelliyor: `combinedClickable` her zaman
  `enabled = true` alıyor, koruma `onClick = { if (enabled) onClick() }` içinde.
- Etkilenenler: alan boşken „Çevir" düğmesi, ücretsiz hesapta motor simgeleri
  (kesin / her ikisi / çevrimiçi), fotoğraf yokken OCR düğmeleri.
- README: yardım penceresi tuzakları listesine yeni 10. madde eklendi.

## v1.0.41 (2026-09-06) — versionCode 42

**Genel yardım penceresi kuralından (v40) sonra UI düzeltmeleri.**

- **"Anladım"** yardım penceresinde artık arayüz dilini izliyor (önce Lehçe kalıyordu). `HelpWindow`, `Dialog{}` öncesinde `LocalContext` öğesini yakalayıp `CompositionLocalProvider` ile geri veriyor.
- **Alt çubuk 5 simgeye döndü** (Çevirmen, Konuşma, Sohbet, Kişiler, Profil). Altıncı OCR konumu çubuğu dolduruyordu.
- **Çevirmen**: "Çevir" düğmesi klavyenin üstüne kayar (`bringIntoViewRequester` + `LaunchedEffect(WindowInsets.isImeVisible)` ile `delay(250)`).
- **Motor simgeleri (hassas, ikisi, çevrimiçi)** devre dışı olsalar bile uzun basışta yardım gösterir.
- **Konuşma ve OCR**: gereksiz altyazılar kaldırıldı — "?" düğmesi sayfayı zaten açıklıyor.
- **Kişiler → Telefondan**: "Kişilerimde arkadaş bul" ve ".vcf içe aktar" artık yardıma sahip (uzun basış).
- **Profil**: arayüz dili seçici çerçeveli; gizlilik kartının altında sürüm + **Yenilikler** bağlantısı olan **Hakkında** kartı.
- **Uygulama simgesi**: adaptif simge arka planı yeşilden (#2C6B85) krem rengine (`CalmDayBg` #F7F5F1). PNGler beş yoğunlukta 108/162/216/324/432 px (Lanczos).

---

## v1.0.40 (2026-09-06) — versionCode 41

**Genel arayüz kuralı: simgeye dokun = işini yap, basılı tut = yardım penceresi.**

- **Uygulamadaki her simge** artık kendini açıklıyor: ne olduğunu, ne yaptığını ve nasıl
  kullanıldığını. `ui/components/HelpDialog.kt` içinde yeni ortak altyapı (`HelpWindow`,
  `helpClickable`, `HelpIconButton`, `HelpFramedIconButton`, `QuestionMarkButton`, `ScreenHeader`).
- **Her ekran başlığı**: ateşböceği logosu (saydam) + başlık + sağda tüm sayfayı açıklayan
  bir **„?"** düğmesi.
- **Çevirmen**: iki dil seçici ve değiştirme simgesi için yardım; kısa başlıklı dört motor
  (hızlı / doğru / her ikisi / çevrimiçi) ve kapsamlı pencereler — motorların altındaki eski
  açıklamalar **kaldırıldı**; mikrofon / kamera / kamera Pro artık **çerçeveli** ve
  „sesten" / „fotoğraftan" / „fotoğraftan pro" başlıklı; Çevir düğmesi, geçmiş ve sonuç
  kartlarındaki beş simge ve alt çubuk için yardım.
- **Sohbet (Konuşma)**: logo + „?" (konuşmanın hiç kaydedilmediği ve cihazdan hiç çıkmadığı
  notu dahil), dil alanları, değiştirme, mikrofon ve gönder düğmesi için yardım.
- **OCR artık alt çubukta bir girişe sahip** — çubukta altı sekme var (gezinmesi olmayan tek
  ekrandı).
- **Kişiler**: Arkadaşlar / Davetler / Telefondan / Harici, **11.sp metnin üzerinde simge**
  olarak (alt çubuktaki gibi) ve yardım pencereleriyle.
- **Sohbet, Profil, QR kodum, telefon doğrulama** — „?" başlıkları ve simgelerde yardım.
- **6 dilde ~50 yeni yardım metni** (394 anahtar, hiçbir dilde eksik yok).

---

## v1.0.39 (2026-09-05) — versionCode 40

v1.0.3'ten bugüne kadarki tüm geliştirmelerin özeti.

**Yenilikler**

- **1:1 sohbet + Kişiler (Aşama 1–3)**
  - Gelen kutusu, sohbet başlığı, Kişiler içinde sekmeler (TabRow, 4 sekme).
  - `.vcf` içe aktarma (kendi parser'ımız, sıfır bağımlılık), mesajlarda arama, «Tanıyor olabilecekleriniz» (`suggestFriends`), telefon numarasıyla davet.
- **Telefon numarası ve SMS doğrulama (Aşama 2.4 / 2.6)**
  - Anlaşılır Phone Auth hataları, SMS bölgesi açıldı (**ALL** — tüm dünya).
  - Çökme düzeltmeleri: «no activity» (v37), «SMS gönder»e dokununca çökme (v38), anlaşılır hatalar (v39).
- **FCM push bildirimleri** (Aşama 2): Cloud Functions + FCM, App Check (HMAC sırrı), `matchContacts`.
- **Fotoğraflar ve OCR** (Aşama 5): tam ekran fotoğraf önizlemesi + yükleme ilerlemesi, sohbette OCR, canlı STT transkripsiyonu.
- **QR kodlar** (Aşama 4): QR kodum (ZXing), GMS Code Scanner, App Links + `assetlinks.json`.
- **Firefly markalaması**: saydam launcher simgesi (v40), logo, Play Store yayın planında App Check adımı.
- **Gizlilik**: 6 dilde gizlilik politikası, `READ_CONTACTS` için belirgin açıklama.
- **6 dil** (pl, en, es, zh, de, tr); metin denetimi ×6 (257 anahtar, eksik yok).
- **Cloud Functions çalışma zamanı**: Node 20 → nodejs22 (7 Android fonksiyonu).

---

## v1.0.3 — versionCode 3

GitHub raw (`master`) üzerinden otomatik güncelleme, arayüz düzeltmeleri: yıldız yerine Pro hoparlör,
ücretsiz OCR'un yanında Pro OCR, klavyenin üzerinde gizli menü.

## v1.0.2

Reaktif senkronizasyon, kırmızı çöp kutusu, free kullanıcılar için ipuçlu Pro hoparlör,
Pro OCR + OCR geçmişi, klavye artık Çevirmen'i kapatmıyor.

## v1.0.1 — versionCode 2

Otomatik güncelleme test derlemesi. Pro Oku / Sil simgeleri, Firestore senkronizasyonu,
