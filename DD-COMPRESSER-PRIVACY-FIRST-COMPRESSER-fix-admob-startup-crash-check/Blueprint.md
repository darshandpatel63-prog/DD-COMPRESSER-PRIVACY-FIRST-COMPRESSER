# DD Compressor — Project Blueprint

## Purpose
DD Compressor is a privacy-first, client-side file compressor. User-selected files are processed locally in the browser/app. The repository contains the web app/PWA, local compression engines, legal pages, and a manually triggered Android APK workflow.

## Runtime architecture
- `index.html` — lightweight app shell and workspace UI.
- `css/styles.css` — self-contained responsive design system; no UI framework.
- `js/main.js` — file selection, compression orchestration, results and downloads.
- `js/ui.js` — file cards, progress, results and notifications.
- `js/menu.js` — app menu and lightweight theme switcher.
- `js/utils.js` — shared utilities, downloads and media limits.
- `js/app-config.js` — website link to the latest published Android APK.
- `js/capacitor-bridge.js` — legacy web/Capacitor bridge retained only for source compatibility; it is not packaged into the native Android app.
- `js/compressors/image.js` — image compression.
- `js/compressors/video.js` — video compression through local FFmpeg WASM.
- `js/compressors/audio.js` — audio compression through local FFmpeg WASM.
- `js/compressors/pdf.js` — PDF compression through local pdf-lib.
- `js/compressors/generic.js` — generic/text-like compression through CompressionStream.
- `js/compressors/ffmpeg-engine.js` — local FFmpeg WASM loader and cleanup.
- `js/workers/image-worker.js` — image-processing worker.
- `vendor/ffmpeg/` — local FFmpeg runtime/core files.
- `vendor/pdf-lib/` — local pdf-lib runtime.
- `vendor/fonts/` — locally bundled Inter fonts.
- `assets/` — application icons and static assets.
- `legal/` — in-app HTML legal pages and shared legal-page CSS.
- `manifest.webmanifest`, `robots.txt`, `sitemap.xml` — PWA/SEO/deployment metadata.
- `.github/workflows/build-apk.yml` — manual Android APK build workflow.

## UI/UX direction
The public app UI uses a lightweight, card-based design inspired by the information hierarchy of modern design-intelligence sites such as UUPM: a strong hero, compact stat cards, pill controls, modular cards, dark/light mode, clear section hierarchy and responsive mobile behavior. It is an original DD Compressor design and does not depend on UUPM assets or code.

The Android/web experience is intentionally kept dependency-light:
- No UI framework.
- No external icon/font CDN.
- Fonts are bundled locally.
- Existing compression engines stay local.
- Legal pages use one shared local stylesheet.
- Theme state is stored locally in the browser/app.

## Legal documents
The app must not expose source `.md` documents as its legal UI.
- `legal/privacy.html` — in-app Privacy Policy.
- `legal/terms.html` — in-app Terms of Service.
- `legal/disclaimer.html` — in-app Disclaimer.
- `legal/licenses.html` — in-app third-party license summary.
- `PRIVACY.md`, `TERMS.md`, `DISCLAIMER.md`, and `THIRD_PARTY_LICENSES.md` remain source/documentation records; they are not linked as user-facing app pages.
- Official DD Tech Labs product page: `https://dd-tech-labs-hub.vercel.app/product/dd-compressor`.

## Important project principles
1. Do not upload user files to a server for core compression.
2. Keep compression engines local where the current architecture requires them.
3. Do not remove a dependency merely because it is not imported directly; verify runtime references first.
4. Do not remove legal/license files that are required for source redistribution.
5. Keep the Android workflow because it builds the APK on demand.
6. Keep `.nojekyll` for GitHub Pages compatibility.
7. Keep `.gitignore` for repository hygiene.
8. Prefer plain HTML/CSS/JS and local assets over new npm dependencies.

## Android architecture
- The Android app is a separate native Java/Android project under `android-native/`; it does not package the website, WebView, Capacitor runtime, JavaScript compressors, or FFmpeg WASM.
- Android uses Jetpack Media3 Transformer for native H.264/AAC media encoding, Android Bitmap encoders for images, PdfBox-Android for PDF image optimization, and Android gzip for generic files.
- The native app uses Android's document picker, ACTION_SEND/ACTION_SEND_MULTIPLE receive flow, ACTION_CREATE_DOCUMENT save flow, and FileProvider sharing.
- Compression is quality-first: keep the largest useful dimensions and highest feasible quality that meets the requested target; reduce quality/resolution only when necessary.
- Release signing credentials stay in GitHub Actions Secrets; never commit a keystore.

## Size budget
- Requested hard target: final APK strictly below 25 MB.
- The native workflow fails the final APK build if the signed APK reaches 25 MB or more.
- The native app deliberately avoids bundling FFmpeg WebAssembly, a WebView, or a web runtime; this keeps the Android package smaller and removes the web-wrapper dependency.
- If the release artifact exceeds the target, measure the actual APK first and optimize the media engine/delivery architecture rather than deleting required legal or application files.

## Store/review considerations
The app should present itself as a real local utility rather than a generic website wrapper:
- local compression engines;
- target-size controls and multi-file queue;
- on-device result comparison;
- native Android save/share bridge;
- offline-first core workflow;
- direct in-app legal pages;
- responsive app-style UI with dark/light theme.

These are real application functions and should remain functional in the Android build.

## Cleanup rule for future changes
Before deleting any file, search the complete repository for imports, script/style references, asset paths, workflow references, legal references, and deployment references. Delete only when no real use remains.
