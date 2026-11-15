# NovaStore

**F-Droid formatında, açık kaynak, kendi uygulamaların için bir mobil mağaza.**

NovaStore, bir *depo (repository)* istemcisidir: `index-v1.json` okur, uygulamaları
listeler/araman/ister, APK'yı **SHA-256 özeti doğrulanarak** indirir ve sistem kurulum
ekranıyla kurar. Kendi uygulamalarını `tools/mkindex.py` ile indeksleyip herhangi bir
**statik dosya sunucusunda** yayınlayabilirsin — sunucu tarafında çalışma, veritabanı veya
hesap sistemi gerekmez.

| | |
|---|---|
| Uygulama adı | **NovaStore** |
| Paket adı | `dev.novastore.app` |
| Depo formatı | F-Droid `index-v1.json` (v30000) |
| Min / Hedef SDK | 26 (Android 8.0) / 37 |
| Lisans | MIT (`LICENSE`) |

## Özellikler

- **Katalog**: kategoriye göre ızgara, anlık arama, uygulama detayı (açıklama, ekran
  görüntüleri, izinler, lisans, kaynak kod bağlantıları)
- **Sürüm geçmişi**: depodaki tüm sürümleri listeler, istediğin sürümü kurabilirsin
- **Kurulum akışı**: indirme ilerlemesi → SHA-256 doğrulama → sistem kurulum ekranı
  (bilinmeyen kaynak izni tek seferlik istenir)
- **Güncellemeler**: cihazda kurulu sürüm ile depodaki en yeni sürüm karşılaştırılır;
  "Güncelle / Aç / Kaldır"
- **Çoklu depo**: Ayarlar'dan birden fazla depo ekleyip kaldırabilirsin; varsayılan olarak
  bu projenin GitHub Pages deposu (`https://yahya3886.github.io/novastore/repo`) gelir
- **Önbellek**: indeks çevrimdışı kullanılabilir; `maxage` süresi dolunca otomatik yenilenir
- Material 3 (dinamik renkler), karanlık tema, Türkçe arayüz

## Derleme

```bash
# JDK 17+ ve Android SDK (platform 37) gerekir
./gradlew :app:assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # birim testleri
```

APK'yı cihaza kur:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Kendi deponu oluşturma (adım adım)

> **Sunucun yok mu?** Bu repo hazır: `apks/` içine APK ekle, `meta/<paket>.json` künye yaz,
> push'la — GitHub Actions indeksi üretip **GitHub Pages**'e yayınlar:
> `https://<kullanıcı>.github.io/<repo>/repo`. NovaStore bu adresi varsayılan depo olarak kullanır.

### 1. Dizin yapısı (kendi sunucunda barındıracaksan)

```
projen/
├── apks/       # uygulamalarının APK dosyaları
├── meta/       # <paket>.json künye dosyaları
├── icons/      # <paket>.png ikonlar (512x512 önerilir)
└── shots/      # shots/<paket>/<dil>/<tür>/*.png ekran görüntüleri
```

`shots` türleri: `phoneScreenshots`, `sevenInchScreenshots`, `tenInchScreenshots`,
`tvScreenshots`, `wearScreenshots`.

`meta/com.ornek.hello.json` örneği (`meta/_ornek.json` dosyasında tüm alanlar var):

```json
{
  "name": "Merhaba Dünya",
  "summary": "Kısa özet (listede görünür)",
  "description": "<p>HTML açıklaması…</p>",
  "categories": ["Tools"],
  "license": "MIT",
  "authorName": "Adın Soyadın",
  "sourceCode": "https://github.com/kullanici/proje",
  "webSite": "https://ornek.com",
  "issueTracker": "https://github.com/kullanici/proje/issues",
  "releaseNotes": "Sürüm notu…",
  "localized": {
    "tr-TR": { "name": "Merhaba Dünya", "summary": "Özet" },
    "en-US": { "name": "Hello World", "summary": "Summary" }
  }
}
```

### 2. İndeksi üret

```bash
python3 tools/mkindex.py \
    --apk-dir ./apks \
    --out ./repo \
    --address https://repo.ornek.com/repo \
    --name "Benim Depom" \
    --description "Kendi uygulamalarım"
```

Araç `aapt2` ile APK'ların adını/sürümünü/izinlerini okur, SHA-256 özetini hesaplar ve
`repo/` altına `index-v1.json` + `icons/` + APK dosyalarını yazar.

> `aapt2` bulunamazsa `--aapt2 /path/to/aapt2` ile ver veya `ANDROID_HOME` tanımla.

### 3. Yayınla

Depo düz bir dosya klasörüdür; GitHub Pages, Cloudflare Pages, S3, kendi sunucun…
fark etmez:

```bash
rsync -av repo/ sunucu:/var/www/repo/
# veya yerelde dene:
python3 -m http.server -d repo 8080
```

### 4. NovaStore'a ekle

**Ayarlar → Depo ekle** alanına adresi yaz (ör. `https://repo.ornek.com/repo`).
Yenile'ye bas; uygulamaların katalogda görünür.

Emülatörden yerel sunucuya erişirken `http://10.0.2.2:8080/repo` kullan
(`10.0.2.2`, geliştirme makinenin karşılığıdır).

## Güvenlik notları

- Her APK, indeksteki SHA-256 ile doğrulanır; uyuşmazlıkta dosya silinir ve kurulum
  yapılmaz.
- Üretimde **HTTPS** kullan. İsteğe bağlı olarak
  `app/src/main/AndroidManifest.xml` içindeki `android:usesCleartextTraffic="true"`
  satırını kaldırıp yalnızca HTTPS depolarına izin verebilirsin (yerel ağ testleri için
  bu yüzden açık bırakıldı).
- İmzalama: APK'ları imzalı dağıt; NovaStore imza doğrulaması yapmaz, bunu cihazın paket
  yöneticisi yapar.

## Proje yapısı

```
app/src/main/java/dev/novastore/app/
├── MainActivity.kt              # tek Activity
├── data/
│   ├── Index.kt                 # index-v1.json Gson modelleri
│   ├── RepoConfig.kt            # depo listesi + kalıcılık (SharedPreferences)
│   ├── CatalogRepository.kt     # indirme + önbellek + ayrıştırma
│   └── AppEntry.kt              # arayüz modeli, arama/filtre (saf fonksiyonlar)
├── install/
│   ├── ApkDownloader.kt         # indirme + SHA-256 doğrulama
│   └── Installer.kt             # FileProvider / kurulum / açma / kaldırma
├── viewmodel/CatalogViewModel.kt
├── ui/
│   ├── NovaStoreRoot.kt         # Navigation + niyet yürütücü
│   ├── home/ detail/ installed/ settings/
│   ├── components/AppBits.kt
│   └── theme/
└── util/RepoPaths.kt            # URL birleştirme, yerelleştirme, ikon/görsel yolları

tools/mkindex.py                 # APK klasöründen depo indeksi üreteci
```

Adı/paketi değiştirmek istersen: `app/build.gradle.kts` → `applicationId` ve `namespace`,
`res/values/strings.xml` → `app_name`, `AndroidManifest.xml` → FileProvider otoritesi
(`dev.novastore.app.fileprovider`), `ui/detail` dışındaki kodlarda `dev.novastore.app`
paket yolunu yeniden adlandır.

## Test

`app/src/test` içinde indeks ayrıştırma, yerelleştirme seçimi, URL üretimi ve
arama/kategori filtresi testleri var:

```bash
./gradlew :app:testDebugUnitTest
```

## Lisans

MIT — bkz. [`LICENSE`](LICENSE). F-Droid projesiyle aynı depo formatını kullanır;
NovaStore, F-Droid'in resmi uygulamasının çatalı değildir, bağımsız bir istemcidir.
