# Seyir backlog

Bu dosyada kullanıcının istekleri ve testlerde bulunan sorunlar tutulur. Biten maddeler işaretlenir, silinmez. Son güncelleme: 2026-11-22.

## P0 — Şu an açık

- [x] **Sol menüde öğeler üst üste biniyor** (düzeltildi, TV'de doğrulandı 2026-11-22) (TV, kullanıcı geri bildirimi). Menü kapalıyken seçili Ana Sayfa göstergesi logoya değiyor. Menü açıkken öğeler üstte sıkışıyor, altta boş alan kalıyor.
  - Başlanan düzeltme (`HomeScreen.kt`, commit'lenmedi, TV'de doğrulanmadı):
    - Zorla verilen 42 dp yükseklik kaldırıldı; öğeler drawer'ın kendi 56 dp yüksekliğini kullanıyor.
    - Öğeler arası 8 dp boşluk var.
    - Bölümler dikeyde ortalandı.
  - Not: Bu yapıyla release derlenip TV'ye `install -r` başlatıldı, ancak kullanıcı işlemi yarıda kesti. TV'de hangi sürümün kurulu olduğu belirsiz.
- [ ] **Canlı yayın ve film/dizi oynatıcısı ayrılmalı** (kullanıcı). İki oynatıcının arayüzü ve ayarları farklı olmalı.
  - Canlı yayın: kanal listesi, EPG şimdi/sonra, kanal değiştirme, kayıt, ses ve altyazı. Zaman çubuğu, hız ve "baştan başla" olmamalı.
  - Film/dizi: aranabilir zaman çubuğu, sonraki bölüm, hız, altyazı stili/boyutu, içerik bilgisi.
  - Ayar panelinde de yalnızca o türe uygun seçenekler görünmeli.
- [ ] **Görsel kimlik ve "sözde modern" arayüz** (kullanıcı). Mevcut arayüz temiz ama sıradan, hazır açık kaynak arayüzü gibi, gerçekten modern değil. Logo da beğenilmiyor.
  - Yeni logo ve marka: isim, renk, tipografi.
  - Cesur bir tasarım dili: katmanlı derinlik, büyük görseller, hareket ve geçişler, odak efektleri.
  - Netflix, Apple TV ve Prime Video referans alınacak; kopyalanmayacak.
  - Önce birkaç tasarım önerisi hazırlanıp kullanıcıya onaylatılacak.
- [x] **Canlı TV performansı** (2026-11-22 TV'de ölçülüp düzeltildi):
  - Tekrar ölçümde Canlı TV kategori kaydırması %0,4-0,8 geciken kare (10 ms). Daha önceki %19, o sırada arka planda çalışan EPG/katalog işlerinden kaynaklanıyordu.
  - Düzeltmeler:
    - EPG indirme/işleme, playlist yenileme, TMDB işçisi ve katalog uzlaştırma düşük öncelikli `seyir-bg` iş parçacıklarına taşındı.
    - Pencere arka planı kaldırıldı; tüm ekranın iki kez boyanması bitti.
    - Release manifest'ine `profileable` eklendi.
  - Katalog yenilenirken Filmler'de kaydırma: %3,8 geciken kare.
  - Bulgu: APK dosyadan kurulunca ART derlenmemiş (`status=verify`) çalışıyor; derleme cihaz boştayken yapılıyor. `cmd package compile -m speed-profile` sonrası Ana Sayfa'da geciken kare %5,6'dan %1,6'ya düştü. TV'ye adb ile kurulumdan sonra bu komut çalıştırılmalı.
- [ ] **Performans ince ayar:**
  - Room yazmaları (`arch_disk_io`) yenileme sırasında normal öncelikte yaklaşık 1 çekirdek kullanıyor. Yazma ağırlıklı veritabanları için düşük öncelikli executor düşünülebilir.
  - Uygulamaya özel baseline profile (macrobenchmark) eklenebilir.
- [ ] (Eski ölçüm) **Canlı TV performansı (gerçek TCL TV, 0.2.3, release):**
  - Ölçümler:
    - Canlı TV'ye geçiş: medyan 38 ms, %21 geciken kare, %99'luk dilim 150 ms.
    - Kategori listesinde kaydırma: medyan 48 ms, %19 geciken kare. Ana iş parçacığı 32 karede yavaş kaldı.
    - Kanal listesinde kaydırma: medyan 24 ms, %10 geciken kare.
    - Karşılaştırma: Ana Sayfa'da kaydırma %1,6 geciken kare.
  - Şüpheliler:
    - Yan panelde eşzamanlı oynayan önizleme videosu (zayıf işlemci).
    - Her satırda EPG `produceState` çağrısı.
    - `LiveBrowser`'ın her odak değişiminde yeniden çizilmesi.
    - 1408 kanallık liste.
  - Yapılacaklar: manifest'e `profileable` ekleyip simpleperf/Perfetto ile profil almak. Gerekirse önizlemeyi ayardan kapatılabilir yapmak.
  - Cihaz: TCL Smart TV Pro, rtd6748, armeabi-v7a, 1,8 GB RAM, Android 12, 60 Hz.
- [ ] **Varsayılan ses ve altyazı** (kullanıcı, 2026-11-22): ses varsayılan olarak içeriğin orijinal dili, altyazı varsayılan olarak Türkçe olmalı. Kullanıcı bir içerikte değiştirirse o seçim korunmalı.
- [ ] **Ayarlar ekranı:** satırlar hâlâ "< değer >" şeklinde değer çeviriyor (Dil, Varsayılan bölüm, Ses/Altyazı dili, Altyazı stili…). Kullanıcı açılır liste istedi. "User-Agent" ve "Tampon süresi" gibi teknik ayarlar "Gelişmiş" altına taşınmalı. Rehber kaldırıldığı halde ayarlarda "EPG" sayfası duruyor; adı/yeri gözden geçirilmeli.
- [ ] **Oynatıcı ayarları paneli:** satırların solunda boş ikon alanı var. İkon eklenmeli ya da boşluk kaldırılmalı.
- [ ] **Canlı oynatıcıda Geri tuşu:** panel → kontroller → çıkış sırasında kaç basış gerektiği doğrulanmalı. "Geri: kontrolleri gizle" sonrasında kontroller hâlâ görünüyordu.

## P1 — Özellikler ve doğruluk

- [ ] **TMDB eşleştirmesi — gerçek örnek (2026-11-22):** Panelde "Göz Açıp Kapayıncaya Kadar (2021)" adıyla duran yayın aslında "In the Blink of an Eye" (2026, İngilizce, 94 dk). Başlık ve yıl eşleştirmesi bunu Türk belgeseli (TMDB 957152) sanıyor. Sağlayıcının `tmdb_id` alanı ve süre karşılaştırması kullanılmalı.
- [ ] **TMDB eşleştirmesi:** sağlayıcının `tmdb_id`'si (Xtream vod/series info) varsa birebir eşleşme için kullanılmalı. Listelerde eksik posterler de tamamlanmalı. (Codex'in 0.2.2/0.2.3 eşleştirme işi kontrol edilecek.)
- [ ] Son seçilen kategori ve sıralama, uygulama yeniden açıldığında hatırlanmalı (Canlı/Film/Dizi).
- [ ] Kategori gizleme: çoklu seçim (canlı/film/dizi). "Gruplarım": kullanıcının kendi grupları.
- [ ] Tam ekran canlı yayın bilgi paneli (IBO tarzı): logo, şimdi/sonra, EPG, kilit, favori, altyazı, ses. Sağ/sol tuşları ile kanal değişimi.
- [ ] Catch-up için gün listesi.
- [ ] Canlı yayın akış formatı seçimi (TS/HLS).
- [ ] İzleme geçmişinden seçerek silme.
- [ ] Liste/ızgara görünümü arasında geçiş.
- [ ] Kanal logoları: sağlayıcı vermediğinde baş harf gösteriliyor. EPG logosu veya başka bir kaynak düşünülebilir.
- [ ] Android TV "Play Next" satırında logosu olmayan içerikler boş kart olarak görünüyor. Yedek görsel kullanılmalı.

## P2 — Platformlar

- [ ] Telefon: aynı uygulamada dokunmatik için optimize düzen.
- [ ] Windows masaüstü uygulaması (Compose Multiplatform + VLC önerisi).

## Teknik ve altyapı

- [ ] Release keystore tanımlanmalı (şu an release, debug anahtarıyla imzalanıyor).
- [ ] CI: `setup-java` v5'e geçilmeli.
- [ ] `docs/QA-2026-11-21.md` içindeki eskimiş satır düzeltilmeli.
- [ ] Test notu: TV'de video katmanı ekran görüntüsüne yansımıyor. Oynatma durumu `dumpsys media_session` ile doğrulanmalı.

## Bitenler (son)

- [x] Geri tuşu: içerik → menü → Ana Sayfa → çıkış onayı (0.2.1).
- [x] Menüde ve kategoride OK'a basınca odak içeriğe geçiyor. Detay sayfasından dönünce odak eski yerine geliyor (0.2.1).
- [x] Bölüm geçişlerinde 1-2 saniyelik donmalar (arka planda hesaplama) ve `focusRestorer` kaynaklı çökme (0.2.2).
- [x] Codex 0.2.2/0.2.3:
  - Rehber kaldırıldı.
  - Kayıtlar Canlı TV'nin altına taşındı.
  - Her bölüme kendi araması geldi.
  - Film türleri ve TMDB puanı eklendi.
  - Açıklamanın tamamını okuma ekranı eklendi.
  - Aranabilir zaman çubuğu eklendi.
  - Altyazı panelleri tek Geri ile kapanıyor.
  - Duraklatınca içerik bilgisi görünüyor.
  - Arka planda katalog/TMDB indeksi eklendi.
  - 5 altyazı stili eklendi.

## TV e2e notları (2026-11-22, ses/altyazı)

- [x] Varsayılan ses "Orijinal dil", altyazı "Türkçe" (yeni ayar anahtarları `audio_lang_v2` / `subtitle_lang_v2`). TV'de doğrulananlar:
  - The Last of Us S1B1: TR + EN 5.1 sesten EN seçildi. Altyazıda forced yerine tam TR seçildi.
  - The Batman (2022): ilk parça TR dublaj; orijinal EN seçildi, tam TR altyazı.
  - Indian Police Force S1B1: 10'dan fazla ses parçasından Hintçe (orijinal) seçildi, TR altyazı.
  - Werewolf Castle: tek EN parça, TR altyazı.
  - "Göz Açıp Kapayıncaya Kadar (2021)": panelde başlık yanlış (bkz. TMDB maddesi).
- [ ] Altyazı/ses paneli Geri ile kapanınca odak paneli açan düğmeye değil "Duraklat"a dönüyor.
- [ ] Arama sonuçlarında aşağı tuşu ilk sonuca değil, geometrik olarak en yakın sonuca gidiyor.
- [ ] Bölüm aramasını menüden açınca odak bazen arama kutusuna değil sol menüye düşüyor (Filmler → Ara).
- [ ] Oynatıcıdan Geri: aramadan açılan dizi bölümünde dönüş arama sonuçlarına değil dizi sayfasına gidiyor (beklenen olabilir, değerlendirilecek).
- [ ] İsteğe bağlı: gömülü TR altyazı yoksa OpenSubtitles'tan otomatik TR altyazı.

## Tasarım v2 (2026-11-22) — tasarım panosu: https://claude.ai/artifact/Eji8f93DBxFTPLMfsR8Nue

Kullanıcı ilk üç yönü "ucuz, profesyonel değil" diye reddetti. Onun yerine tek yön hazırlandı:
- Koyu, beyaz ağırlıklı arayüz; tek vurgu rengi kehribar (#F2B544); yazı tipi Inter Tight + Inter.
- TMDB arka planları ve başlık logoları kullanılıyor.

Oynatıcı v2, benchmark sonrası şu kurallarla kuruldu:
- Zaman çubuğu ana kontrol; önizleme yalnızca sararken görünür.
- Ses, altyazı ve altyazı görünümü tek panelde; panel açıkken video durmaz.
- İçerik bilgisi yalnızca duraklatınca gösterilir.
- "Girişi atla" ve "Sonraki bölüm" bağlama göre çıkar; "22:56'da biter" bilgisi var.

Kullanıcı kararları:
- Oynat/duraklat düğmesi kalsın.
- Dizide "Sonraki bölüm" olsun.
- Filmde ve canlı yayında önceki/sonraki düğmesi olmasın; −10/+10 düğmesi olmasın.

Logo ayrıca çalışılacak; şu an sade bir göz-oynat işareti.
- [ ] Oynatıcı v2'yi uygula
- [ ] Ana Sayfa v2'yi uygula
- [ ] Detay v2'yi uygula
- [ ] Canlı TV v2'yi uygula
- [ ] Logo ve ikon
