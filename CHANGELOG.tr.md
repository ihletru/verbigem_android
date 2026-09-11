# Verbigem Android — değişiklik geçmişi

---

## v1.0.70 (2026-09-11) — versionCode 70

**OpenRouter anahtarı ve cüzdan bakiyesi artık hesaba ait.**

- İkisi de tüm telefon için ortaktı: ikinci bir hesapla giriş yaptığınızda ilk hesabın OpenRouter anahtarını ve bakiyesini görüyordu. Anahtar, model sağlayıcısındaki kişisel kotadır; bakiye ise hesap durumudur — ikisi de tıpkı geçmiş gibi hesabı takip etmelidir.
- Güncellemeden sonra giriş yaptığınız ilk hesap mevcut anahtarı ve bakiyeyi devralır; hiçbir şeyi yeniden girmeniz gerekmez. Sonraki hesaplar boş anahtarla başlar.
- Tema, arayüz dili, dil çifti ve seçili çevrimiçi model cihaz genelinde ortak kalır — bunlar uygulama ayarlarıdır, hesap verisi değil.

## v1.0.69 (2026-09-11) — versionCode 69

**Çeviri geçmişi artık telefonun değil, hesabın.**

- Şimdiye kadar bir telefonda tüm hesaplar aynı çeviri ve fotoğraf geçmişini görüyordu; başka bir hesapla giriş yapmak başkasının kayıtlarını görmeye yetiyordu. Geçmiş artık ayrı: her hesabın kendi geçmişi var ve yalnızca kendisiyle eşitleniyor.
- Bu güncellemeden sonra ilk giriş yaptığın hesap mevcut geçmişi devralır. Hiçbir şey kaybolmaz.

**Pro'daki sesli okuma simgesi sonunda ne yaptığını söylüyor.**

- «Oku (Pro)» düğmesinde okumayla ilgisi olmayan bir yıldız vardı. Artık üç dalgalı bir hoparlör var — ücretsiz sürümden bir fazla, yani Pro olduğu hemen belli.

**Düzeltildi: cüzdan yükleme açıklaması fazla söz veriyordu.**

- İletişim kutusu «1 USD = 1 USD bakiye» diyordu. Ödeme sağlayıcısının komisyonunu ve kendi marjımızı hesaba katmıyordu, yani vaat gerçek değildi. Bakiyenin ne için olduğu açıklaması kaldı, tutarlar düğmelerde.

**Profil'deki «Reklam tanılama» kartını kaldırdık.** Reklamlar Google Play'de yayınlanana kadar gösterilmeyecek, bu yüzden tanılama erkendi ve yalnızca yer kaplıyordu. Reklamlar gerçekten çalışmaya başlayınca geri dönecek.

**İletişim kutuları artık lavanta rengi değil.** Açık temada, uygulamanın geri kalanıyla uyuşmayan Material varsayılanı bir arka plan kullanıyorlardı.

## v1.0.68 (2026-09-11) — versionCode 68

**Web sitesinden indirilen sürümün artık kendi reklam birimi var.**

- Google Play sürümü ile web sitesinden indirilen sürüm AdMob'da iki ayrı uygulamadır. Şimdiye kadar tek bir kimlik paylaşıyorlardı, bu yüzden AdMob bir reklam isteğinin gerçekte nereden geldiğini bilemiyordu.
- Artık web sürümünün kendi uygulama kimliği ve kendi banner birimi var; böylece her iki sürümdeki reklamlar bağımsız olarak ölçülüp incelenebilir.

## v1.0.67 (2026-09-10) — versionCode 67

**Yeni: Profilde „Reklam teşhisi" kartı.**

- Hiç dolmayan bir banner sebep hakkında hiçbir şey söylemez: SDK başlamamış olabilir, izin verilmemiş olabilir ya da slotta kampanya yoktur. Kart bunu doğrudan ekranda gösterir: reklam SDK'sı çalışıyor mu, izin verildi mi, hangi birim yükleniyor ve son hatanın adı (örneğin „dolmama").
- **Google test reklamları** anahtarı. Google test birimi, hesap ayarımızdan bağımsız olarak her zaman dolar — o yüklenir de bizimki yüklenmezse sorun slotta veya AdMob hesabındadır.
- **AdMob hata ayıklama menüsü** düğmesi — telefonu bilgisayara bağlamadan SDK'nın birim hakkında ne dediğini görün.

## v1.0.66 (2026-09-10) — versionCode 66

**Düzeltildi: ödeme Paddle kasası yerine web sitemizi açıyordu.**

- Cüzdan veya «Reklamsız» paketi seçildiğinde tarayıcı ana sayfayı gösteriyor ve hiçbir şey olmuyordu — ödemeye ulaşmak mümkün değildi.
- Sebep: Paddle kasa bağlantısını kendi alan adınızdan oluşturur ve o sayfanın Paddle.js yüklemesini bekler. Ana sayfamız bunu yapmıyordu, bu yüzden kasanın açılacağı bir yer yoktu.
- Artık ödemeyi hemen açan ayrı bir kasa sayfası var; tarayıcıda kimse giriş yapmamış olsa bile.
- Cüzdan paketleri «300 / 500 / 1000 kredi» gösteriyordu, oysa bunlar sentti. Artık gerçek fiyatlar görünüyor: 3, 5 ve 10 USD (ödenen 1 USD = 1 USD bakiye), web sitesindekiyle aynı.

## v1.0.65 (2026-09-10) — versionCode 65

**Düzeltildi: bazı mesajlar seçilen arayüz dilini yok sayıyordu.**

- İngilizce seçiliyken bazı mesajlar hâlâ Lehçe görünüyordu (ve tersi). Bu; oturum açma hataları, çeviri, konuşma ve metin tanıma, hesap yükleme ve güncelleme penceresi için geçerliydi. Aynı kural artık model indirme mesajlarını, bildirim kanalı adını, bildirim eylem etiketlerini (Yanıtla, Okundu olarak işaretle) ve kişi davet ederken kullanılan e-posta konusunu da kapsıyor.
- Arayüz dili artık ilk ekranda değil, uygulama başlarken okunuyor. Bu, açılışta kısa süreli Lehçe görünmesini ortadan kaldırıyor ve uygulama arka planda çalışırken bile bildirim kanalı adının ve eylem etiketlerinin seçilen dilde olmasını sağlıyor.
- Nedeni: bu metinler çeviriyi sistem bağlamından alıyordu; sistem bağlamı uygulamada seçilen dili bilmez, telefonun dilini kullanır. Artık tüm bu noktalar tek ve doğru bir metin kaynağından geçiyor.
- Kitaplıklardan gelen teknik hatalar (örn. "HTTP 402") artık ekrana çıkmıyor; günlükte kalıyor ve kullanıcı seçilen dilde anlaşılır bir mesaj görüyor.
- Konuşma tanıma hatalarında kodun içine gömülü **on bir** Lehçe metin vardı (örn. "Ağ hatası", "Ses algılanmadı"). Artık hepsi çevirilerden geliyor ve arayüz dilini izliyor.
- Oturum açmada yanlış parola, kullanımda olan e-posta ve internet yokluğu artık Firebase'in İngilizce metni yerine kendi mesajlarına sahip.
- Güncelleme indirme hataları (eksik dosya, sunucu hatası, yükleyici hatası) da artık seçilen dilde.
- Aynı hata **tarih ve sayı biçimlendirmesine** de uzanıyordu: sohbet listesindeki gün kısaltmaları ("Pzt", "Sal") ve numara doğrulama ekranındaki algılanan ülke adı ("Poland" yerine "Polska") telefonun dilini alıyordu, arayüz dilini değil. Model boyutu telefonun ondalık ayırıcısını kullanıyordu ("2.9 GB" yerine "2,9 GB").
- Yeni mesaj bildiriminin yedek metni (push kendi metnini getirmediğinde kullanılır) de telefonun dilini alıyordu; artık diğer tüm mesajlarla aynı yoldan geçiyor.
- Firebase'den tanımadığımız bir hata kodu, kendi dilindeki mesaja kütüphaneden gelen İngilizce bir cümle ekliyordu. Artık bu ender durumda seçtiğin dilde bir cümle görüyorsun, teknik ayrıntı günlükte kalıyor.

## v1.0.64 (2026-09-10) — versionCode 64

**Düzeltildi: Google Play sürümünde yalnızca iki arayüz dili vardı.**

- Play'den yalnızca Lehçe ve İngilizce geliyordu; Almanca, İspanyolca, Türkçe ve Çince hiç yoktu — oysa kendi sitemizden indirilen sürümde altısı da çalışıyordu.
- Sebep: Google Play, AAB'yi daha küçük parçalara böler ve varsayılan olarak **dile göre** de böler — telefona yalnızca kendi dili ve yedek olarak İngilizce kurulur. Sitemizdeki APK her şeyi içeren tek bir dosyadır, bu yüzden orada sorun hiç görünmedi.
- Play sürümü için dil bölme kapatıldı. Birkaç yüz kilobayta mal oluyor, ama artık her dil her telefonda çalışıyor.

**Düzeltildi: Model indirme penceresi yalan söylüyordu ve Lehçe konuşuyordu.**

- Hızlı modeli indirdikten sonra Kesin modeline geçildiğinde, o model telefonda hiç olmadığı hâlde yeşil „Kesin modeli kullanıma hazır!" mesajı çıkıyordu. Uygulama son indirmenin durumunu alıp yeni modelin adını ona ekliyordu.
- İndirme penceresi seçilen arayüz dilini yok sayıyordu: arayüz İngilizceyken pencere hâlâ Lehçe görünüyordu. Sistem pencerelerinin içindeki metinlere uygulamada seçilen dilin açıkça verilmesi gerekir; bu pencere bunu yapmıyordu.
- Uygulamadaki **sekiz pencere daha** aynı hataya sahipti (Profıl'deki onaylar, Kişiler'deki kanal seçimi, sohbetteki fotoğraf önizlemesi, güncelleme pencereleri). Hepsi seçilen dil yerine telefonun dilinde açılıyordu; artık ortak ve doğru bir sarmalayıcı kullanıyorlar.
- İndirme koduna sabit yazılmış Lehçe metinler (bellek yetersiz, yer yetersiz, sunucu hatası) kaldırıldı — seçilen dilden bağımsız olarak Lehçe görünürlerdi.
- Süresi dolmuş SMS kodu artık kendi mesajına sahip. „Bu, gönderdiğimiz koda benzemiyor" hem yazım hatası hem de süresi dolmuş kod anlamına geliyordu; oysa bu iki durum tamamen farklı tepki gerektirir.
- Kodu girme süresi 60 saniyeden 120 saniyeye çıkarıldı. SMS daha yavaş ulaştığında eski 60 saniye kod yazılamadan doluyor ve tamamen doğru bir kod reddediliyordu.

## v1.0.63 (2026-09-10) — versionCode 63

**Düzeltildi: uygulama açılışta çöküyordu.**

- Android 14 ve üzerinde uygulama, açılıştan hemen sonra çöküyordu: siyah ekran ve hata mesajı, içeri girmek mümkün değildi.
- Sebep: ana ekrandaki bir alan, sistem uygulamayı bağlamayı bitirmeden çok erken oluşturuluyordu. Eski Android sürümleri buna izin veriyordu; yenileri hata veriyor.
- Artık bu alan yalnızca gerçekten gerektiğinde oluşturuluyor. Google Play derlemesinde hiç oluşturulmuyor, çünkü orada kullanılmıyor.
- Not: bu sürümden itibaren sürüm numarası derleme numarasıyla aynı (63 = 1.0.63). Önceden bir fark vardı.

## v1.0.60 (2026-09-10) — versionCode 61

**Build: derleme/hedef SDK API 36 (Android 16) seviyesine yükseltildi.**

- Google Play, 2026-08-31'den itibaren yeni uygulamaların en az API 36'yı hedeflemesini zorunlu kılıyor; `compileSdk` ve `targetSdk` 35'ten 36'ya yükseltildi.
- Kodda değişiklik yok; yalnızca derleme hedefi. Son kullanıcılar fark görmez.

## v1.0.59 (2026-09-10) — versionCode 60

**Düzeltildi: «Imported 5 contact», «1 mutual friends».**

- Sayı içeren üç mesaj, sayı kaç olursa olsun aynı şeyi söylüyordu: beş kişilik bir dosya içe aktarıldığında «Imported 5 contact», tek ortak arkadaşta ise «1 mutual friends» yazıyordu.
- Bu üç mesaj artık her dilin çoğul kurallarına uyuyor. Lehçe 1, 2–4 ve 5+ için ayrı biçimler kullanıyor («1 kontakt», «3 kontakty», «10 kontaktów»); Türkçe ve Çince tek biçimde kalıyor, çünkü bu diller sayıdan sonra ismi çekimlemiyor.
- Elle yazılmış bir «1 ise … değilse» yerine Android'in çoğul kaynakları kullanıldı; böylece ileride yeni bir dil eklemek kod değişikliği gerektirmiyor.


## v1.0.58 (2026-09-10) — versionCode 59

**Düzeltildi: her şey yolunda gibi davranan iki yer.**

- Kaydedilemeyen bir sohbet silme işlemi hiçbir işaret vermiyordu: gelen kutusuna dönüyordunuz ve sohbet hâlâ oradaydı, üstelik nedenini söyleyen hiçbir şey yoktu. Telefon artık işlemin başarısız olduğunu söylüyor.
- Başarısız bir kişi araması (çevrimdışı veya izin yok) tıpkı «böyle bir kullanıcı yok» gibi görünüyordu: sadece boş bir liste. Arama kutusunun altında artık isteğin başarısız olduğu yazıyor. Boş liste yeniden yalnızca «kimse bulunamadı» anlamına geliyor.
- Mesajlar uygulamanın geri kalanı gibi 6 dilde.


## v1.0.57 (2026-09-10) — versionCode 58

**Düzeltildi: ikinci mesaj «gönderiliyor» durumunda takılı kalabiliyordu.**

- Önceki mesaj hâlâ gönderilirken yeni bir şey gönderdiğinde (art arda iki fotoğrafta tipik), yeni mesaj bir sonraki tetikleyiciyi bekliyordu — ağın dönmesini ya da yeni bir gönderimi. Artık kuyruk hemen ardından kendini bir kez daha boşaltıyor.
- Read Pro ses üretemediğinde hiçbir şey yapmıyordu: ne ses ne açıklama. Artık başarısız olduğunu açıkça söylüyor.


## v1.0.56 (2026-09-10) — versionCode 57

**Düzeltildi: bir şey ters gittiğinde mikrofon sessiz kalıyordu.**

- Mikrofon izni olmaması, telefonda ses tanıma bulunmaması ve tanıma hatası yalnızca sistem günlüğüne bir satır yazıyordu. Senin için üçü de aynı görünüyordu: mikrofon «sadece çalışmıyor», sebep belirtilmeden.
- Artık bu üç durumun her biri ne olduğunu doğrudan söylüyor — yazma alanının üzerinde kısa bir mesajla. Metinler, uygulamanın geri kalanı gibi 6 dilde.


## v1.0.55 (2026-09-10) — versionCode 56

**Düzeltildi: gönderilemeyen fotoğraf ya da sesli mesaf iz bırakmadan kayboluyordu.**

- Normal bir metin mesajı yerel kuyruğa girer: ağ düştüğünde kırmızı bir «gönderilmedi» balonu ve yanında yeniden dene düğmesi görürsün. Fotoğraflar ve sesli mesaf bu kuyruğun dışından gidiyordu; hata olduğunda ne balon ne de uyarı vardı. Mesaf sadece kayboluyor, geriye sistem günlüğünde tek bir satır kalıyordu.
- Artık fotoğraflar ve sesli mesaf metinle aynı kuyruktan gidiyor: balon hemen görünüyor (telefonun hafızasındaki küçük resimle ya da transkriptle) ve gönderim başarısız olursa tıpkı metin mesafında olduğu gibi «gönderilmedi» ve «yeniden dene» çıkıyor. Yeniden denemek fotoğrafı tekrar seçmeni istemiyor.
- Tek seferlik yerel veritabanı geçişi gerektirir (v9 → v10). Hiçbir şey kaybolmaz.


## v1.0.54 (2026-09-10) — versionCode 55

**Düzeltildi: hata mesajları uygulama diline bakılmadan Lehçe (veya İngilizce) çıkıyordu.**

- Bazı hataların —giriş, sohbet modunda çeviri, fotoğraftan metin tanıma, Pro Okuma— metni doğrudan kodun içine yazılmıştı. Uygulama İngilizce, Almanca, İspanyolca, Türkçe ya da Çince olsa bile Lehçe bir cümle görünüyordu, örn. «Błąd logowania».
- Artık tüm hata mesajları arayüzün geri kalanıyla aynı kaynaklardan geliyor, yani 6 dilde de var.
- Bu arada tüm yerelleştirmeyi denetledik: 482 metin, 6 dilin hiçbirinde eksik yok.

## v1.0.53 (2026-09-09) — versionCode 54

**Yeni: tarayıcı (OCR) ekranında artık motor seçimi var.**

- Şimdiye kadar tarayıcı, çeviri ekranında ne seçersen seç, her zaman Hızlı modeliyle çeviriyordu. Metin alanının üstünde artık aynı seçim çubuğu var: ⚡ Hızlı, 🎯 Doğru, ⚖️ İkisi, ☁️ Çevrimiçi.
- Seçim çeviri ekranıyla ortak: bir kez ayarlıyorsun, ikisinde de geçerli oluyor.
- ⚖️ İkisi, iki sonucu alt alta gösterir ve her biri kendi etiketini taşır (⚡ Hızlı / 🎯 Doğru).
- Seçilen modelin ağırlıkları indirilmemişse tarayıcı, çeviri ekranındaki indirme penceresini gösterir — ana ekrana dönmen gerekmez.

**Düzeltildi: tarayıcı sonuç başlığı her dilde «(Hızlı)» diyordu.**

- Sonucun üstündeki etikete «Hızlı» sabit yazılmıştı; İngilizce, Almanca, İspanyolca ve Çince sürümlerde bile Lehçe bir sözcük kalıyordu. Artık seçilen motorun adı yazılıyor.
- Çeviri ekranındaki **Çevir (X)** düğmesi artık model boyutunu içeren uzun açıklama yerine motorun kısa adını gösteriyor (Hızlı / Doğru / İkisi / Çevrimiçi).

## v1.0.52 (2026-09-09) — versionCode 53

**Düzeltildi: ücretsiz hesaplarda reklamlar hiç görünmüyordu.**

- Google'ın onay istemediği hesaplarda (AEA ve Birleşik Krallık dışı), onay akışı „reklam istenemiyor" diyebiliyordu — genelde „Privacy & messaging" yapılandırılmamış, yeni onaylanmış bir AdMob hesabında. Reklam SDK'sı o zaman hiç gelmeyecek bir onayı bekliyor ve banner sonsuza kadar boş kalıyordu. Artık uygulama 3 saniye sonra reklamları kendisi başlatıyor.
- AEA ve Birleşik Krallık'ta bir şey değişmiyor: onayın olmadan reklam yüklenmez — Google'ın kurallarını ihlal etmiyoruz.
- Ayrıca loglarda artık onay durumunun tamamı ve reklam yüklenmezse hata kodu var — boş bir banner'ın nedeni daha kolay anlaşılır.

**Düzeltildi: model indirme diyaloğu ve Çevir düğmesi, hangi model seçilirse seçilsin „Hızlı" diyordu.**

- „Modeli indir" penceresi Hızlı modele (~440 MB) göre yazılmıştı. Doğru modeli (~1,1 GB) indirirken başlıkta „Hızlı", altında „~1,1 GB" görünüyordu — çelişkili bilgi.
- Başlık, gövde, indirme düğmesi, ilerleme etiketi ve „model hazır" etiketi artık parametrik ve gerçekten indirdiğiniz modeli yansıtıyor.
- Ana çeviri ekranındaki **Çevir (X)** düğmesi artık gerçekten seçtiğiniz motoru gösteriyor — Hızlı, Doğru, İkisi veya Çevrimiçi — ve her zaman „Hızlı" değil.

## v1.0.50 (2026-09-09) — versionCode 51

**Düzeltildi: PRO hesapları tekrar giriş yapabiliyor.**

- Cloud Function, `noAdsUntil` alanını `Firestore Timestamp` olarak yazıyordu, Android ise düz bir sayı (ms) bekliyordu. Profil okunurken uygulama „Failed to convert a value of type com.google.firebase.Timestamp to long" hatasıyla çöküyor ve **PRO hesapları giriş yapamıyordu**. `UserProfile` artık iki biçimi de kabul ediyor; yeni yazımlar `number` olarak yapılıyor.

## v1.0.49 (2026-09-09) — versionCode 50

**Ücretsiz sürümde reklamlar ve izinler üzerinde tam kontrol.**

- Çeviri ekranında artık bir **Google reklam banner'ı** gösteriliyor. PRO 💎 hesaplar (reklam kaldırma satın alımı veya cüzdan bakiyesi > 0) reklam görmez — onlar için hiçbir şey değişmiyor.
- AEA, Birleşik Krallık ve İsviçre'de ilk açılışta bir **Google izin formu** gösteriyoruz. Reklamlar yalnızca sen karar verdikten sonra yüklenir, asla önce değil.
- Profilindeki **Gizlilik** kartına **Reklam gizliliği ayarları** ekledim — iznini istediğin zaman değiştirebilir ya da geri çekebilirsin.
- Reklamları Google sunar. Çevirdiğin metinler reklam ağına hiç ulaşmaz: çeviri cihazında yapılır.

## v1.0.48 (2026-09-08) — versionCode 49

**PRO durumu artık sabit kaydedilmiyor, hesaplanıyor.**

- Hesap yalnızca aktif reklam kaldırma satın alımı (gelecekte `noAdsUntil`) veya cüzdan bakiyesi > 0 ise PRO'dur.
- `noAdsUntil` boş cüzdanla dolduğunda hesap Free'e döner. `plan` alanı yalnızca bilgilendirmedir.

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
