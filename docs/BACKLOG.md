# Seyir backlog

Bu dosyada kullanıcının istekleri ve testlerde bulunan sorunlar tutulur. Biten maddeler işaretlenir, silinmez. Son güncelleme: 2026-11-22 (MetadataCatalog bellek içi anlık snapshot/decode önbelleği — TMDB puan/tür filtreli listede detaydan dönüşte "0 öğe" yanıp sönmesi giderildi; sürüm 0.2.17).

## P0 — Şu an açık

- [x] **D-pad (sağ/sol/yukarı/aşağı) odak geçişleri öngörülebilir değil** (2026-11-22, TV'de doğrulandı): Sol menü (sağ ile tek basışta içeriğe geçiş ve kapanış), Ana Sayfa, Canlı TV (`Ara`/`Kayıtlar`/`Sırala`/kategoriler/kanal listesi/`İzle` + `.focusGroup()`), Filmler/Diziler (üst çubuk/kategoriler/ızgara ilk öğe), Bölüm araması (doğrudan arama kutusu → ilk sonuç) ve Dizi Detay (bilgi/sezon/bölüm deterministik geçişleri) düzeltilip TCL TV'de 7 akışla doğrulandı.
- [x] **Sol menüde öğeler üst üste biniyor** (düzeltildi, TV'de doğrulandı 2026-11-22) (TV, kullanıcı geri bildirimi). Menü kapalıyken seçili Ana Sayfa göstergesi logoya değiyor. Menü açıkken öğeler üstte sıkışıyor, altta boş alan kalıyor.
  - Başlanan düzeltme (`HomeScreen.kt`, commit'lenmedi, TV'de doğrulanmadı):
    - Zorla verilen 42 dp yükseklik kaldırıldı; öğeler drawer'ın kendi 56 dp yüksekliğini kullanıyor.
    - Öğeler arası 8 dp boşluk var.
    - Bölümler dikeyde ortalandı.
  - Not: Bu yapıyla release derlenip TV'ye `install -r` başlatıldı, ancak kullanıcı işlemi yarıda kesti. TV'de hangi sürümün kurulu olduğu belirsiz.
- [x] **Canlı yayın ve film/dizi oynatıcısı ayrılmalı** (2026-11-22, TV'de doğrulandı): `LiveControls` (`Kanal listesi`, `Kaydet`, `Ses ve altyazı`, `Diğer`, EPG şimdi/sonra; zaman çubuğu/hız/"baştan başla" yok) ve `VodControls` (zaman çubuğu, `Ses ve altyazı`, korumalı `Önceki`/`Sonraki bölüm`) ile `PlayerSettingsPanel` canlı/VOD menüleri ayrı akışlara oturtuldu.
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
- [x] **Performans ince ayar (2026-11-22):**
  - Room yazmaları ve sorguları (`ContentDb`, `EpgDatabase`, `MetadataDb`) normal öncelikli `arch_disk_io` yerine `THREAD_PRIORITY_BACKGROUND` öncelikli `BackgroundWork.dbExecutor` (`seyir-db-1..3`) havuzuna taşındı; katalog/EPG yenilenirken 60 Hz arayüz kareleri kesintiye uğramıyor.
  - Her kurulum sonrası `cmd package compile -m speed-profile -f dev.iptv.player` çalıştırılıyor.
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
- [x] **Varsayılan ses ve altyazı** (2026-11-22, TV'de doğrulandı): Ses varsayılanı "Orijinal dil", altyazı varsayılanı "Türkçe" olarak uygulandı; gerçek içeriklerde (bkz. aşağıdaki TV e2e notları) ve Ayarlar → Oynatıcı ekranında doğrulandı.
- [x] **Ayarlar ekranı:** (2026-11-22, TV'de doğrulandı) Tüm satırlardaki `< değer >` okları kaldırılıp açılır liste (`ListPickerDialog` + `ArrowDropDown`) yapısına geçildi; Sol tuş değer kaydırmak yerine kenar menüye dönüyor, Sağ/OK tuşu ise seçili sekmenin hizasındaki rastgele satır yerine her zaman sayfanın en üst satırına (`SafeFocusRequester`) odaklanıyor. Teknik oynatıcı ayarları (`Canlı yayın akış biçimi`, `Tampon süresi`, `User-Agent`, yeniden bağlanma, harici oynatıcı, kare hızı) ve `EPG` ayarları yeni **Gelişmiş** sekmesi altında gruplandı.
- [x] **Oynatıcı ayarları paneli:** (`PlayerSettingsPanel.kt`, `PanelRow` / `checkSlot`) Menü satırları sol ikonlarıyla hizalandı; yalnızca seçim işaretli alt listelerde tik alanı ayrılıyor, ana menüde boş ikon boşluğu bırakılmıyor.
- [x] **Canlı oynatıcıda Geri tuşu:** (2026-11-22, TV'de doğrulandı) `onPreviewKeyEvent` `Key.Back` akışı paneller ve açık kontroller (`panel → kontroller → çıkış`) için birleştirildi; `Kanal listesi` → `Geri` → `Kanal listesi` odağı → `Geri` → kontrollerin tek basışta kapanması → `Geri` → çıkış ve doğrudan panel (`DPAD_LEFT`) kapatmada hayalet kontrol çıkmaması TV'de doğrulandı.

## P1 — Özellikler ve doğruluk

- [x] **Dizi oynatıcısında "Önceki bölüm" ve yanlışlıkla basmayı önleme** (2026-11-22, TV'de doğrulandı): `VodControls` sırası `Duraklat` → `Ses ve altyazı` → `Önceki bölüm` → `Sonraki bölüm` olarak düzenlendi; iki bölüm düğmesi de 4 sn pencereli çift basış onayı (`Tekrar bas: Önceki` / `Sonraki` kehribar vurgu + odak ayrılınca sıfırlama) ile korunuyor. Tek basışta geçmediği, odak ayrılınca sıfırlandığı ve çift basışta önceki bölüme geçtiği TV'de doğrulandı.
- [x] **TMDB eşleştirmesi — gerçek örnek (2026-11-22, TV'de doğrulandı):** Panelin kendisi "Göz Açıp Kapayıncaya Kadar (2021)" için yanlış TMDB verisi (`74 dk`, Okan Bayülgen / 957152) dönse bile gerçek oynatma süresi (`WatchHistory.durationMs` = `1 sa 34 dk`) ve süre doğrulamalı yıl/yılsız TMDB aramasıyla "In the Blink of an Eye" (2026, Bilim-Kurgu, Dram, TMDB `1032892`) doğru eşleştirildi.
- [x] **TMDB eşleştirmesi (2026-11-22, doğrulandı):** Xtream liste/info yanıtlarındaki `tmdb_id`/`tmdb` alanı `Stream`/`SeriesInfo`/`VodInfo` ve Room (`ContentDb` v2, `MetadataDb` v2 `MIGRATION_1_2`) katmanlarına eklendi; birebir TMDB ID eşleşmesi, süre tutmazsa başlık/süre doğrulamasına düşme ve `withCatalogMetadata` ile listelerde eksik poster/backdrop/yıl tamamlama uygulandı. Not: "Farouk Omar" dizisinde sağlayıcı `tmdb_id` göndermediği için (`Dram, Belgesel`, özetsiz Xtream verisi) bu örnekte yine başlık eşleşmesine düşüyor.
- [x] **Son seçilen kategori ve sıralama, uygulama yeniden açıldığında hatırlanmalı (Canlı/Film/Dizi)** (2026-11-22, TV'de doğrulandı): `SettingsStore` (`browse_prefs_json` / `BrowseMemory`) üzerinden profil ve playlist bazında (`Filmler`, `Diziler`, `Canlı TV`) son seçilen kategori + sıralama kaydedilip açılışta geri yükleniyor (`force-stop` sonrası TV'de doğrulandı); ayrıca `visibleEntryIndex` + `SafeFocusRequester` ile hızlı kategori/sıralama geçişindeki `FocusRequester is not initialized` çökmesi giderildi.
- [x] Kategori gizleme, çoklu (2026-11-22, Claude, TV'de doğrulandı): Ayarlar → Kategoriler. Bölüm seçilir (Canlı TV / Filmler / Diziler), her kategori için aç/kapa, "Tümünü göster / Tümünü gizle" tek yazımla, "x / y kategori görünüyor" sayacı. Yetişkin adlar "yetişkin içeriği gizle" açıkken listelenmiyor.
- [x] "Gruplarım" (2026-11-22, TV'de uçtan uca doğrulandı): kanala uzun bas → "Gruba ekle" → grup seç (işaretli = içinde; seçmek ekler/çıkarır) ya da "Yeni grup…" ile ad ver. Gruplar Canlı TV'de Favoriler'in altında, kanallar ekleniş sırasıyla. Gruba uzun bas → **Adı değiştir**, **Yukarı taşı** / **Aşağı taşı**, **Grubu sil** (TV'de iki grupla yukarı taşıma, yeniden adlandırma ve silme doğrulandı). Profile özel; playlist silinince o listenin kanalları gruplardan düşer; yedekleme/geri yüklemeye (`BackupData.groups` + `ChannelGroups.merge`) dahil edildi.
- [x] **Tam ekran canlı yayın bilgi paneli (IBO/v2 tarzı)** (2026-11-22, TV'de doğrulandı): `LiveControls` yerel `EpgService.nowAndNext` (ve `shortEpg` yedeği) üzerinden anlık ŞİMDİ/Sonra + program açıklaması, `Favori ekle/çıkar`, `Kaydet`, `Ses ve altyazı`, `Kanal listesi` ve yeni **Rehber** (`LiveInfoPanel`, `KEYCODE_INFO` veya `Rehber` düğmesi) paneliyle tamamlandı; panel açıkken ◀/▶ ile kanal değiştirme, tam EPG akışı/açıklaması, gelecek programlara hatırlatıcı kurma ve Geri ile `Rehber` düğmesine odak dönüşü TV'de doğrulandı.
- [x] Catch-up gün listesi (2026-11-22, Claude): "Geçmiş yayınlar" penceresinde üstte gün sekmeleri (Bugün, Dün, Cmt 21…), altında o günün programları yeniden eskiye. Gün sınırı yerel saate göre (birim testli). TV'de arşivli kanal bulunamadığı için görsel olarak doğrulanmadı.
- [x] **Canlı yayın akış formatı seçimi (TS/HLS)** (2026-11-22, TV'de doğrulandı): **Ayarlar → Gelişmiş → Canlı yayın akış biçimi** (`MPEG-TS (.ts)` / `HLS (.m3u8)`, `Setting.LiveStreamFormat` + `applyLiveStreamFormat`) ile Xtream canlı yayın bağlantıları katalog yenilemeye gerek kalmadan oynatıcıda, canlı önizlemede, çoklu izlemede ve harici oynatıcıda seçilen biçimle açılıyor.
- [x] İzleme geçmişinden seçerek silme: zaten vardı (kart üzerinde uzun bas → "Geçmişten kaldır"; toplu silme "Son İzlenenler" kategorisinde).
- [x] Liste/ızgara geçişi (2026-11-22, Claude, TV'de doğrulandı): Filmler/Diziler başlığında "Liste / Izgara" düğmesi. Liste: iki sütun satır kart (afiş, ad, yıl · puan · tür, konu, ilerleme). Seçim kalıcı. Ayrıca alt bilgi satırında ve listede "(2021) · 2021" yıl tekrarı giderildi.
- [x] Kanal logoları (2026-11-22, TV'de doğrulandı): Canlı TV listesinde ve Ana Sayfa 16:9 kanal kartlarında sağlayıcı logosu yoksa ya da yüklenemezse önce XMLTV EPG `<channel><icon src="..."/>` (`EpgChannel.icon` / `EpgService.channelIcon`) logosu deneniyor, o da yoksa soluk kutuda baş harfler gösteriliyor.
- [x] **Android TV "Play Next" satırında logosu olmayan içerikler boş kart olarak görünüyor. Yedek görsel kullanılmalı** (2026-11-22, birim testle doğrulandı): `WatchNextPublisher` (`resolveWatchNextPoster`) logosu eksik içeriklerde `android.resource://.../banner` (16:9) yedeğine düşüyor.

## P2 — Platformlar

- [ ] Telefon: aynı uygulamada dokunmatik için optimize düzen.
- [ ] Windows masaüstü uygulaması (Compose Multiplatform + VLC önerisi).

## Teknik ve altyapı

- [ ] Release keystore tanımlanmalı (şu an release, debug anahtarıyla imzalanıyor).
- [x] CI: eylemler güncel ana sürümlere taşındı (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7; Node 20 uyarıları gitti).
- [x] `docs/QA-2026-11-21.md` içindeki eskimiş satır düzeltildi (`OWNER` yer tutucu notu GitHub repo bağlantısıyla güncellendi).
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
- [x] Altyazı/ses paneli Geri ile kapanınca odak paneli açan düğmeye değil "Duraklat"a dönüyor. (2026-11-22, TV'de doğrulandı: `VodControls` ve `LiveControls` `PlayerControlFocus` ile paneli açan düğmeye dönüyor.)
- [x] Arama sonuçlarında aşağı tuşu ilk sonuca değil, geometrik olarak en yakın sonuca gidiyor. (2026-11-22, TV'de doğrulandı: `Search` satırına `Aşağı` ile sonuç #0 odaklanıyor.)
- [x] Bölüm aramasını menüden açınca odak bazen arama kutusuna değil sol menüye düşüyor (Filmler → Ara). (2026-11-22, TV'de doğrulandı: `Filmler → Ara` doğrudan arama kutusuna odaklanıyor.)
- [x] Oynatıcıdan Geri: aramadan veya devam et satırından açılan dizi bölümünde oynatıcıdan Geri ile dizi detay sayfasına (`Kaldığın yerden devam et`), oradan Geri ile önceki ekrana/aramaya dönülüyor (2026-11-22, TV'de doğrulandı).
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
- [x] Oynatıcı v2 (film/dizi) uygulandı — 2026-11-22, TV'de doğrulandı ("Bölümler" sağ yan paneli çoklu sezon seçici, oynatılıyor göstergesi ve tam dizi kuyruğuyla eklendi; `Inter Tight` + `Inter` yazı tipi ailesi `res/font` altına gömülüp `IptvTheme` genelinde etkinleştirildi ve TV'de doğrulandı). Kalan: sararken küçük resim önizleme (trick-play görselleri sağlayıcıda yok).
- [x] Canlı yayın kontrolleri v2 diline geçti (2026-11-22, TV'de doğrulandı): kanal kimliği + CANLI rozeti, şu anki program ilerlemesi ve "Sonra", oynat/duraklat (timeshift), Kanal listesi, Kaydet, Ses-altyazı (seçenek varsa), Diğer. Eski kontrol çubuğu ve SeekBar silindi.
- [x] Canlı: aktif kontroller açıkken ipucu `◀▶ gezin · Geri gizle` olarak güncellendi (`OK kontroller · ▲▼ kanal · ◀ liste` pasif bilgi şeridinde kalıyor); kanal listesi paneli (`ChannelListPanel`) ve canlı program rehberi/bilgi paneli (`LiveInfoPanel`) v2 diline taşınıp TV'de doğrulandı.
- [x] Ana Sayfa v2 (2026-11-22, TV'de doğrulandı): kenardan kenara arka plan görseli (yalnızca sağ üst alan, önbellekli katman), odaklanan içeriği izleyen başlık bloğu (★ puan, yıl, tür, 2 satır özet), "İzlemeye devam et" için 16:9 kartlar + kehribar ilerleme. Performans aynı senaryoda: eski %5 / 28 ms → yeni %8 / 23 ms ortanca.
- [x] Ana Sayfa (2026-11-22, TV'de doğrulandı): `TitleLogoOrText` + `pickTmdbLogo` ile hero bloğunda şeffaf TMDB başlık logosu (`Cheetahs Up Close` üzerinde TV'de doğrulandı), `Canlı TV` satırında logoyu kapatmayan 16:9 v2 kanal kartları (`ChannelCard`: ortalanmış logo veya soluk baş harf rozeti, odakta `CANLI` etiketi, kart altında kanal adı — TV'de doğrulandı) ve sol çekmece menünün (`ModalNavigationDrawer`) yerini alan ortalanmış üst hap menü (`TopNavPill` / `TopNavIconPill`: solda `Seyir · playlist`, ortada `Ana Sayfa / Canlı TV / Filmler / Diziler` hap barı, sağda `Yenile / Playlistler / Ayarlar` ikon hapları; içerikten `Yukarı`, `Geri` veya sol kenardan `Sol` ile doğrudan aktif sekmeye odaklanma — TV'de doğrulandı).
- [x] Detay v2 tamamlandı (2026-11-22, TV'de doğrulandı): beyaz ana düğme / yarı saydam diğerleri, TMDB logo başlık (`TitleLogoOrText` — Film Detay `The Sparks Brothers` ve Dizi Detay `The Last of Us`), kehribar puan + `MetaLine`, temiz bölüm adları + TMDB `/season/{n}` 16:9 bölüm görselleri ve süreleri (`The Last of Us` üzerinde TV'de doğrulandı).
- [x] Uygulama genelinde odak beyaz (yazı siyah), seçim/ilerleme kehribar: kartlar, kategoriler, açılır listeler, oynatıcı panelleri, sezon/bölüm satırları (2026-11-22).
- [x] Canlı TV v2 (2026-11-22, TV'de doğrulandı): sağ üst 16:9 arka plan önizleme + önbellekli karartma katmanı (`RESIZE_MODE_FILL` ile dikey taşma/çizgi düzeltildi), üst başlık satırı, beyaz odaklı kanal satırları + CANLI rozeti, sağ alt ŞİMDİ/Sonra bloğu ve eylem düğmeleri. Ölçüm: kanal listesi %9,45 / 16 ms, kategoriler %28,22 / 42 ms.
- [x] Logo ve ikon v2 (2026-11-22, TV'de doğrulandı): eski göz ikonu tamamen kaldırıldı; koyu obsidyen-antrasit squircle içinde kehribar sinema ekranı çerçevesi + yuvarlatılmış fildişi-altın oynat prizması ve `Inter Tight ExtraBold` (`-0.025` tracking) `Seyir` wordmark tasarlandı. Uygulama içi `BrandMark` (`28dp`/`30dp`/`44dp`/`48dp`/`56dp`), adaptive launcher ikonu (`ic_launcher_background.xml` + `ic_launcher_foreground.xml`) ve 640×360 TV banner'ı (`drawable-xhdpi/banner.png`) güncellenip TV'de doğrulandı.
- [x] Bölüm adları (2026-11-22): sağlayıcı ad vermiyorsa TMDB sezon bölüm adları kullanılıyor; Türkçe ad "1. Bölüm" gibi genel bir adsa İngilizce gerçek ad. TMDB eşleşmesi yoksa "Bölüm N" gösteriliyor. Örnek: sağlayıcıdaki "Farouk Omar" TMDB'de bulunmuyor (TMDB'deki karşılığı "Hz. Ömer", id 58887); eşleşmesi için sağlayıcının tmdb_id alanı gerekli (P1).
- [~] Otomatik Türkçe altyazı (2026-11-22, Claude): gömülü altyazıda tercih edilen dil yoksa OpenSubtitles'tan en çok indirilen eşleşme bir kez otomatik iniyor. Arama artık TMDB kimliğiyle yapılıyor (film: tmdb_id; bölüm: dizi kimliği + sezon + bölüm), başlıkla arama yalnızca yedek. Elle altyazı seçildiyse otomatik indirme devreye girmiyor. TV'de denenen iki filmde de gömülü TR olduğu için otomatik indirme henüz gerçek bir içerikte doğrulanmadı.
- [x] Aynı dilden iki altyazı (2026-11-22, Claude): tekrarlanan etiketlere sırayla SDH, farklıysa biçim (SRT/PGS/ASS…), son çare #1/#2 ekleniyor. Birim testli; TV'de çift Türkçe altyazılı içerikte henüz görülmedi.
- [x] Canlı oynatıcı (2026-11-22, Claude): "Favori ekle/çıkar" düğmesi eklendi; oynatıcıdaki kanal listesinde (çoklu izleme dahil) her kanalın şu anki programı ve ilerlemesi görünüyor. Panel ayırıcı sahte kanallar ("∞∞∞∞ TURK ULUSAL ∞∞∞∞") Canlı TV'de gizleniyor. Sürüm 0.2.12 (kod 32).
- [x] Kanal listesindeki program bilgisi TV'de doğrulandı (şu anki program ve ilerleme çubuğu görünüyor).
- Benchmark notu: Spectre (com.flextv.livestore) aynı TV'de 50 saniyeden uzun süre açılış ekranında kaldı; Seyir 1-2 saniyede açılıyor.
- [x] Ses-altyazı panelinde kumanda gezintisi (2026-11-22, Claude, TV'de doğrulandı): ▶ ile bir sonraki sütunun seçili öğesine, ◀ ile önceki sütunun seçili öğesine gidiliyor; boyut satırından sola basınca artık "Sade beyaz" yerine seçili altyazıya dönüyor.
- [x] OpenSubtitles TMDB kimliğiyle arama TV'de doğrulandı (Cheetahs Up Close: birebir EN/TR sonuçlar). Kimlik biliniyorsa başlıkla yedek arama yapılmıyor (önceden alakasız bir Sırp dizisi geliyordu). Elle indirilen altyazı artık otomatik seçiliyor ve hemen ekranda görünüyor (TV'de doğrulandı).
- [x] Canlı TV odak görünürlüğü (2026-11-22, Claude, TV'de doğrulandı): kategoride OK zaten listeye geçiriyor (gezinme doğruydu); asıl sorun "İzle" düğmesinin odakta değilken de beyaz olmasıydı. Canlı TV'de artık ikincil renkte, yalnızca odaklanınca beyazlaşıyor. Film/dizi sayfasında birincil beyaz kaldı.
- [x] Canlı oynatıcı kontrol çubuğu bulunamıyordu (2026-11-22, kullanıcı): kanal değişince beliren çubuk odaklanmıyor, ipucu OK'den bahsetmiyordu. İpucu artık "OK kontroller · ▲▼ kanal · ◀ liste". ▼ bilerek kanal değiştirmeye devam ediyor (art arda kanal gezme bozulmasın diye).
- [x] CI kırmızıydı (2026-11-22): lint 4 hata veriyordu (androidx kısıtlı sabitleri, produceState yanlış alarmı). Düzeltildi, CI yeşil.
- [x] Üst düğmeler (Ara, Kayıtlar) odakta kırmızı oluyordu; artık uygulamanın geri kalanı gibi beyaz zemin, koyu yazı (2026-11-22, TV'de doğrulandı).
- [x] Ayarlar ekranı design v2'ye taşındı (2026-11-22, TV'de doğrulandı): odaklı satır beyaz zemin + koyu yazı, açık anahtarlar ve seçili değerler amber. Uygulamadaki mavi "Primary" rengi amber oldu (diyalog düğmeleri, giriş alanları, çoklu izleme çerçevesi, rozetler); amber üstündeki yazılar koyu.
- [x] EPG başlıklarında HTML kodları (2026-11-22, TV'de doğrulandı): "0---gt;100" artık "0--->100". Ayrıştırırken ve veritabanından okurken çözülüyor (eski kayıtlar da düzelir); iki kez kodlanmış ve "&" kaybolmuş biçimler dahil, harften sonra gelen "gt;" dokunulmuyor (birim testli).
- [x] Uzun basma menüsü (2026-11-22, TV'de doğrulandı): odak artık ilk seçenekte; menüyü açan uzun basışın tuştan kalkışı yok sayılıyor (yeni bir OK gerekiyor), böylece yanlışlıkla seçim olmuyor. Silme onaylarında odak Vazgeç'te.
