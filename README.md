# DarkStream – HTML arayüzlü oynatıcı

CloudStream mantığında çalışan bir Android uygulaması (telefon ve Android TV). Arayüz tamamen HTML/JS ile yazıldığı için `web/` klasöründeki dosyaları düzenleyerek görünümü değiştirebilirsin. Ağ istekleri ve video oynatma uygulamanın içinde yapılır, bu yüzden **CORS engeli yoktur** ve Referer/User-Agent gibi başlıklar eklenebilir.

```
web/            → arayüz (index.html, style.css, app.js) + örnek catalog.json
android/        → uygulama: WebView + ExoPlayer (Media3)
.github/        → her push'ta APK derleyen iş akışı
```

## APK nasıl alınır?

`darkstream` dalına her gönderimde GitHub Actions APK'yı derler.

- **Releases** → `darkstream-apk` sürümü → `DarkStream.apk`
- ya da **Actions** → son çalışma → *Artifacts* → `DarkStream.apk`

Elle başlatmak için: **Actions → DarkStream APK → Run workflow** (dal: `darkstream`).

## Nasıl çalışır?

| Parça | Görevi |
|---|---|
| `web/app.js` | Depoları yükler, ana sayfa / detay / sezon-bölüm / arama / ayarlar |
| `Android.request()` | JSON/M3U dosyalarını uygulama içinden indirir (CORS yok) |
| `Android.play()` | Yerel oynatıcıyı açar: HLS, DASH, MP4, altyazı, kalite seçimi |
| `PlayerActivity` | Bir kaynak açılmazsa listedeki sıradakine geçer, kalınan yeri kaydeder |

Tarayıcıda açarsan (`web/index.html`) aynı arayüz `<video>` + hls.js ile çalışır, ama orada CORS kuralları geçerlidir.

## Katalog (depo) biçimi

Ayarlar → *Depolar* bölümüne bir JSON adresi ekle (ör. GitHub'daki dosyanın **raw** bağlantısı).

```json
{
  "name": "Benim Depom",
  "categories": [
    {
      "title": "Filmler",
      "items": [
        {
          "id": "film1",
          "title": "Film Adı",
          "year": 2024,
          "poster": "https://.../poster.jpg",
          "backdrop": "https://.../genis.jpg",
          "description": "Açıklama",
          "sources": [
            {
              "name": "1080p",
              "type": "hls",
              "url": "https://.../master.m3u8",
              "headers": { "Referer": "https://site.com/", "User-Agent": "..." },
              "subtitles": [{ "lang": "tr", "label": "Türkçe", "url": "https://.../tr.vtt" }]
            },
            { "name": "Yedek", "type": "mp4", "url": "https://.../film.mp4" }
          ]
        }
      ]
    },
    {
      "title": "Diziler",
      "items": [
        {
          "id": "dizi1",
          "title": "Dizi Adı",
          "type": "series",
          "poster": "https://.../poster.jpg",
          "seasons": [
            {
              "season": 1,
              "episodes": [
                { "episode": 1, "title": "Pilot", "sources": [{ "url": "https://.../s1e1.m3u8" }] }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

- `type`: `hls`, `dash`, `mp4` (boşsa adresten tahmin edilir). İçerik için `type`: `movie`, `series`, `live`.
- `headers`: oynatıcıya eklenecek HTTP başlıkları (Referer, Origin, User-Agent, Cookie…).
- Birden fazla `sources` verirsen ilki açılmazsa otomatik olarak sonrakine geçilir.
- Altyazı: `.vtt` veya `.srt`.

### M3U listeleri

Depo olarak `.m3u` adresi de eklenebilir. `group-title` kategori olur, `tvg-logo` afiş olur. Başlıklar şu iki yolla okunur:

```
#EXTINF:-1 tvg-logo="logo.png" group-title="Ulusal",Kanal Adı
#EXTVLCOPT:http-referrer=https://site.com/
#EXTVLCOPT:http-user-agent=Mozilla/5.0
https://.../kanal.m3u8
https://.../kanal2.m3u8|Referer=https://site.com/&User-Agent=Mozilla/5.0
```

## Kumanda / TV

Ok tuşlarıyla kartlar arasında gezilir, OK ile açılır, Geri ile kapanır. Oynatıcıda sol/sağ 10 saniye ileri-geri sarar.

## Not

Örnek katalogdaki videolar herkese açık test yayınları ve Blender Vakfı'nın açık lisanslı filmleridir. Kendi depolarına yalnızca yayınlama hakkın olan içerikleri ekle.

İmza anahtarı (`android/app/darkstream.keystore`) depoda durur ki her yeni APK eskisinin üzerine kurulabilsin. Uygulamayı herkese dağıtacaksan kendi gizli anahtarını kullan.
