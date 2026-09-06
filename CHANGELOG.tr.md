# Verbigem Android — değişiklik geçmişi


> ⚠️ `v1.0.1`–`v1.0.3` etiketleri **erken tarihli derlemelerdir** (versionCode 2–3).
> Güncel sürüm **v1.0.39** (versionCode 40).
> Sürüm `app/build.gradle.kts` içinde tutulur (`versionCode` / `versionName`).

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
