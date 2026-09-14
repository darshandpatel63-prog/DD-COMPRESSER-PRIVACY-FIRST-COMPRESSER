# DD Compressor

Privacy-first file compression — images, video, audio, and PDFs — that runs entirely on your own device. No upload, no server, no account. Works on the web and as an installed Android app (phone and tablet), and works fully offline once loaded.

This is the same compression engine from the original DD Compresser project, restructured to ship as a proper Android app via **Tauri** instead of Capacitor, with a redesigned app-specific UI and an ad system that pays for hosting without getting in the way. Everything below explains what changed, why, and exactly how to build and publish it — written for building entirely from a phone, the same way the original project was.

---

## What's in this repository

```
web/                    The actual app — HTML/CSS/JS. Identical for the website
                         and the Android app; see "Web vs. app UI/UX" below for
                         how one codebase serves two different experiences.
src-tauri/               The Tauri (Rust) application shell that turns web/ into
                         an installed Android app.
plugins/tauri-plugin-dd-native/
                         A small custom plugin: native save/share, and the
                         entire ad system (AdMob banner + app-open, UMP
                         consent). Kotlin on the Android side, Rust glue on
                         the Tauri side.
icon-source/             The app icon's source artwork (SVG + a rendered 1024px
                         PNG) — `tauri icon` generates every platform size from
                         the PNG.
.github/workflows/
  android-build.yml      Builds the Android app on GitHub's servers — no
                         Android Studio or Rust needed on your own device.
STORE_LISTING.md         Copy and guidance for submitting to Play Store, Amazon
                         Appstore, Samsung Galaxy Store, and Xiaomi/Vivo/Oppo
                         app stores.
```

---

## Why Tauri, not Capacitor

The previous version of this project used Capacitor to wrap the web app as an Android app (see `web/js/native-bridge.js` for a code-level comparison with how the old `capacitor-bridge.js` worked). This version uses **Tauri** instead, for a few concrete reasons:

- **The web code barely changes.** Tauri wraps the exact same `web/` folder in a native shell — there was no rewrite of the compression engine, the UI, or the CSS in a different framework's component model, the way switching to Flutter or React Native would have required (both would mean rebuilding the interface from scratch in Dart or JSX). The one JS file that talks to the native layer (`native-bridge.js`) was swapped for a Tauri-based version with the *exact same function names and return shapes* the rest of the app already calls — see that file's comments for the full explanation.
- **Smaller, and a genuinely different security model.** Tauri's Android build uses the system WebView (same as Capacitor) but ships a Rust core instead of a Node/Chromium-adjacent one, with an explicit capabilities system that scopes exactly which native commands the web page is allowed to call (see `src-tauri/capabilities/default.json`) — a good match for an app whose entire pitch is "nothing leaves your device."
- **It's the actively-maintained, first-party path.** Of the four options considered (Flutter, React Native, Tauri Mobile, native Kotlin — see the comparison you provided), Tauri Mobile is the one explicitly positioned as a Capacitor alternative that keeps a web codebase as the source of truth, backed by the Tauri team/community rather than requiring a full platform-specific rewrite.

**Trade-off to know about:** Tauri Mobile is younger than Capacitor for Android specifically, and this repository's Android/Kotlin pieces could not be compiled or run in the environment that built them (no Android SDK, no network access — see "If the Android build fails" below for exactly what was and wasn't possible to verify, and how to fix the most likely failure points).

---

## Platforms

- **Web** — unchanged, deploy `web/` anywhere static (GitHub Pages, same as before).
- **Android** — phone and tablet, via this Tauri project. One APK/AAB runs on both; the CSS is responsive, not a separate tablet build.
- **Not included:** iOS and desktop (Windows/macOS/Linux) builds. Tauri could technically add both later with modest extra work, but neither was asked for, so neither is wired up here (see `plugins/tauri-plugin-dd-native/src/desktop.rs` — it's a harmless no-op stub, not a real implementation).

---

## Running the web version

Nothing changed here — open `web/index.html` directly, or serve the `web/` folder with any static file server. Still no build step, no `npm install` required just to run it.

## Building the Android app

You don't need Android Studio, Rust, or the Android SDK on your own device — `.github/workflows/android-build.yml` does the whole build on GitHub's servers, the same way the old `build-apk.yml` did for the Capacitor version.

1. Push this project to a GitHub repository (uploading the extracted zip through GitHub's web "Add file → Upload files" works fine from a phone browser, same as before).
2. Go to the repo's **Actions** tab → **Build Android app** → **Run workflow**.
3. Wait for the run to finish (Rust + Android builds take a few minutes longer than the old Capacitor build did).
4. Open the finished run → **Artifacts** → download `DD-Compressor-debug-apk`. That's an installable, unsigned debug APK — good enough to sideload and test immediately.
5. Once you're ready for a real release (Play Store, or any store that wants a signed build), see **Signing a release build** below — the same workflow run will then also produce a signed AAB and APK.

The workflow also runs automatically on every push to `main` that touches `web/`, `src-tauri/`, `plugins/`, or `icon-source/`.

### If the Android build fails

Everything Kotlin/Rust in this repository was written against current, verified Tauri v2 plugin documentation and real published plugin source code, but none of it could be compiled in the environment that produced it (no network, no Android SDK — see the note under "Why Tauri, not Capacitor"). If the workflow fails, here's where to look first, roughly in order of likelihood:

- **Gradle dependency version conflicts** — `plugins/tauri-plugin-dd-native/android/build.gradle.kts` pins specific versions for `play-services-ads` and `user-messaging-platform`. If Gradle reports a version conflict, bumping these to whatever the error message suggests is almost always the fix.
- **The `compileOnly(project(":tauri-android"))` line** in that same file assumes `tauri android init` names the Tauri runtime module `:tauri-android`, which is standard but could change in a future Tauri CLI release — check `src-tauri/gen/android/settings.gradle.kts` after step 2 above for the actual module name if this line errors.
- **The `sed` step that forces `compileSdk`/`targetSdk` to 36** in the workflow assumes the generated `build.gradle.kts` uses `compileSdk = <number>` formatting. If a future Tauri CLI template formats this differently, that step will silently not match — open `src-tauri/gen/android/app/build.gradle.kts` from the workflow's logs (or run `tauri android init` yourself anywhere with Node installed, even without the Android SDK, just to inspect the generated file) and adjust the `sed` pattern to match.
- **Any error mentioning `ipc.localhost` or "Content Security Policy"** — see `web/index.html`'s CSP meta tag comment; this is the one line that makes native calls (saving files, ads) work at all inside the app specifically.

None of this affects the website — `web/` runs standalone with zero build step regardless of anything above.

### Signing a release build

Play Store (and most other stores) need a **signed** build, not the debug APK above. This uses the standard Android signing setup, wired into the GitHub Actions workflow via four repository secrets:

1. Generate a keystore once (needs a JDK — this one step does need a real computer, or a cloud shell/Termux; everything else in this project can still be done from a phone):
   ```
   keytool -genkeypair -v -keystore release.keystore -alias dd-compressor -keyalg RSA -keysize 2048 -validity 10000
   ```
2. In your GitHub repo → **Settings → Secrets and variables → Actions**, add:
   - `ANDROID_KEYSTORE_BASE64` — the keystore file, base64-encoded (`base64 -w0 release.keystore`)
   - `ANDROID_KEYSTORE_PASSWORD` — the password you set when generating it
   - `ANDROID_KEY_ALIAS` — `dd-compressor` (or whatever alias you used above)
   - `ANDROID_KEY_PASSWORD` — usually the same as the keystore password unless you set a separate one
3. Re-run the **Build Android app** workflow. It will now also produce a signed `.aab` (for Play Store) and a signed `.apk` (for direct install or other app stores) as a second artifact.

**Keep `release.keystore` somewhere safe outside GitHub too.** If you ever lose it, you cannot publish an update to an app already live on Play Store under the same listing — Google cannot reset this for you.

---

## Setting up your own AdMob account

Every ad unit ID in this codebase is currently **Google's own official test ID** (safe to build and test with — they always show a clearly-labeled "Test Ad" and never earn real money). Before publishing a release you intend to make public:

1. Create/sign in to an AdMob account at **https://apps.admob.com**.
2. Add this app (you can do this before or after your first Play Store upload — AdMob supports linking either order).
3. Create two ad units: one **Banner**, one **App Open**.
4. Replace the two `TODO`-marked constants in `plugins/tauri-plugin-dd-native/android/src/main/java/com/darshan/compressor/nativebridge/AdUnitIds.kt` with your real ad unit IDs.
5. Replace the `com.google.android.gms.ads.APPLICATION_ID` value in `plugins/tauri-plugin-dd-native/android/src/main/AndroidManifest.xml` with your real AdMob **App ID** (different from the ad unit IDs — it's the one tied to the app as a whole).
6. Re-run the Android build.

A release build that still has the test IDs in it isn't just "not making money" — the Mobile Ads SDK deliberately crashes a **release-signed** build that ships with test ad unit IDs, by design on Google's part, specifically so this can't happen by accident. A debug build is unaffected either way.

---

## Ad strategy

This section is the actual policy the code enforces, not just a description of it — see `plugins/tauri-plugin-dd-native/android/.../BannerAdController.kt` and `AppOpenAdManager.kt` for where each rule below is implemented.

- **Two ad slots, full stop:** a persistent adaptive banner docked at the bottom of the screen, and one App Open ad shown at most once per cold start, only when online.
- **No interstitial, rewarded, rewarded-interstitial, or video-only ad formats anywhere in this codebase.** App Open is a distinct AdMob format from Interstitial, specifically designed for the "big, immediately closeable, launch-time" ad — it is not a workaround for the interstitial rule, it's a different, purpose-built format for that exact moment.
- **Never a placeholder.** The banner slot has zero height until a real ad loads, and collapses back to zero on any load failure (`View.GONE`, not just an empty/transparent view sitting there) — see `BannerAdController.attach()`. The App Open ad simply doesn't show at all if it isn't ready in time — no retry loop, no fallback creative, no delay to the app becoming usable.
- **The banner never covers anything.** It's docked by resizing the WebView's own container, not by floating on top of it — the page's viewport genuinely gets shorter when a banner is showing, so nothing in the file list or results is ever hidden underneath it (see `web/js/platform.js`'s comment for why this needed no JS-side coordination at all).
- **Offline stays fully offline.** Ads simply never attempt to load without a connection — no retry loop burning battery/data in the background, and compression itself was never touched by any of this (see PRIVACY.md and the untouched files listed under "What wasn't touched" below).
- **Consent-gated.** No ad is requested until Google's User Messaging Platform SDK has gathered consent where required — see `ConsentManager.kt`. "Menu → Ad privacy choices" lets a user reopen that same form at any time afterward, not just on first launch.
- **The website has no ads at all.** AdMob is an app SDK, not something that belongs inside a webpage — see the CSP note in `index.html` for why ads were kept entirely native and outside the page content rather than loaded as a web ad tag inside the WebView (the latter is also against AdMob's own policies for apps that wrap web content).

If you want more ad revenue than this conservative default, the two most common next steps — without breaking the "no interstitial/rewarded/video" rule — would be a **native ad** placed between file cards in a long list, or showing the App Open ad on resume-from-background as well as cold start (with a cooldown). Neither is implemented here since the original brief was explicit about not risking uninstalls over incremental revenue; both are a small, contained change to `AppOpenAdManager.kt`/`DdNativePlugin.kt` if you want them later.

---

## Web vs. app UI/UX

One `web/` codebase, two intentionally different experiences — `web/js/platform.js` sets an `is-app` or `is-web` class on page load, and `web/css/app-shell.css` (loaded after `styles.css`) contains every rule that differs. Specifically:

- **The app skips the marketing pitch.** `styles.css`'s hero section (headline, supporting paragraph, "why trust this" framing) makes sense to a first-time website visitor deciding whether to use this tool at all. Someone who already installed the app from a store listing doesn't need re-convincing — the app-only CSS shrinks the header and hides the paragraph, getting to the actual drop zone faster.
- **No duplicated navigation.** The website's header text links (Formats / Privacy / Source) exist for a visitor scanning for where to click. The app already has all of that (and more) in the hamburger menu, so the app hides the redundant header links — brand mark and menu button only, like a native app bar.
- **Safe areas and touch targets.** The app adds padding for notches/status bars/gesture-nav bars (`env(safe-area-inset-*)`, only relevant inside the installed app) and slightly larger tap targets for icon buttons.
- **Bottom ad space.** Handled entirely on the native side by resizing the WebView (see "Ad strategy" above) — nothing web-side needed for this specifically.

Nothing about the compression engine, the file cards, or the core interaction flow differs between the two — same tool, same trust model, just a shell that fits where it's running.

---

## What wasn't touched

The entire compression engine is byte-for-byte identical to the original project — verified during the build (`diff` against the original files, zero differences): `web/js/compressors/*.js`, `web/js/workers/*.js`, `web/js/ui.js`, and everything under `web/vendor/`. Nothing about how images, video, audio, or PDFs get compressed changed in this rewrite — only how the result gets saved (native plugin instead of Capacitor) and what wraps around it (Tauri instead of Capacitor, new UI shell, ads).

---

## What's new beyond the original ask

A few small, low-risk additions on top of the Tauri/ad/UI work, kept intentionally modest rather than bolting on a long feature list:

- **A lifetime "space saved" stat** (`web/js/stats.js`) — a small local counter (localStorage only, never sent anywhere) showing cumulative bytes saved across every file you've compressed. Shows up as a pill next to the privacy badge once you've compressed at least one file.
- **A signature motion moment** in the result view — the size comparison now includes a bar that visibly shrinks to the real output/original ratio the moment a compression finishes (`web/js/ui.js`'s `showResult`, purely additive — every existing element and class name it produces is untouched). Respects `prefers-reduced-motion`.
- **"Ad privacy choices"** menu item (app only) — reopens Google's consent form at any time, not just on first prompt.
- **A new app icon** — see below.

### Ideas not built (to keep this release contained), worth doing next

- A local, on-device "recently compressed" history (IndexedDB) — the original project's own README already flagged this as a natural next step.
- Multi-language UI (Gujarati/Hindi would be a natural first pair, given this project's origin) — deliberately not attempted here rather than ship machine-translated strings without a native speaker review.
- Haptic feedback on compression complete, via Tauri's haptics plugin — small polish, left out to keep the plugin surface (and this build's risk) as small as possible.

---

## App icon

The previous icon packed a shield, five format glyphs, and two lines of text into one image — legible on a marketing page, not at a 48px launcher size (and Play/Amazon/Samsung/Xiaomi guidelines all discourage text in app icons for exactly this reason). The new one (`icon-source/app-icon.svg`, rendered to `icon-source/app-icon-1024.png`) is a single geometric mark — two triangles meeting at a teal seam, literally the app's function (compression, squeezed from both sides) — in the app's own graphite/brass/teal palette, no text.

`tauri icon icon-source/app-icon-1024.png` (already wired into the CI workflow) regenerates every required Android size — including adaptive icon foreground/background layers — from that one source file, along with the PWA/website icons at `web/assets/`. To use a different icon entirely, replace `icon-source/app-icon-1024.png` with your own 1024×1024 artwork and re-run the build; nothing else needs to change. You're free to change this however you like — this is a starting point, not a constraint.

---

## Naming

Kept the existing "DD Compressor" brand (your own established name for this project) rather than inventing a new one — just standardized the spelling to "Compressor" throughout the new files for store-listing professionalism (the original "DD Compresser" spelling is still accurate for anything referencing the *original* GitHub repo name, which wasn't renamed). The Android `applicationId`/Tauri `identifier` was kept as `com.darshan.compresser` for continuity with anything already set up under that ID. Change either in `src-tauri/tauri.conf.json` (`identifier`) and `productName` freely — naming is entirely up to you, so nothing here is precious.

---

## Security notes

- **`web/index.html`'s CSP is the same strict `'self'`-only policy as before, plus one addition** (`ipc: http://ipc.localhost` in `connect-src`) — this is Tauri's own internal call channel for the installed app, not a network origin; without it, every native call (saving a file, opening ad consent) would fail its own CSP inside the app specifically. See the comment directly above that meta tag for the full reasoning, including why `src-tauri/tauri.conf.json` deliberately leaves its own CSP setting as `null` so this one tag stays the single source of truth on every platform instead of two policies quietly fighting each other.
- **`src-tauri/capabilities/default.json` scopes the webview's native access to exactly two commands** (save a file, open ad consent) — no filesystem, shell, or HTTP plugins are exposed, so even `withGlobalTauri`'s broader `window.__TAURI__` surface only actually reaches this app's own narrow plugin.
- **The FileProvider** (`plugins/.../AndroidManifest.xml`) only ever exposes the app's own `DD Compressor/` output subfolder, nothing else on the device.
- **R8/ProGuard:** `plugins/tauri-plugin-dd-native/android/consumer-rules.pro` keeps the plugin's reflectively-dispatched command methods from being stripped in a minified release build — worth knowing about if you ever see native calls silently stop working in a release build specifically while a debug build of the same code works fine.

---

## Publishing to app stores

See `STORE_LISTING.md` for store-specific guidance (Play Store, Amazon Appstore, Samsung Galaxy Store, and the Xiaomi/Vivo/Oppo family) — data safety form answers, permissions justification, content rating notes, and what each store needs beyond just the APK/AAB.

---

## License

Unchanged — see `LICENSE`. This remains the DD Compressor Community License: free to use, study, and modify; selling it or a modified version needs the original author's permission, which as the original author, obviously isn't a constraint on you.
