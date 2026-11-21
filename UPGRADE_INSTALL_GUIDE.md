# MobAntigravity v1.4.0 Upgrade & Install Guide (Oppo A79 5G)

इस पैकेज में आपकी रिपॉजिटरी (`hemantkumar30590-tech/Mobantigravity`) के लिए सभी अपग्रेडेड फ़ाइलें सही फ़ोल्डर स्ट्रक्चर के साथ तैयार हैं।

## इस अपग्रेड (v1.4.0) में क्या नया है?
1. **Oppo A79 5G (MediaTek Dimensity 6020 / Mali-G57) परफॉर्मेंस ट्यूनिंग:** `LP_NUM_THREADS=4`, `NODE_OPTIONS="--max-old-space-size=2048"`, `MALLOC_ARENA_MAX=2`, और `--disable-gpu-vsync --in-process-gpu --renderer-process-limit=3`।
2. **ColorOS बैकग्राउंड प्रोटेक्शन:** नया `GemKeepAliveService` (Foreground Service) + ऑटो-रीकनेक्टिंग लोकल सर्वर (`127.0.0.1:8898`) + ऐप में सीधा **Battery Unrestricted** और **Android 13/14+ Restricted Settings (App Info)** बटन।
3. **डायनामिक स्क्रीन रेजोल्यूशन (`WindowInsets`):** हार्डकोड `1080x2161` के बजाय जेस्चर/बटन नेविगेशन के अनुसार लाइव स्क्रीन साइज और DPI ऑटो-सेट।
4. **Fast Sync (`sync-scripts.sh`):** पूरा Debian PRoot दोबारा इंस्टॉल किए बिना सिर्फ 2 सेकंड में नई स्क्रिप्ट्स और डिस्प्ले प्रोफ़ाइल अपडेट करें।
5. **सेफ VA39 पैचर और CI लिंटिंग:** `patch_va39.py` में एटॉमिक राइट व सेफ्टी चेक, और `.github/workflows/build-apk.yml` में `bash -n` व `py_compile`।

## अपने Oppo A79 5G में बिल्ड और इंस्टॉल कैसे करें?

### तरीका 1: GitHub Actions से ऑटोमैटिक APK बिल्ड (सबसे आसान)
1. इस ZIP को एक्सट्रैक्ट करें और अंदर की सभी फ़ाइलों को अपनी स्थानीय `Mobantigravity` रिपॉजिटरी में उसी फ़ोल्डर पाथ पर कॉपी/रिप्लेस कर दें।
2. GitHub पर पुश करें:
   ```bash
   git add .
   git commit -m "Upgrade to v1.4.0: Oppo A79 5G Mali/RAM tuning, ColorOS keep-alive & dynamic display"
   git push origin main
   ```
3. लगभग 2–3 मिनट में GitHub Actions का **Build MobAntigravity APK** वर्कफ़्लो अपने आप नया `MobAntigravity-debug.apk` बनाकर **Releases → `latest-apk`** पर पब्लिश कर देगा।
4. अपने Oppo A79 5G पर `latest-apk` रिलीज़ से नया `MobAntigravity-debug.apk` डाउनलोड करके इंस्टॉल करें।
5. ऐप खोलें:
   - **"Allow Background / Battery Unrestricted"** पर टैप करके Allow करें।
   - यदि पहले से Debian PRoot सेटअप है, तो सिर्फ **"Fast Sync Scripts & Display Profile"** पर टैप करें (2 सेकंड में अपडेट हो जाएगा, पूरा सेटअप दोबारा नहीं करना पड़ेगा)।
   - यदि नया सेटअप है, तो **"1-Tap Install & Setup"** पर टैप करें।

### तरीका 2: बिना नया APK बनाए केवल स्क्रिप्ट्स अभी टेस्ट करें (AntiGem में)
यदि आप APK बिल्ड करने से पहले ही Mali/RAM ट्यूनिंग टेस्ट करना चाहते हैं, तो ZIP से `app/src/main/assets/` के अंदर की फ़ाइलें अपने फोन के `/sdcard/Download/MobAntigravity/` में कॉपी करें और AntiGem टर्मिनल में चलाएँ:
```bash
bash /sdcard/Download/MobAntigravity/sync-scripts.sh
gem
```
