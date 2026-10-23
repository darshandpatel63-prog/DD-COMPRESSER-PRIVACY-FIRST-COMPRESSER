# 📢 AdMob Integration — DD Compressor (યાદદાસ્ત ફાઇલ)

આ ફાઇલ AdMob નું **આખું જ્ઞાન** સાચવે છે: બધા IDs, જાહેરાત ક્યારે બતાવવી તેનો logic, કોડ ક્યાં ક્યાં છે, અને crash થી કેવી રીતે બચવું.
ભવિષ્યમાં જ્યારે AdMob **A થી Z delete કરીને ફરીથી નવેસરથી** integrate કરવું હોય, ત્યારે આ ફાઇલ વાંચીને બધું જ પાછું બનાવી શકાય છે.

---

## 1) 🔑 AdMob IDs

| પ્રકાર | ID | ક્યાં વપરાય છે |
|---|---|---|
| **App ID** | `ca-app-pub-9557757991977213~8750973505` | `AndroidManifest.xml` (meta-data `APPLICATION_ID`) + `AdsConfig.APP_ID` |
| **Banner** | `ca-app-pub-9557757991977213/8928348733` | ઉપર અને નીચે, બંને જગ્યાએ |
| **Interstitial** | `ca-app-pub-9557757991977213/9510802662` | મોટા batch પછી |
| **Rewarded** | `ca-app-pub-9557757991977213/1993993468` | "Watch an ad" બટન |
| Rewarded Interstitial | `ca-app-pub-9557757991977213/5180675414` | AdMob માં બનાવેલ છે, **હજી ઉપયોગમાં નથી** |
| Native | `ca-app-pub-9557757991977213/5013329464` | AdMob માં બનાવેલ છે, **હજી ઉપયોગમાં નથી** |

**Google ના Test IDs (ફક્ત debug build માં વપરાય છે):**

| પ્રકાર | Test ID |
|---|---|
| Banner | `ca-app-pub-3940256099942544/9214589741` |
| Interstitial | `ca-app-pub-3940256099942544/1033173712` |
| Rewarded | `ca-app-pub-3940256099942544/5224354917` |

> બધા IDs એક જ જગ્યાએ છે: `android-native/app/src/main/java/com/darshan/compresser/AdsConfig.java`
> (ફક્ત App ID ની બીજી નકલ Manifest માં છે.)

---

## 2) 🎯 જાહેરાત ક્યારે અને ક્યાં બતાવવી (Logic)

| જાહેરાત | ક્યારે બતાવવી | ક્યાં | નિયમ |
|---|---|---|---|
| **Banner (adaptive)** | App ખૂલ્યા પછી, SDK તૈયાર થાય ત્યારે | **ઉપર:** brand bar ની નીચે, "Compress without uploading." ની ઉપર<br>**નીચે:** reward બટન ની નીચે, footer ની ઉપર | Ad લોડ થાય ત્યારે જ જગ્યા દેખાય; લોડ ન થાય તો જગ્યા બંધ (ખાલી gap નહીં). Ad-free દરમિયાન બંને banner દૂર. |
| **Interstitial** | "Compress selected files" દબાવ્યા પછી **બધી ફાઇલો પૂરી થાય ત્યારે** (batch end) | Full screen | **ફક્ત જો batch માં 7 કે તેથી વધુ ફાઇલો** હોય (`INTERSTITIAL_MIN_FILES = 7`, નિષ્ફળ ફાઇલ પણ ગણાય). Ad-free દરમિયાન ક્યારેય નહીં. **App background માં હોય ત્યારે નહીં** (policy). બતાવ્યા પછી આગળની ad તરત preload. |
| **Rewarded** | વપરાશકર્તા **"Watch an ad • 30 minutes ad-free"** બટન દબાવે ત્યારે | Full screen, બટન info card ની નીચે | Reward મળે તો **30 મિનિટ** (`AD_FREE_MS`) માટે banners દૂર + interstitial બંધ + toast "30 minutes ad-free activated." 30 મિનિટ પછી banners પાછા આવે. Ad તૈયાર ન હોય તો "still loading" સંદેશ. |
| **Consent (UMP)** | દરેક launch પર સૌથી પહેલા | Google નું ફોર્મ (જરૂર હોય ત્યારે) | **પહેલા consent, પછી જ SDK initialize.** Consent વગર કોઈ ad request નહીં. |

બીજી બાબતો:
- Ad-free સ્થિતિ ફક્ત memory માં છે. App ફરી શરૂ થાય તો ads ફરી શરૂ.
- Banner ની પહોળાઈ = સ્ક્રીનની પહોળાઈ − 32dp (main layout ના 16dp + 16dp padding).
- Release build માં **live IDs**, debug build માં **test IDs** (`BuildConfig.DEBUG`).
- **પોતાના ફોન પર ચકાસણી માટે:** `AdsConfig.java` માં `FORCE_TEST_ADS = true` કરો, તો release APK પણ Google ની test ads બતાવશે (live ads પર ભૂલથી click થતાં account જોખમમાં આવે). Store release પહેલાં ફરી `false` કરો.
- નીચેના banner આગળ 16dp વધારાની જગ્યા છે, જેથી "Watch an ad" બટનના બદલે ad પર ભૂલથી tap ન થાય (AdMob policy).

---

## 3) 🗂️ AdMob કોડ ક્યાં ક્યાં છે (delete / re-add checklist)

| ફાઇલ | AdMob નો ભાગ |
|---|---|
| `android-native/app/build.gradle` | `play-services-ads:25.5.0` અને `user-messaging-platform:4.0.0` (2 dependencies) |
| `android-native/app/src/main/AndroidManifest.xml` | `xmlns:tools`, permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `com.google.android.gms.permission.AD_ID`; meta-data `APPLICATION_ID`; provider `MobileAdsInitProvider` (`tools:node="remove"`) |
| `.../compresser/AdsConfig.java` | બધા IDs + નિયમો (7 ફાઇલો, 30 મિનિટ, વગેરે) — **આખી ફાઇલ** |
| `.../compresser/AdsGuard.java` | Crash-loop breaker — **આખી ફાઇલ** |
| `.../compresser/AdsManager.java` | Consent, initialize, banner, interstitial, rewarded — **આખી ફાઇલ** |
| `.../compresser/MainActivity.java` | fields `adTopSlot`, `adBottomSlot`, `adsManager` · `startAds()` · `onResume()`/`onPause()` · ઉપરનો ad slot (`buildUi`) · reward બટન + નીચેનો ad slot · `onDestroy()` માં `destroy()` · batch end પર `showInterstitialAtBatchEnd(completedCount)` |
| `android-native/app/proguard-rules.pro` | હાલમાં AdMob નો કોઈ rule નથી (નીચે "Troubleshooting" જુઓ) |
| `js/main.js`, `index.html` | **હવે કંઈ નથી.** જૂનું `js/admob.js` (Capacitor plugin) native app માં વપરાતું ન હતું, તેથી delete કર્યું અને ફરી ઉમેર્યું નથી. |

> ⚠ `.github/workflows/build-apk.yml` `android-native` ફોલ્ડરમાં `capacitor`, `WebView`, `ffmpeg.wasm` શબ્દો શોધે છે અને મળે તો build ફેલ કરે છે. એટલે `android-native` ની અંદર (comments માં પણ) આ શબ્દો **ન લખવા**.

---

## 4) 🛡️ Crash થી બચવાની રચના (કેમ આવી રીતે લખ્યું)

App ખોલતાં જ splash પછી તરત બંધ થઈ જતી હતી. સાચું કારણ crash log (logcat) વગર પાકું કહી શકાય નહીં, તેથી રચના એવી બનાવી છે કે **કારણ ગમે તે હોય, app ખુલે જ અને કારણ સ્ક્રીન પર દેખાય**. સૌથી સંભવિત જોખમો: ads SDK નો કોઈ Error (ફક્ત `Exception` પકડાતું હતું), અથવા SDK નું process શરૂ થતી વખતે auto-start. એટલે:

1. **SDK ની auto-start બંધ:** Manifest માં `MobileAdsInitProvider` દૂર કર્યું. Process શરૂ થતી વખતે ads નો કોઈ કોડ ચાલતો નથી (ત્યાં crash થાય તો કોઈ બચાવી ન શકે).
2. **પહેલી સ્ક્રીન પછી જ ads:** `START_DELAY_MS = 1500` ms પછી જ શરૂ.
3. **દરેક entry point પર `catch (Throwable)`:** જૂના કોડમાં ફક્ત `Exception` પકડાતું હતું. `NoClassDefFoundError`, `NoSuchMethodError` જેવા **Error** પકડાતા ન હતા અને app બંધ થઈ જતી.
4. **ક્રમ:** Consent (UMP) → `MobileAds.initialize` **worker thread** પર → ads load.
5. **`MainActivity` માં પણ try/catch:** `startAds()` ગમે તે થાય, app ખુલે જ.
6. **`AdsGuard` (crash-loop breaker):** Ads શરૂ થતાં પહેલાં disk પર "window open" ફ્લેગ લખાય છે; 15 સેકન્ડ પછી app જીવતી હોય તો ફ્લેગ બંધ. જો તે દરમિયાન app crash થાય, તો **આગલી launch માં ads 6 કલાક માટે બંધ** (પછી 12h, 24h … મહત્તમ 7 દિવસ) અને app સામાન્ય રીતે ખુલે. Crash નું કારણ toast માં દેખાય છે.
7. **Unused UI gap નહીં:** Ad ન હોય ત્યારે ad slot છુપાયેલો રહે.

**Toast નો અર્થ:** `Ads switched off (consent -> NoSuchMethodError: ...)` — એટલે ads નું કોઈ ભાગ fail થયો, app ચાલુ રહી. આ લખાણનો screenshot લઈને મોકલો, કારણ તરત મળી જશે.
જ્યારે બધું સ્થિર લાગે, ત્યારે `AdsConfig.SHOW_DEBUG_NOTICES = false` કરવાથી આવા toast બંધ થશે.

---

## 5) 🔧 Troubleshooting (જો હજી પણ સમસ્યા આવે)

| લક્ષણ | કરવાનું |
|---|---|
| App ખૂલે છે પણ ads આવતી નથી, toast માં error | Toast નું લખાણ મોકલો. |
| Ads જ નથી આવતી, કોઈ error નથી | નવા ad units / નવી app પર ads આવવાનું શરૂ થવામાં થોડા કલાકથી 1 દિવસ (ક્યારેક વધુ) લાગી શકે. AdMob console માં app ની સ્થિતિ તપાસો. |
| R8 સંબંધિત શંકા | `proguard-rules.pro` માં આ એક લીટી ઉમેરીને ચકાસો: `-keep class com.google.android.gms.ads.** { *; }` (જૂના project માં હતી.) |
| "ads paused" પછી તરત ફરી ચલાવવી હોય | Phone માં App info → Storage → **Clear data** (ads નો pause રદ થશે). |

---

## 5.1) 📌 SDK સંબંધિત નોંધ (Google ના official docs મુજબ, ઓક્ટોબર 2026)

- `play-services-ads:25.5.0` (17 સપ્ટેમ્બર 2026) હાલનું નવીનતમ છે; તે `user-messaging-platform:4.0.0` પર આધારિત છે, એટલે બંને version મેળ ખાય છે.
- `AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize()` SDK 25.0.0 થી **deprecated** છે, છતાં ચાલે છે (build માં ફક્ત ચેતવણી). ભવિષ્યમાં તેને નવા *large anchored adaptive* API થી બદલી શકાય (banner ઊંચો થશે).
- Google એ આ "Legacy" Mobile Ads SDK ને maintenance mode માં મૂક્યો છે અને નવા **GMA Next-Gen SDK** પર migrate કરવાની ભલામણ કરે છે. હાલ માટે જરૂરી નથી, પણ ભવિષ્યની યોજનામાં રાખો.

---

## 6) ⚠️ સુરક્ષા અને નીતિ સંબંધિત ચેતવણીઓ

- **તમારી પોતાની live ads પર ક્યારેય click ન કરો.** આ AdMob policy નો ભંગ છે અને account બંધ થઈ શકે છે. પરીક્ષણ માટે debug build (test IDs) અથવા AdMob માં "test device" નોંધો.
- **Privacy / Store દસ્તાવેજો અપડેટ કરવા:** `PRIVACY.md`, `legal/privacy.html`, `STORE_LISTING.md` માં હાલ લખેલું છે કે "ad-free build / AdMob SDK સમાવિષ્ટ નથી". Ads સાથે release કરતાં પહેલાં આ લખાણ સુધારો, અને Play Console માં **Data safety** અને **Ads declaration** (Advertising ID / AD_ID) મુજબ ભરો. ખોટું declaration app ને Play Store માંથી દૂર કરાવી શકે.
- **Reward ની મર્યાદા:** "30 મિનિટ ad-free" ફક્ત app ની અંદર ચકાસાય છે. કોઈ rooted ફોન પર તેને ફેરવી શકે, પણ તેમાં કોઈ પૈસાનું મૂલ્ય નથી, તેથી server-side verification જરૂરી નથી.
- **Ad unit IDs છુપા નથી:** APK માં IDs દેખાય જ છે. AdMob console માં app ને Play Store listing સાથે link કરો, જેથી બીજી app તમારા IDs નો દુરુપયોગ કરે તો જોખમ ઘટે.
- **EEA/UK માટે privacy options entry:** UMP નું "Privacy options" બટન હજી ઉમેર્યું નથી. યુરોપિયન વપરાશકર્તાઓ માટે Google તેની ભલામણ કરે છે; ઉમેરવું હોય તો કહો.
- Ads SDK advertising ID, device/app ની માહિતી, IP અને ad interaction જેવો ડેટા Google ને મોકલી શકે છે. Compression engine તમારી ફાઇલોનું કન્ટેન્ટ ads SDK ને આપતું નથી (compression local છે), પણ privacy policy માં ads નો ઉલ્લેખ સાચો હોવો જરૂરી છે.

---

## 7) ♻️ ફરીથી A થી Z delete કરીને integrate કરવાની રીત

**Delete:** ઉપરના વિભાગ 3 ની યાદીમાંનું બધું દૂર કરો (dependencies, manifest, ત્રણ `Ads*.java`, MainActivity ના ad ભાગો).
**ફરી ઉમેરો:** વિભાગ 1 ના IDs, વિભાગ 2 નો logic અને વિભાગ 4 ના સલામતી નિયમો મુજબ ફરી લખો. IDs ફક્ત `AdsConfig.java` અને Manifest ના App ID માં જ રાખો.
