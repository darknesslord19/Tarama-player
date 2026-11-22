# PuuStream IPTV Motoru — Güncelleme Raporu

**Tarih:** 22-11-2026 22:53 (TSİ) · **Test bölgesi:** github · **Süre:** 7 dk 1 sn

✅ **Yayınlandı.** Koruma: kontroller geçti

## Özet

| | |
|---|---|
| Sağlam kanal (en az 1 çalışan kaynak) | **493** |
| Yedek kaynağı olan kanal (≥2 sağlam) | 197 |
| tr.m3u otomatik bölüm kaydı | 690 |
| Test edilen adres | 1343 (derin/FFprobe: 500) |
| Sonuç | ✅ 1196 sağlam · ⚠️ 25 kararsız · 🚫 59 engelli · ❌ 63 ölü |
| Karantinada (bu tur atlandı) | 758 |
| Kaynak listesi | 187/197 indirildi |
| Aday (hafızadan geri çağrılan) | 2101 (589) |
| Resmî site çözücüleri · JSON veritabanı · kardeş sunucu | 11 · 147 · 141 aday |
| Türk listesi sayılan kaynak | 188 |
| Yabancı olduğu için indirilmeyen dosya | 3686 |
| Elenen | paid: 490, banned: 1285, credential: 27, foreign: 3504 |

## Zırhlı kanallar

- Kimlik: **0** kanal (0 kayıt)
- Sağlam: **0** · Çalışmayan: **0**
- Kendi adresi bozuk ama otomatik yedekle kurtarılan: **0**

## Gruplara göre sağlam kanallar

- **TR-DİĞER:** 153
- **TR-YEREL:** 126
- **TR-ULUSAL:** 53
- **TR-HABER:** 47
- **TR-MÜZİK:** 37
- **TR-DİNİ:** 21
- **TR-SPOR:** 19
- **TR-ÇOCUK:** 18
- **TR-BELGESEL:** 16
- **TR-SİNEMA:** 3

## En iyi 20 kanal (puan)

| Kanal | Puan | Sağlam kaynak | Çözünürlük | Açılış |
|---|---|---|---|---|
| TRT 1 | 100 | 6 | 1080p | 100 ms |
| Kanal D | 100 | 6 | 1080p | 698 ms |
| Show TV | 100 | 6 | 1080p | 640 ms |
| Star TV | 100 | 6 | 1080p | 705 ms |
| TV8 | 100 | 6 | 1080p | 202 ms |
| Kanal 7 | 100 | 1 | 1080p | 766 ms |
| Beyaz TV | 100 | 6 | 1080p | 514 ms |
| 360 | 100 | 6 | 1080p | 548 ms |
| Teve2 | 100 | 6 | 1080p | 106 ms |
| TV4 | 100 | 6 | 1080p | 194 ms |
| A2 | 100 | 6 | 1080p | 739 ms |
| TRT Türk | 100 | 6 | 1080p | 412 ms |
| TRT Avaz | 100 | 6 | 1080p | 897 ms |
| TRT Kurdî | 100 | 6 | 1080p | 666 ms |
| TRT Arabi | 100 | 5 | 1080p | 116 ms |
| Show Max | 100 | 6 | 1080p | 440 ms |
| Kanal 7 Avrupa | 100 | 6 | 1080p | 436 ms |
| TV 24 | 100 | 6 | 1080p | 339 ms |
| TYT Türk | 100 | 2 | 1080p | 591 ms |
| Vav TV | 100 | 3 | 1080p | 453 ms |

## En verimli kaynaklar

- 235 sağlam · `https://raw.githubusercontent.com/YosemiteSam6/turkiye-iptv-m3u-m3u8/main/kanallar.m3u`
- 133 sağlam · `https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/main/m3u/turkiye-iptv-org.m3u`
- 107 sağlam · `https://raw.githubusercontent.com/raalbatros/iptv/HEAD/176.m3u`
- 84 sağlam · `https://raw.githubusercontent.com/hussobaba/tbrt_medya/HEAD/Huseyin_cvk_tv.m3u8`
- 46 sağlam · `https://onureroz.com/indirmeler/turk/index.m3u`
- 44 sağlam · `https://raw.githubusercontent.com/Yusufdkci/iptv/main/liste.m3u`
- 44 sağlam · `https://raw.githubusercontent.com/MediaVizo/Premium1/HEAD/Giri%C5%9F%20Paketi.m3u8`
- 33 sağlam · `https://raw.githubusercontent.com/kodlama155-ctrl/tv/main/discovered.m3u`
- 30 sağlam · `https://raw.githubusercontent.com/kodlama155-ctrl/tv/main/tr.m3u`
- 30 sağlam · `https://raw.githubusercontent.com/BoranT-3000/PlayerM3U8/HEAD/data/master_all_channels.m3u8`
- 25 sağlam · `https://raw.githubusercontent.com/bugsfreeweb/LiveTVCollector/HEAD/BugsfreeStreams/Output/StreamLinks-TR.m3u`
- 24 sağlam · `https://raw.githubusercontent.com/ridvanseref37/tivi-rs-playlist/main/Tivi_RS_M3U_Listesi.m3u`
- 20 sağlam · `https://raw.githubusercontent.com/levi-45/iptvstream/HEAD/TURK_ULUSAL_IPTV.m3u`
- 19 sağlam · `https://raw.githubusercontent.com/famelack/famelack-data/main/tv/countries/tr.json`
- 18 sağlam · `https://raw.githubusercontent.com/iptv-turk-tr/iptv/main/list.m3u`

## Ölü/engelli nedenleri

- http 403: 50
- timeout: 19
- http 404: 14
- segment http 401: 9
- ffprobe: 8
- variant: 5
- not media: 5
- segment http 404: 3
- http 429: 3
- empty playlist: 2
- variant http 500: 2
- variant http 404: 1

_iptv-org ile zenginleştirilen kanal: 97_
