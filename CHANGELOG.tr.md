# Verbigem Android — değişiklik geçmişi

---

## v1.0.47 (2026-09-08) — versionCode 48

**„Reklamları kaldır" satın aldıktan sonra hesap durumu.**

- Satın aldığında **hesap PRO 💎 olur**. Sadece reklam banner'ı kaybolmakla kalmaz, Pro özellikler de açılır (bazıları hesabı önce hâlâ Free görüyordu).
- Durum kartı artık **cüzdan bakiyesini her zaman** gösteriyor — hiç doldurmadıysan **0.00** görürsün. Reklamları kaldırmak ile cüzdan doldurmak iki ayrı ödemedir.
- Altında yeni bir **reklamların geri dönmesine kalan süre sayacı** var — satın aldığın dönemden kaç gün kaldığını hemen görürsün.

## v1.0.46 (2026-09-08) — versionCode 47

**Reklamları kaldır — tek seferlik ön ödeme, abonelik değil.**

- Hesap durumu kartında, **Cüzdanı doldur**'un yanında artık **Reklamları kaldır** düğmesi var. Güvenli bir tek seferlik ödeme açıyor: bir süre seçiyorsun ve ödemeden sonra reklam banner'ı o süre boyunca gizli kalıyor.
- Dört seçenek: **$1 · 1 ay**, **$3 · 3 ay**, **$5 · 5 ay**, **$10 · 10 ay**.
- Bu bir **abonelik değil** — bir kez ödüyorsun, reklamlar seçtiğin süre boyunca gizli kalıyor ve hiçbir şey kendiliğinden yenilenmiyor. **Hesabın PRO 💎 olur.**
- Hesap durumu kartında artık **cüzdan bakiyesi** de görünüyor — hiç doldurmadıysan **0.00** yazar (reklamları kaldırmak kredi eklemez, bu ayrı bir ödeme) — ve ayrıca **reklamların geri dönmesine kalan süre**, yani satın aldığın dönemden kaç gün kaldığı.

## v1.0.45 (2026-09-08) — versionCode 46

**Düzeltildi: ücretli çevrimiçi çeviri ve cüzdan doldurma.**

- Verbigem'in sunucusu (projenin bölgesiyle uyumlu olarak) başka bir bölgeye taşındı. Uygulama hâlâ eski adresi çağırıyordu, bu yüzden **ücretli çevrimiçi modellerle çeviri hata veriyordu** — şimdi yeniden çalışıyor.
- **Cüzdanı uygulamadan doldurmak hiç çalışmıyordu** — düğme aynı nedenle ödeme sayfasını açamıyordu. Düzeltildi; satın alma sonrası krediler otomatik ekleniyor.

## v1.0.44 (2026-09-07) — versionCode 45

**Kendi OpenRouter anahtarını kullanmak artık daha kolay.**

- Çevrimiçi model seçim kartı artık **Varsayılan çevrimiçi çeviri modeli** adını taşıyor; böylece çevrimiçi çevirilerde kullanılan modeli ayarladığı hemen anlaşılıyor.
- **Kendi OpenRouter anahtarın** kartında (kendi anahtarınla ücretsiz modeller) artık anahtar oluşturma sayfasına giden tıklanabilir bir bağlantı var — kartın altındaki açıklamada da yardım penceresinde de. OpenRouter'a kaydolduktan sonra tek bir dokunuşla anahtar oluşturmaya gidersin.

## v1.0.43 (2026-09-07) — versionCode 44

**Hesabını doğrudan uygulama içinden doldur.**

- Hesap durumu kartında artık bir **Cüzdanı doldur** düğmesi var. Güvenli bir ödeme açar ve ödemeyi yaptıktan sonra kredileri otomatik olarak cüzdanına ekler — yenileme yok, elle bir şey yazmana gerek yok.
- Seçilebilecek üç paket: Küçük (300 kredi), Orta (500 kredi) ve Büyük (1000 kredi).
- Cüzdan bakiyen başarılı ödemeden hemen sonra arka planda kendiliğinden güncellenir.

## v1.0.42 (2026-09-06) — versionCode 43

**Yardım artık devre dışı düğmelerde de çalışıyor.**

- Bir düğmeyi uzun basılı tutmak, düğme soluk görünse bile açıklamasını gösterir — örneğin boş bir metin alanı, ücretsiz hesapta kilitli motorlar veya okunacak fotoğraf yokken.

## v1.0.41 (2026-09-06) — versionCode 42

**Yardım pencerelerinden sonra görünüm düzeltmeleri.**

- Yardım penceresindeki „Anladım" düğmesi artık her zaman seçtiğin arayüz dilinde.
- Alt çubuk tekrar 5 simgeye döndü: Çevirmen, Konuşma, Sohbet, Kişiler, Profil.
- Çevirmen ekranı yazarken klavyenin üstüne kayar.
- Motor simgeleri (kesin, ikisi, çevrimiçi) ücretsiz hesapta bile yardımı gösterir.
- Profil'e sürüm numarası ve **Yenilikler** bağlantısı içeren **Uygulama hakkında** kartı eklendi.
- Uygulama simgesi artık sayfaların temasına uyan krem rengi bir arka plana sahip.

## v1.0.40 (2026-09-06) — versionCode 41

**Uygulamadaki her simgenin artık bir açıklama penceresi var.**

- Bir simgeye dokunmak görevini yapar; uzun basmak ise ne olduğu, ne yaptığı ve nasıl kullanılacağına dair bir açıklama açar.
- Bu, Çevirmen, Konuşma, Sohbet, Kişiler ve Profil ekranlarını kapsar — 6 dilde onlarca yeni açıklama.

## v1.0.39 (2026-09-05) — versionCode 40

**Şu ana kadarki en büyük yenilik paketi.**

- 1:1 Sohbet ve Kişiler: davetler, kayıtlı kişileri içe aktarma (.vcf dosyaları) ve arkadaş bulma.
- Telefon numarası SMS doğrulaması dünyanın her bölgesinde çalışır, hata mesajları açıktır.
- Yeni mesajlar için push bildirimleri (FCM).
- Sohbette tam ekran önizlemeli fotoğraflar ve görüntülerden metin okuma (OCR).
- Arkadaş eklemek için kendi QR kodların ve bir kod tarayıcısı.
- 6 dilde gizlilik politikası ve izinlerin açık bir açıklaması.
- Uygulama 6 dilde çalışır: Lehçe, İngilizce, İspanyolca, Çince, Almanca ve Türkçe.
