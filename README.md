# Apollo Kumanda: Google TV için Android kumanda uygulaması

Telefonu, aynı Wi-Fi ağındaki Google TV veya Android TV cihazı için kumandaya çevirir.
Google'ın resmi "Google TV" kumandasının kullandığı **Android TV Remote v2** protokolünü sıfırdan uygular.
TV'de geliştirici modu ya da ADB açmanız gerekmez.

Arayüz, evdeki **Dijitsu Apollo Premium 4K** televizyonun fiziksel kumandasıyla birebir eşleşecek şekilde tasarlandı.
Protokol standart olduğu için diğer Google TV ve Android TV cihazlarıyla da çalışır.

[Son APK'yi indir](../../releases/latest)

## Özellikler

**Bağlantı**
- Ağdaki TV'leri otomatik bulma (mDNS) ve elle IP girme
- TV ekranında çıkan 6 karakterli kodla tek seferlik eşleştirme
- Birden fazla TV kaydetme ve aralarında geçiş
- Son TV'yi hatırlama, bağlantı koparsa otomatik yeniden bağlanma

**Kumanda**
- Yön tuşları, OK, Geri, Ana ekran, Güç, Kaynak, renkli tuşlar ve diğer TV tuşları
- Dokunmatik yüzey: kaydırarak gezinme, dokunarak OK, basılı tutarak Geri
- Ses ve kanal +/–, Sessiz; telefonun kendi ses tuşları da TV'nin sesini değiştirir
- Oynatma kontrolleri ve sayı tuşları

**Ek özellikler**
- Telefon mikrofonuyla sesli arama
- Telefon klavyesiyle TV'deki arama kutusuna yazı yazma
- Düzenlenebilir uygulama kısayolları ve TV'de açık olan uygulamayı kısayollara ekleme
- Aranabilir, düzenlenebilir kanal listesi
- Uyku zamanlayıcısı: süre dolunca TV'yi kapatır
- Ana ekran widget'ı ve bildirimden kumanda

## Teknik ayrıntılar

| Konu | Uygulama |
| --- | --- |
| Arayüz | Kotlin, Jetpack Compose, Material 3 |
| Keşif | `_androidtvremote2._tcp` mDNS servisi (`TvDiscovery.kt`) |
| Eşleştirme | Port 6467 üzerinde TLS, istemci sertifikası BouncyCastle ile üretilir (`PairingClient.kt`, `Tls.kt`) |
| Komutlar | Port 6466 üzerinde kalıcı TLS bağlantısı (`RemoteClient.kt`) |
| Mesajlar | Harici kütüphane olmadan yazılmış küçük bir protobuf okuyucu/yazıcı (`Proto.kt`) |
| Ses | Mikrofon sesini TV'ye akıtma (`VoiceStreamer.kt`) |
| Derleme | GitHub Actions her `main` güncellemesinde imzalı APK üretir ve sürüm olarak yayınlar |

## Derleme

1. **Android Studio**'yu kurun (Ladybug veya daha yeni bir sürüm).
2. *File › Open* ile depo klasörünü (`tw-kumanda`) açın. Gradle senkronizasyonu gereken her şeyi kendisi indirir.
3. Telefonu USB ile bağlayın (telefonda USB hata ayıklama açık olmalı) ve ▶ **Run**'a basın.

En düşük Android sürümü: 8.0 (API 26).

### Sürüm imzalama

İmzalama anahtarı depoda tutulmaz. GitHub Actions, anahtarı şu Secrets değerlerinden okur:

| Secret | İçerik |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `.jks` dosyasının base64 hâli (`base64 -w0 release.jks`) |
| `SIGNING_STORE_PASSWORD` | Anahtar deposu şifresi |
| `SIGNING_KEY_ALIAS` | Anahtar adı |
| `SIGNING_KEY_PASSWORD` | Anahtar şifresi |

Bu değerler tanımlı değilse APK debug anahtarıyla imzalanır.
Yerelde aynı ortam değişkenlerini (`SIGNING_KEYSTORE_PATH` ve şifreler) tanımlayarak imzalı derleme alabilirsiniz.

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
├── MainViewModel.kt       Ekran durumu, bağlanma, eşleştirme ve kayıtlı TV'ler
├── QuickControls.kt       Widget, bildirim kumandası ve uyku zamanlayıcısı
├── ui/Screens.kt          Compose arayüzü (TV listesi, eşleştirme, kumanda, ayarlar)
└── tv/
    ├── Proto.kt           Küçük protobuf okuyucu ve yazıcı, mesaj çerçeveleme
    ├── Tls.kt             İstemci sertifikası ve TLS bağlantısı
    ├── PairingClient.kt   Eşleştirme (port 6467)
    ├── RemoteClient.kt    Komut kanalı (port 6466), tuş kodları, metin gönderme
    ├── QuickSender.kt     Widget ve bildirimden tek seferlik komut gönderme
    ├── VoiceStreamer.kt   Sesli arama için mikrofon akışı
    └── TvDiscovery.kt     mDNS keşfi
```

## Notlar

- Uygulama kısayolları TV'de ilgili uygulama yüklüyse çalışır.
- Eşleştirmeyi sıfırlamak için uygulamanın verilerini temizleyin.

## Geliştiren

Mert Melih Yüksek: fikir, tasarım ve geliştirme.

Uygulama, yapay zeka asistanı Claude ile birlikte çalışılarak geliştirildi. Gereksinimleri belirleme, arayüzü gerçek kumandaya göre tasarlama, gerçek TV üzerinde test etme ve yayına alma süreci bana aittir.

## Lisans

[MIT](LICENSE)
