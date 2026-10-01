# Store listing & submission guide

## Suggested listing copy

**Short description (≤80 chars):**
> Compress photos, videos, audio & PDFs — locally on your device. No uploads.

**Full description starting point:**
> DD Compressor is a privacy-first file compression utility for images, video, audio, and PDFs. Core compression runs on your own device instead of a cloud compression server.
>
> • Multi-file queue
> • Target size controls
> • Local image, video, audio and PDF engines
> • Review results before saving
> • Offline-first core compression
> • Native Android save/share support
> • First store release: free and ad-free

The current UI is an app-style local utility, not a remote file-upload interface.

## Privacy policy URL

Use the hosted HTML Privacy Policy, not a Markdown source file:

https://dd-tech-labs-zsrw.vercel.app/products/dd-compressor/privacy.html

The app also includes local HTML copies under `legal/` so users can open Privacy Policy, Terms, Disclaimer and third-party license information directly from the app without opening a `.md` source document.

## First ad-free Android build

The first store build keeps AdMob disabled and does not install the AdMob plugin.

## Later monetized build

A later build may enable Google AdMob/UMP through the manual workflow and GitHub Actions Secrets. Update the store's data-safety/privacy declarations to match the exact release being submitted.

## Permissions

The current Android build is designed around the narrowest runtime permissions possible. Saving compressed output uses the app's own external-files area through Capacitor Filesystem, and sharing is handled by the Android share sheet. Do not add broad storage/media permissions unless a future feature truly requires them.

## APK size

The workflow enforces the current project target of a signed APK below 25 MB. FFmpeg WebAssembly is the dominant size constraint; do not inflate the app with unnecessary packages, remote UI libraries or duplicated assets.
