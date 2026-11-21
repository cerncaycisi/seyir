# Seyir

Android TV için içerik sağlamayan, açık kaynaklı IPTV/medya oynatıcı. / An open-source Android TV media player that ships no content.

## Türkçe

**Seyir** (izleme), kendi M3U veya Xtream oynatma listelerinizi ekleyerek kullandığınız bir oynatıcıdır. Hiçbir kanal, film ya da yayın adresi içermez.

### Özellikler
- M3U ve Xtream Codes listeleri, canlı TV / film / dizi bölümleri
- Android TV için D-pad dostu Compose arayüzü
- Media3 (ExoPlayer) ile HLS ve yaygın formatlar
- İsteğe bağlı TMDB ile film/dizi bilgileri, OpenSubtitles ile altyazı
- Reklam ve analitik yok; veriler yalnızca cihazda

### Ekran görüntüleri
_Yakında / coming soon (`docs/screenshots/`)._

### Derleme
Gereksinimler: JDK 17, Android SDK 35.

```
./gradlew testDebugUnitTest assembleDebug
./gradlew assembleRelease     # R8 açık; keystore.properties yoksa debug anahtarıyla imzalanır
./gradlew bundleRelease       # App Bundle (.aab) gerekirse
```

Sürüm kodu Git geçmişinden bağımsızdır (şu an `20`). Yeni dağıtımlarda `app/build.gradle.kts` içindeki varsayılanı artırın veya `-PappVersionCode=21` kullanın; kurulu sürümden daha düşük bir kod seçmeyin.

`local.properties` (git'e girmez) isteğe bağlı anahtarlar:
```
tmdb.apiKey=...
opensubtitles.apiKey=...
```
Release imzası için `keystore.properties.example` dosyasını `keystore.properties` olarak kopyalayın.

### Test sunucusu
Gerçek sağlayıcı olmadan denemek için sahte Xtream sunucusu: `tools/mock-xtream.ps1` (PowerShell).

Android regex regresyonlarını bağlı emülatörde çalıştırmak için:
```
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```
Bu ayar `gradle.properties` içinde de açıktır: test sonunda uygulama ve verileri korunur. Ayarı kapatmayın; AGP varsayılan temizliği uygulamayı verileriyle birlikte kaldırır.

### Yasal not
Uygulama içerik sağlamaz. Yalnızca yasal olarak erişim hakkınız olan içeriği ekleyin; sorumluluk kullanıcıya aittir. Bkz. [docs/DISCLAIMER.md](docs/DISCLAIMER.md), [docs/PRIVACY.md](docs/PRIVACY.md).

## English

**Seyir** ("watching" in Turkish) is a player for your own M3U or Xtream playlists. It contains no channels, movies or stream URLs.

### Features
- M3U and Xtream Codes playlists; live TV, movies, series
- D-pad friendly Compose for TV interface
- Media3 (ExoPlayer) playback, HLS and common formats
- Optional TMDB metadata and OpenSubtitles subtitles
- No ads or analytics; data stays on the device

### Screenshots
_Coming soon (`docs/screenshots/`)._

### Build
Requires JDK 17 and Android SDK 35.

```
./gradlew testDebugUnitTest assembleDebug
./gradlew assembleRelease     # R8 enabled; falls back to debug signing without keystore.properties
./gradlew bundleRelease       # Android App Bundle (.aab) if needed
```

The version code is independent of Git history (currently `20`). For each new distribution, increase the default in `app/build.gradle.kts` or pass `-PappVersionCode=21`; use a code no lower than the installed version.

Optional keys in `local.properties` (gitignored): `tmdb.apiKey`, `opensubtitles.apiKey`.
For signed releases copy `keystore.properties.example` to `keystore.properties`.

### Mock server
Try the app without a real provider using the fake Xtream server: `tools/mock-xtream.ps1` (PowerShell).

Run the Android regex regressions on a connected emulator:
```
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```
This setting is also enabled in `gradle.properties` to retain the app and its data after testing. Keep it enabled: AGP's default cleanup uninstalls the app and deletes its data.

### Legal
No content is provided. Only add content you are legally entitled to access; you are solely responsible for it. See [docs/DISCLAIMER.md](docs/DISCLAIMER.md) and [docs/PRIVACY.md](docs/PRIVACY.md).

## License
Apache-2.0, see [LICENSE](LICENSE). Contributing: [CONTRIBUTING.md](CONTRIBUTING.md).

This product uses the TMDB API but is not endorsed or certified by TMDB. Subtitle data provided by OpenSubtitles.com.
