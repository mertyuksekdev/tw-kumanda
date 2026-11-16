# TV Kumanda: Google TV için Android kumanda uygulaması

Telefonu, aynı Wi-Fi ağındaki Google TV veya Android TV cihazı için kumandaya çevirir.
Google'ın resmi "Google TV" kumandasının kullandığı **Android TV Remote v2** protokolüyle çalışır.
TV'de geliştirici modu ya da ADB açmanız gerekmez.

## Özellikler
- Ağdaki TV'leri otomatik bulma (mDNS) ve elle IP girme
- TV ekranında çıkan 6 karakterli kodla tek seferlik eşleştirme
- Yön tuşları, OK, Geri, Ana ekran, Güç, Kaynak, Ayarlar, Arama
- Ses ve kanal +/–, Sessiz; telefonun kendi ses tuşları da TV'nin sesini değiştirir
- Oynat/Duraklat, ileri ve geri sarma, önceki ve sonraki
- Sayı tuşları
- Tek dokunuşla uygulama açma: YouTube, Netflix, Prime Video, Disney+, Spotify
- Son bağlanılan TV'yi hatırlar, uygulama açılınca otomatik bağlanır
- TV'nin açık/bekleme durumunu ve ses seviyesini gösterir

## Derleme
1. **Android Studio**'yu kurun (Ladybug veya daha yeni bir sürüm).
2. *File › Open* ile `TvKumanda` klasörünü açın. Gradle senkronizasyonu gereken her şeyi kendisi indirir.
3. Telefonu USB ile bağlayın (telefonda USB hata ayıklama açık olmalı) ve ▶ **Run**'a basın.
   APK almak için: *Build › Build App Bundle(s) / APK(s) › Build APK(s)*.

En düşük Android sürümü: 8.0 (API 26).

## Kullanım
1. Telefon ile TV'nin aynı Wi-Fi ağında olduğundan emin olun.
2. Uygulamayı açın ve listeden TV'nizi seçin. TV listede yoksa IP adresini elle girin
   (TV'de: Ayarlar › Ağ ve İnternet).
3. TV ekranında 6 karakterli bir kod çıkar. Bu kodu uygulamaya girip **Eşleştir**'e basın.
4. Kumanda ekranı açılır. Bir sonraki açılışta eşleştirme gerekmez.

## Proje yapısı
```
app/src/main/java/com/tvkumanda/app/
├── MainActivity.kt        Giriş noktası, telefonun ses tuşlarını TV'ye yönlendirir
├── MainViewModel.kt       Ekran durumu, bağlanma ve eşleştirme akışı
├── ui/Screens.kt          Compose arayüzü (TV listesi, eşleştirme, kumanda)
└── tv/
    ├── Proto.kt           Küçük protobuf okuyucu ve yazıcı, mesaj çerçeveleme
    ├── Tls.kt             Android Keystore'da istemci sertifikası, TLS bağlantısı
    ├── PairingClient.kt   Eşleştirme (port 6467)
    ├── RemoteClient.kt    Komut kanalı (port 6466), tuş kodları
    └── TvDiscovery.kt     _androidtvremote2._tcp mDNS keşfi
```

## Notlar
- Klavyeyle metin yazma (TV'deki arama kutusuna yazı gönderme) henüz yok.
  Arama için TV'nin kendi ekran klavyesi yön tuşlarıyla kullanılabilir.
- Uygulama kısayolları TV'de ilgili uygulama yüklüyse çalışır.
- Eşleştirmeyi sıfırlamak için uygulamanın verilerini temizleyin.
