# TruTranslate

Tamamen **yerel** ve **sunucusuz** çalışan çeviri uygulaması. Çeviri için internet gerekmez; hiçbir hesap, abonelik veya bulut servisi kullanılmaz. Model cihazınıza bir kez indirilir ve tüm çeviriler cihazınızda yapılır — metinleriniz asla bir sunucuya gönderilmez.

## İçindekiler

- **Chrome Uzantısı** (`extension/`): Web sayfalarını yerinde çevirir, sayfa düzenini bozmaz.
- **Android Uygulaması** (`android/`): Google Çeviri benzeri Türkçe arayüz; metin çevirisi, konuşma tanıma (STT) ve sesli okuma (TTS).

## Çeviri Modeli

- **Tencent Hy-MT2-1.8B** (Q4_K_M kuantizasyonu, ~1,13 GB)
- Kaynak: https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF
- Lisans: **Apache 2.0**
- Türkçe dahil 33 dil destekler.
- Model, ilk kullanımda HuggingFace üzerinden **bir kez** indirilir ve cihazda önbelleğe alınır. Sonrasında çeviriler tamamen çevrimdışıdır.

## Chrome Uzantısı — Kurulum

1. Releases bölümünden `trutranslate-extension.zip` dosyasını indirin ve bir klasöre çıkarın.
2. Chrome'da `chrome://extensions` adresini açın.
3. Sağ üstten **Geliştirici modu**'nu etkinleştirin.
4. **Paketlenmemiş öğe yükle**'ye tıklayıp çıkardığınız klasörü seçin.
5. Uzantı simgesine tıklayın, hedef dili seçin; model ilk seferde indirilir (tarayıcı önbelleğine alınır).
6. Bir sayfadayken "Sayfayı Çevir"e basın — sayfa metinleri yerinde çevrilir, düzen korunur.

## Android Uygulaması — Kurulum

1. Releases bölümünden `trutranslate.apk` dosyasını telefonunuza indirin.
2. Dosyaya dokunun; Android "bilinmeyen kaynaklardan yükleme" izni isterse izin verin.
3. Uygulamayı açın; model ilk açılışta indirilir (~1,13 GB — Wi-Fi önerilir) ve cihazda saklanır.
4. Metin yazarak veya mikrofonla konuşarak çeviri yapın; çeviriyi hoparlör simgesiyle dinleyin.

## Kaynaktan Derleme

### Chrome Uzantısı

Derleme gerekmez — `extension/` klasörü olduğu gibi yüklenebilir (Manifest V3, bağımlılık yok). Çeviri motoru, MV3 uyumu için yamalanmış [wllama](https://github.com/ngxson/wllama) (llama.cpp'nin WASM portu) kullanır.

### Android APK

Gereksinimler: JDK 17, Android SDK (platform 34, build-tools 34.0.0), Android NDK 27.0.12077973, CMake 3.22.1, Gradle 8.9.

1. **Yerel kütüphaneyi derleyin** (llama.cpp köprüsü, gradle dışında):
   ```bash
   cmake -S native-build -B native-build/build \
     -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/27.0.12077973/build/cmake/android.toolchain.cmake \
     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
   cmake --build native-build/build -j2 --target trutranslate
   cp native-build/build/libtrutranslate.so android/app/src/main/jniLibs/arm64-v8a/
   ```
   (`native-build/CMakeLists.txt` llama.cpp kaynaklarını bir alt dizinde bekler; depo köküne `git clone --depth 1 https://github.com/ggml-org/llama.cpp` yapın.)
2. **APK'yı paketleyin**:
   ```bash
   cd android && echo "sdk.dir=$ANDROID_HOME" > local.properties
   gradle --no-daemon assembleDebug
   ```
   Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

## Gizlilik

TruTranslate hiçbir analitik, hesap veya arka uç servisi içermez. Çevrilen metinler cihazınızdan çıkmaz.

## Lisans

Bu proje Apache 2.0 altındadır. Kullandığı Hy-MT2-1.8B modeli de Apache 2.0 lisanslıdır (Tencent).

## v1.1.0 — Gömülü Model Sürümleri

- **Android APK (v1.1.0):** Model APK'nın içinde (~1,14 GB) — kurulumdan sonra internet gerekmez. İlk açılışta model dahili depolamaya ayıklanır (~2,3 GB boş alan gerekir).
- **Chrome Uzantısı FULL (`trutranslate-extension-full.zip`, ~1,14 GB):** Model zip'in içinde; ilk açılışta indirme olmaz. Not: tarayıcı modeli ayrıca kendi önbelleğine (OPFS) kopyalar, bu yüzden diskte ~2,3 GB yer kaplar. Hafif sürüm (`trutranslate-extension.zip`, 2,6 MB) modeli ilk kullanımda HuggingFace'den indirir — ikisi de aynı çeviriyi yapar.
- Arayüz yenilendi: Material tarzı kart tasarımı, koyu/aydınlık mod, dil değiştirme (⇄), kopyala/paylaş/dinle düğmeleri, mikrofonla giriş, çeviri geçmişi.
