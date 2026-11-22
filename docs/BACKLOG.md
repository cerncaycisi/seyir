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
- [ ] **Canlı TV performansı (gerçek TCL TV, 0.2.3, release):**
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
- [ ] **Oynatıcı ayarları paneli:** satırların solunda boş ikon alanı var. İkon eklenmeli ya da boşluk kaldırılmalı.
- [ ] **Canlı oynatıcıda Geri tuşu:** panel → kontroller → çıkış sırasında kaç basış gerektiği doğrulanmalı. "Geri: kontrolleri gizle" sonrasında kontroller hâlâ görünüyordu.

## P1 — Özellikler ve doğruluk

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
