# DD Compressor — Project Blueprint

## Purpose
DD Compressor is a privacy-first, client-side file compressor. User-selected files are processed locally in the browser/app; the repository contains the web application, local compression engines, PWA assets, legal documents, and a manually triggered Android APK workflow.

## Runtime architecture
- `index.html` — application entry point.
- `css/styles.css` — complete web UI styling.
- `js/main.js` — application orchestration and compression flow.
- `js/ui.js` — file cards, progress, results, and notifications.
- `js/menu.js` — menu and informational panels.
- `js/utils.js` — shared utilities, local asset loading, downloads, media limits.
- `js/app-config.js` — Android release download URL.
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
- `assets/` — application icons used by the page/PWA.
- `manifest.webmanifest`, `robots.txt`, `sitemap.xml` — PWA/SEO/deployment metadata.
- `.github/workflows/build-apk.yml` — manual Android APK build workflow; keep it because it has an operational purpose.

## Important project principles
1. Do not upload user files to a server.
2. Keep compression engines local where the current architecture requires them.
3. Do not remove a dependency merely because it is not imported directly; verify runtime references first.
4. Do not remove legal/license files that are referenced by the app or required for redistribution.
5. Keep the Android workflow because it builds the APK on demand.
6. Keep `.nojekyll` for GitHub Pages compatibility.
7. Keep `.gitignore` for repository hygiene.

## Documentation
- `PRIVACY.md` — privacy policy referenced by the in-app menu.
- `TERMS.md` — terms referenced by the in-app menu.
- `DISCLAIMER.md` — disclaimer referenced by the in-app menu.
- `THIRD_PARTY_LICENSES.md` — third-party license record referenced by privacy/footer material.
- `LICENSE` — project license.
- `STORE_LISTING.md` — app-store submission material; retained because it has a practical release purpose.
- This `Blueprint.md` is the technical project map and replaces the obsolete README as the primary repository blueprint.

## Deliberately removed as unused/obsolete
- `README.md` — described a Tauri/web/src-tauri structure that is not present in the repository.
- `package.json` — described Tauri commands/dependencies, while the actual APK workflow creates its own Capacitor project dynamically and does not use this package file.
- `vendor/ffmpeg/core/.keep` — redundant because the directory already contains the required FFmpeg core files.

## Android monetization architecture
- `js/admob.js` contains the native-only AdMob control layer.
- The web/PWA build remains functional without AdMob.
- The Android build injects AdMob application/ad-unit IDs from GitHub Actions Secrets; IDs are not committed to the repository.
- UMP consent is requested before ads where required.
- One native adaptive banner is shown at the bottom and the WebView reserves bottom space so the Download button is not covered.
- One interstitial is attempted only after 20 completed compression jobs; it is never shown while compression is running.
- The current Capacitor AdMob JS API manages one banner position at a time. Simultaneous top + bottom banners or true inline native ads inside each WebView result card require a custom native Android view/plugin. The current build deliberately does not overlay a second ad over the app content.

## Store release architecture
- Capacitor 8 Android targets SDK 36; Play publishing uses AAB, while Indus and other stores can accept signed APK/AAB/APKS formats.
- Release signing credentials must stay in GitHub Actions Secrets; never commit a keystore.

## Size budget
- Requested hard target: final APK strictly below 25 MB.
- The workflow reports the native web payload and fails the final APK build if the generated APK reaches 25 MB or more.
- FFmpeg WASM is the dominant size constraint. Current FFmpeg WebAssembly cores are tens of MB before APK compression, so the <25 MB requirement must be measured on the actual release artifact rather than promised in advance.
- If the release artifact exceeds 25 MB, do not delete required FFmpeg files. The next options are a smaller purpose-built native/media engine or on-demand feature delivery.

## Cleanup rule for future changes
Before deleting any file, search the complete repository for imports, script/style references, asset paths, workflow references, legal references, and deployment references. Delete only when no real use remains.
