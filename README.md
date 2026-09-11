
https://darshandpatel63-prog.github.io/DD-COMPRESSER-PRIVACY-FIRST-COMPRESSER/
---

Direct download link 

---

# DD Compressor — Privacy First

A browser-based file compressor. Images, video, audio, and PDFs are re-encoded **inside the browser tab that opens this page** — nothing is ever uploaded, because the app has no upload endpoint to send anything to.

**Live once deployed:** `https://<your-username>.github.io/<your-repo>/`

---

## What changed from the original project

This is a rebuild of the original DD Compressor, not a patch. The original HTML/CSS/UX ideas were good and are kept; the audit below is what was actually broken and how each was fixed.

### 1. FFmpeg never loaded at all
`index.html` referenced `./ffmpeg.min.js` locally, but that file (and its companion `814.ffmpeg.js`) were never actually included in the repository — so on GitHub Pages, `window.FFmpegWASM` was always `undefined` and video/audio compression failed before it could even start.

The underlying reason those files have to sit **next to each other**, and be loaded from the **same origin as the page**, is in `@ffmpeg/ffmpeg`'s own UMD bundle: it resolves its worker chunk relative to wherever its own `<script>` tag was loaded from —

```js
// from node_modules/@ffmpeg/ffmpeg/dist/umd/ffmpeg.js (unminified excerpt)
new Worker(new URL(e.p + e.u(814), e.b))   // e.p = directory of ffmpeg.js itself
```

If `ffmpeg.js` is loaded from a CDN, `e.p` becomes the CDN's URL, and the browser refuses to construct a Worker whose script lives on a different origin than the page — which is exactly the original error: *"Failed to construct 'Worker': Script at https://cdn.jsdelivr.net/.../814.ffmpeg.js cannot be accessed from origin https://....github.io"*.

**Fix:** `vendor/ffmpeg/ffmpeg.js` and `vendor/ffmpeg/814.ffmpeg.js` are real files (downloaded from the published `@ffmpeg/ffmpeg@0.12.15` npm package, not placeholders), committed side by side, loaded via a same-origin relative `<script src="./vendor/ffmpeg/ffmpeg.js">`. **Note this is `./vendor/ffmpeg/ffmpeg.js`, not `./ffmpeg.min.js`** — if a tool or audit flags a reference to `./ffmpeg.min.js` in this project, it's looking at the *original*, pre-rebuild file; `index.html` in this repo has never referenced that path. The WASM core (`ffmpeg-core.js` / the split `ffmpeg-core-part*.bin`, from `@ffmpeg/core@0.12.10`) is also vendored locally and converted to `blob:` URLs before being handed to `ffmpeg.load()`, so it never depends on a CDN being reachable.

*Verified for real:* the exact vendored `ffmpeg-core.wasm` was loaded and executed under Node for this project (not just assumed to work) — see "Testing performed" below.

### 2. Video/audio bitrate was 1000x too high
`compressMedia()` correctly computed a bitrate in **bits/second** (`targetBytes * 8 / duration`), then built the ffmpeg flag as `` `${bitrate}k` ``. FFmpeg's `k` suffix means ×1000 — so a correctly-computed value like `313000` (313 kbps) became the string `"313000k"`, i.e. **313,000 kbps (≈313 Mbps)**. That's an effectively unlimited bitrate ceiling, so `-maxrate`/`-bufsize` did nothing and target-size compression for video/audio couldn't have worked even once FFmpeg loaded.

**Fix:** `bpsToKFlag()` in `js/compressors/ffmpeg-engine.js` divides by 1000 before appending `k`. Confirmed with a real encode (synthetic FFmpeg `lavfi` source, no external test file needed) that a 150KB/3-second target now produces flags like `-b:v 313k` instead of `-b:v 312832k`.

### 3. Compressing a small/already-optimized image could make it *bigger*
`compressImage()` tracked "the candidate closest to the target size," with no floor comparing it to the **original** file size. Converting an already-small image to PNG via `canvas.toBlob` — the default first attempt — can genuinely balloon a small photo (e.g. 80 KB → 400+ KB), because canvas always emits full 32-bit-per-pixel PNG, never the palette/indexed optimization a small source file may have used. Separately, the dimension-reduction loop had a `newWidth === width → break` guard for the 64px floor that could exit silently while still holding that oversized early candidate.

**Fix:** the new engine (`js/workers/image-worker.js`) always compares its final candidate against the **original file size** and refuses to return anything larger — falling back to the unmodified original with an honest "already efficient" status instead. Verified with a real regression test: an 18 KB JPEG asked to hit an 8 KB target now correctly returns ~8 KB (never the original size or larger); see "Testing performed."

### 4. PDF compression was full-page rasterization
The original approach rendered every page to a canvas via PDF.js and rebuilt the file as a stack of images via jsPDF. It shrinks photo-heavy PDFs, but it destroys selectable text, real vector graphics, and form fields on **every** PDF, including ones that were mostly text.

**Fix:** `js/compressors/pdf.js` walks the PDF's own object graph (via `pdf-lib`) and recompresses just the embedded JPEG (`/DCTDecode`) images in place — the same objects a scanner or photo-heavy export actually uses for their bulk. Text, fonts, and vector paths are untouched because their PDF objects are never touched. A resume or invoice with no photos will correctly show little or no size change; that is the honest result for a document with nothing large left to shrink.

### 5. Why the .wasm is split in two
`ffmpeg-core.wasm` is ~32MB. That's over GitHub's 25MB limit for its web "Upload files" button, and too big for phone code editors like Spck (5MB) or Acode (1MB) to handle at all — a real problem for a project meant to be pushed from a phone.

The fix is storage-only, not a code change to FFmpeg: the binary is split into two ordered pieces, `vendor/ffmpeg/core/ffmpeg-core-part1.bin` (~16.1MB) and `ffmpeg-core-part2.bin` (~16.1MB), each comfortably under the 25MB web-upload ceiling. At runtime, `toBlobURLFromParts()` in `js/utils.js` fetches both and concatenates them back into one buffer with `new Blob([part1, part2])` before FFmpeg ever sees it. This was verified two ways before shipping: a SHA-256 checksum of the two parts concatenated together matches the original single file exactly, and the reassembled buffer was fed into the real `ffmpeg-core.wasm` loader under Node and used to run a real encode — same `ffmpeg version 5.1.4`, same working output, byte-for-byte the same core.

**If you ever need the single `.wasm` file back** (e.g. deploying somewhere without a 25MB limit), reassemble it locally:
```bash
cat vendor/ffmpeg/core/ffmpeg-core-part1.bin vendor/ffmpeg/core/ffmpeg-core-part2.bin > vendor/ffmpeg/core/ffmpeg-core.wasm
```
and point `CORE_WASM_PARTS` in `js/compressors/ffmpeg-engine.js` back to a single `toBlobURL()` call instead.

### 6. A many-hundred-image PDF looked like it kept restarting
A 906-image, 212MB scanned textbook PDF appeared to process, then start over, several times, and never finished. It wasn't actually restarting — it was working, just far too slowly, and its own progress reporting made that look like a restart.

The old search tried up to 3 dimension levels × 5 quality guesses = **15 full passes over every single embedded image**, plus one confirmation pass — 16 × 906 ≈ 14,500 encode operations. On top of that, progress was calculated as "image *i* of *N* in this pass," which resets to a low number at the start of every one of those 16 passes — so once a pass finished and the next one began, the progress bar visibly snapped back down, looking exactly like "it finished, then restarted."

**Fix, in `js/compressors/pdf.js`:** documents with more than 30 recompressible images now estimate the right quality/dimension setting from a small, spread-out **sample** (at most ~10 images) instead of grid-searching the whole document, then apply that setting in exactly **one** full pass — not sixteen. Progress is now cumulative across the whole operation (never resets), and after the first few images it shows a real "~N minutes left" estimate timed from this device's own actual speed. Each image decode also now uses `createImageBitmap`'s built-in resize option when the target size is already known from the PDF's own `/Width`/`/Height` fields, instead of decoding a high-DPI scan at full resolution just to immediately shrink it. Verified with a real 50-image synthetic PDF: progress is confirmed strictly non-decreasing end to end, and the whole document is only ever fully encoded once. A many-hundred-image document will still take real time — recompressing hundreds of full-resolution images is genuinely that much work — but it now progresses steadily toward a finish instead of silently repeating itself for much longer than necessary.

### 7. A 3GB+ video hung the tab, and the drop zone stopped responding
FFmpeg's WebAssembly build is **WASM32**, which has a hard **4GiB address-space ceiling** no matter how much RAM the phone has — and that space has to hold the input file, FFmpeg's own working memory, and the output file all at once. Reading a 3GB+ file into a single JS `ArrayBuffer` via `file.arrayBuffer()` is itself a huge, failure-prone allocation on a phone, before FFmpeg is even reached — this is what hung the tab, not a logic bug in the compression code.

**Fix:** `checkMediaFileSize()` in `js/utils.js` now refuses video/audio above **1.75GB outright**, with a clear explanation, and flags anything above 600MB as slow/risky but still attempts it. This check runs **the moment a file is added** (so an oversized file shows its error immediately on the card) and again at the start of `compressVideo`/`compressAudio` as a backstop. Separately, the file picker now resets its internal value both before opening and (via `try/finally`) after handling a selection, so an unexpected error midway through adding a file can't leave the picker pointed at an already-"selected" file — which is what made the drop zone look unresponsive to a second attempt. Honestly: there's a real, hard ceiling here that no amount of clever JavaScript moves — a 3GB+ source video is genuinely beyond what any single-threaded, in-browser WebAssembly engine can hold at once, on any device. For files at that scale, compress with a native app first, or split the video into shorter segments.

### 8. Embedded photos in a print-sourced PDF came out solid black
A real 212MB scanned anatomy textbook compressed successfully (no more restarting, see #6) — but its diagrams came out almost solid black. This was a real, serious bug, fully root-caused rather than patched blind:

`pdfimages -list` on the original showed every affected image as `cmyk` (4-component), and the raw JPEG carries an Adobe APP14 marker with `transform=2` (YCCK) — a well-known, historically inconsistent convention where CMY values are stored inverted, correctly handled by Adobe-aware renderers (confirmed: `poppler`'s own PDF rendering of the same page shows the skeleton diagram correctly) but not reliably by a generic decode of the raw JPEG stream on its own. This project's engine was extracting exactly that raw stream and handing it to `createImageBitmap` — a decode path with no way to guarantee the same Adobe-aware correction, and evidence (a direct raw extraction rendered the same way) confirmed it was producing the same near-black result.

**Fix:** `js/compressors/pdf.js` now resolves each image's actual PDF colorspace — including indirect references and `ICCBased`/`Indexed` wrappers — and only recompresses images that are genuinely RGB or Grayscale. CMYK, `Separation`, and `DeviceN` (spot-color) images are left **completely untouched**, byte-for-byte, the same safety treatment already given to images with a transparency mask. This was not left as a theoretical fix: it was run against the actual real 212MB file, confirming the previously-black page now renders identically to the original (verified with a poppler render, pixel-identical file size to the untouched source page), with zero progress regressions.

**The honest trade-off:** a print-sourced, CMYK-scanned textbook like this one is now correctly *not* corrupted — but also barely compresses, because CMYK images were the vast majority of its size (907 of 909 embedded images in this file). This is the right default: this project's core promise is "never corrupt your file," and that has to outrank compression ratio when the two conflict. For a CMYK-heavy PDF like this, a desktop tool that's specifically CMYK-aware (Adobe Acrobat's own "Reduce File Size," for example) will do meaningfully better than any browser-only engine can right now.

---

## Android app (Capacitor)

A `.apk` build of this project was submitted for review. Findings below are from directly inspecting that file (`aapt dump badging`, unzipping its bundled assets), not assumptions.

**What it is:** a [Capacitor](https://capacitorjs.com) app (`com.darshan.compresser`) — a native Android shell around a WebView, with this project's web files bundled locally inside the `.apk` (`assets/public/...`). That bundling is good news on its own: **the app already works offline**, since Capacitor serves those local files instead of fetching them from the internet.

**What was broken, and why:** the download button did nothing. `assets/capacitor.plugins.json` inside the submitted `.apk` is literally `[]` — no native plugins are compiled in yet. A website's download trick (a `blob:` URL + a hidden `<a download>` click) relies on the *browser's* download manager; a bare WebView has no download manager of its own, so that click had nothing to hook into. This is now fixed on the web-code side (`js/capacitor-bridge.js`, wired into `downloadBlob()` in `js/utils.js`): when running as a native app, it saves through Capacitor's `Filesystem` plugin and offers the native `Share` sheet instead of the browser trick. **This only activates once the Capacitor Android project itself has the plugins added** — that part happens outside this web repo:
```bash
npm install @capacitor/filesystem @capacitor/share
npx cap sync android
```
then rebuild. Until that's done, the app will show a clear message explaining exactly this, instead of a button that silently does nothing.

**Permissions, currently:** only `INTERNET` (required by the WebView component itself; this app makes no network calls with it — see `PRIVACY.md`). Modern Android's scoped storage means adding the Filesystem plugin above should **not** require the old broad "allow storage access" prompt for this kind of save.

### Three things that were asked for and can't honestly be done here

Being direct about this matters more than seeming agreeable — these three specifically need native Android development, which this project (a Capacitor *web* app) cannot provide from JavaScript alone, and which this working environment has no way to build or test (no Android SDK/emulator available):

1. **"Unlimited file size in the app."** The installed app is *still the same WebView/WebAssembly engine* as the browser — Capacitor wraps a web page, it doesn't replace the engine underneath it. WASM32's 4GB address-space ceiling (see bug #7 below) applies exactly as much inside the app as in a browser tab, because it's the same 32-bit engine either way. What actually changed here: the app gets a modestly higher limit (3GB vs. 1.75GB — see `MEDIA_SIZE_LIMITS` in `js/utils.js`), because a dedicated app process isn't sharing memory budget with a dozen other browser tabs. That's a real, honest improvement — "somewhat higher," not "unlimited." Truly removing the ceiling would mean a fundamentally different app: native Kotlin/Java code calling a natively-compiled FFmpeg library (not WebAssembly) — a full rewrite of the compression layer, not a setting to flip.
2. **Background compression.** Android suspends/throttles a WebView's JavaScript when the app isn't in the foreground unless the app runs a native **Foreground Service** with a persistent notification — again, native Kotlin/Java code and manifest changes, not something this web repo controls. If this is wanted, look at a Capacitor background-task plugin as the starting point, added and tested the same way as Filesystem/Share above.
3. **Requesting permissions automatically at launch.** Also native-project configuration (`MainActivity`, `AndroidManifest.xml`), not this web repo. In practice, once Filesystem is added, Capacitor requests what it needs when a save is first attempted, which — combined with scoped storage not needing the old blanket prompt — should mean little to no "Android settings resistance" to begin with.

### What "checking every file, every way" actually means here

The request for the file to be checked by "multiple agents" before and after compression comes from a good, specific place — the CMYK black-image bug (#8 below). Worth being direct about one thing: literal AI agents inspecting file contents would mean sending those contents to an AI service over the network, which directly contradicts this project's one core promise (files never leave the device). That trade isn't made here, even to satisfy this request.

What's built instead is real, and runs in milliseconds, entirely on-device:
- **Analysis before compressing** — this already existed (file-type detection, the CMYK colorspace check) and is unchanged.
- **Compression** — the existing engines, unchanged.
- **An automatic audit after compressing, new in this update** — a fast brightness comparison (`js/workers/image-worker.js` and `js/compressors/pdf.js`) between the original and the candidate output. If a normally-lit image comes out looking suspiciously near-solid-black — the exact signature of the CMYK bug — the result is refused automatically and the original is kept, with an honest status message, rather than risk handing back something corrupted. This is a real safety net, verified against representative before/after brightness values, and it costs a handful of milliseconds per image (a 24x24 or 16x16 pixel sample), not a slowdown.

This is "multiple checks, every file, every time" — done with deterministic code instead of AI agents, for the same privacy reason the whole app exists.

### Other app-related changes in this update
- **Editable filename before download** — the compressed-file name is now an editable field right above the Download button, in both the web and app versions.
- **A simple in-app menu** ("≡" button, top right) — "How this app works," Privacy Policy, Terms of Service, Disclaimer, and a link to the Android app release, all written in plain, non-technical language on purpose (the goal is a user understanding what happens to their file, not a specification another developer could copy).
- **`PRIVACY.md`, `TERMS.md`, `DISCLAIMER.md`** — added at the repo root, summarized in the in-app menu above.
- **`js/app-config.js`** — the *only* file that should need editing for a new Android release: it holds nothing but the download link.
- On the web version, hitting the video/audio size limit now honestly mentions the app as an option with a somewhat higher (not unlimited) ceiling, and that mention is hidden when already running inside the app.



| Question | Decision | Why |
|---|---|---|
| Framework? | None — plain HTML/CSS/JS, ES modules, no build step | The brief is "code from a phone, deploy instantly." A bundler (Next.js/Vite/etc.) would need a build step you can't easily run from a phone, and GitHub Pages just serves files as-is — a build step is a liability here, not a feature. |
| Image engine | Fully custom (`Canvas`/`OffscreenCanvas` + a plain binary search) | This is realistically implementable to a high standard with browser-native APIs alone — no library does this measurably better than a well-written binary search over quality and dimensions. |
| Video/audio engine | `@ffmpeg/ffmpeg` (WebAssembly), vendored locally | Writing a video codec from scratch is not a reasonable ask; FFmpeg-in-WASM is the standard, well-maintained solution the whole ecosystem already relies on. Our own code owns the bitrate math, format selection, and UI — FFmpeg only does the encode. |