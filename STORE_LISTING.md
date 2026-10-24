# Store listing & submission guide

## Suggested listing copy

**Short description (≤80 characters):**
> Compress images, videos, audio & PDFs locally on your device.

**Full description starting point:**
> DD Compressor is a privacy-first utility for compressing supported images, videos, audio files and PDFs directly on your Android device. The core compression process is designed to run locally instead of sending selected files to a D.D. Tech Labs compression server.
>
> • Select and process multiple files in a queue
> • Choose output formats where supported
> • Set an individual target size for each file
> • Review results before saving or sharing
> • Save output to Downloads / DD Compressor
> • Local-first processing of supported files
> • No account required for core compression
>
> **Advertising:** The Android app integrates Google Mobile Ads SDK (AdMob) and Google User Messaging Platform (UMP). Banner Ads may appear at the top and bottom of the app. Native Ads may appear in the app and between completed file results; interstitial and app-open ads may also appear at eligible times. Ad availability varies by consent, connection, region and inventory.
>
> **Optional 30-minute banner-only reward:** Choose to watch and successfully earn the rewarded ad to suppress Interstitial, Native and App Open ads for 30 minutes. Banner Ads may continue to appear during this period. This is not a fully ad-free mode, and watching an ad is never required to compress files.

The Android release is a native Android application; do not describe it as a webview or Capacitor wrapper.

## Privacy policy URL

Use the hosted HTML Privacy Policy, not a Markdown source file:

https://dd-tech-labs-hub.vercel.app/products/dd-compressor/privacy.html

The Android app links to the hosted Privacy Policy and contains in-app navigation for relevant privacy and advertising consent options.

## Advertising and data disclosures

- Google Mobile Ads SDK (AdMob) is integrated for supported ad formats.
- Google User Messaging Platform (UMP) is used for consent information and privacy options where applicable.
- Advertising/device identifiers, IP address or approximate location inferred from IP, diagnostics, ad delivery/measurement and ad interaction information may be processed by Google and its partners, subject to service details, device settings, region and consent.
- Do not describe the app as ad-free. The optional reward suppresses Interstitial, Native and App Open ads for 30 minutes while Banner Ads may remain visible.
- Ensure store-specific data safety and advertising declarations match the exact APK being submitted.

## Permissions and file handling

The native Android app uses the Android file picker for selecting files and Android's Downloads/MediaStore APIs to save output. Sharing uses Android's share sheet and FileProvider. Avoid adding broad storage/media permissions unless a future feature truly requires them.

## APK size and build

The GitHub Actions native release workflow builds from the root `android-native/` project, signs the release APK using repository secrets, verifies the signature and enforces a final APK size below 25 MB. Test the exact signed artifact before submitting it to a store.

## Pre-submission checklist

- Verify that the uploaded APK is the latest signed release from the intended branch.
- Test app launch, file selection, compression, save/share, consent options and the rewarded flow.
- Verify top/bottom banners, native placement and full-screen ad timing on a real device; ad availability itself is not guaranteed.
- Confirm that the hosted Privacy Policy, Disclaimer and Terms accurately describe the submitted app version.
- Confirm the store listing and any privacy/data-safety declarations match the enabled AdMob/UMP SDKs.
