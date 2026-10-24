# Third-party licenses

This project vendors a small number of open-source libraries locally under
`vendor/` (see Blueprint "Local-first dependencies" for why they're local
instead of CDN-loaded). Each keeps its own original license, listed below.
None of them are modified — they're the unmodified, real distribution
files published by their respective projects.

## @ffmpeg/ffmpeg (vendor/ffmpeg/ffmpeg.js, 814.ffmpeg.js)
Version 0.12.15 — MIT License
https://github.com/ffmpegwasm/ffmpeg.wasm

## @ffmpeg/core (vendor/ffmpeg/core/ffmpeg-core.js, ffmpeg-core.wasm)
Version 0.12.10 — GPL-2.0-or-later
(This build includes libx264, which is itself GPL-licensed — this is why
the core package as a whole is GPL rather than the ffmpeg.wasm project's
own MIT/LGPL default, and is standard for any FFmpeg build that includes
libx264.)
https://github.com/ffmpegwasm/ffmpeg.wasm/tree/main/packages/core

## pdf-lib (vendor/pdf-lib/pdf-lib.min.js)
Version 1.17.1 — MIT License
https://github.com/Hopding/pdf-lib

## Inter (vendor/fonts/)
Version 5.3.0 packaging (fonts.google.com/specimen/Inter) — SIL Open Font
License 1.1
Distributed via the @fontsource/inter npm package; only the Latin-subset
woff2 files (weights 400/600/700) are included, to keep this repository
small — the full character set is available from the sources above if
needed.

---

If you redistribute this project, keep this file and the individual
library headers (where present in the minified files) intact.


## Android native release dependencies

The native Android application resolves these dependencies through Gradle. Check the upstream project and the dependency's packaged license/notice files for the terms applicable to the exact resolved version; this list is not a substitute for notices distributed with the dependency.

- Google Mobile Ads SDK (AdMob): `com.google.android.gms:play-services-ads:25.5.0` — [Google Mobile Ads documentation](https://developers.google.com/admob/android/quick-start).
- Google User Messaging Platform (UMP): `com.google.android.ump:user-messaging-platform:4.0.0` — [UMP documentation](https://developers.google.com/admob/ump/android/quick-start).
- AndroidX Core: `androidx.core:core:1.18.0` — [AndroidX](https://developer.android.com/jetpack/androidx).
- AndroidX Media3 Transformer/Effect/Common: `1.11.1` — [Media3](https://developer.android.com/media/media3).
- PdfBox-Android: `com.tom-roush:pdfbox-android:2.0.27.0` — [project and licensing information](https://github.com/TomRoush/PdfBox-Android).

AdMob and UMP-related data processing is described in the DD Compressor Privacy Policy. Selected compression files are intended to be processed locally and are not intentionally uploaded to a D.D. Tech Labs compression server by the compression engine.
