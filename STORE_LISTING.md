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
> • Advertising-supported Android build; ads and consent options may be shown

The current Android app is a native local utility. Compression is performed on-device; advertising and consent SDKs may use network access.

## Privacy policy URL

Use the hosted HTML Privacy Policy, not a Markdown source file:

https://dd-tech-labs-hub.vercel.app/products/dd-compressor/privacy.html

The app also includes local HTML copies under `legal/` so users can open Privacy Policy, Terms, Disclaimer and third-party license information directly from the app.

## Monetized Android build

The Android build integrates Google AdMob and Google User Messaging Platform (UMP). Update the store's data-safety/privacy declarations to match the exact release being submitted, and verify the hosted privacy policy is published before distribution.

## Permissions

The current Android build uses Internet and network-state permissions for advertising/consent services. File selection uses Android's document picker; saving output uses MediaStore Downloads, and sharing is handled through Android's share sheet. Do not add broad storage/media permissions unless a future feature truly requires them.

## APK size

The workflow enforces a signed APK target below 25 MB. The automatic CI workflow builds an unsigned release APK for compilation and size validation only; the manual release workflow signs the APK and creates a GitHub Release when signing secrets are configured.
